/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.manual;

import junit.framework.TestCase;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.Percent;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.LocalDBLoggerSettings;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.secure.PwmRandom;

import java.io.File;
import java.io.Serializable;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalDBLoggerTest extends TestCase {

    private final NumberFormat numberFormat = NumberFormat.getNumberInstance();

    private LocalDBLogger localDBLogger;
    private LocalDB localDB;
    private Configuration config;

    private final AtomicInteger eventsAdded = new AtomicInteger(0);

    private EventRateMeter eventRateMeter = new EventRateMeter(new TimeDuration(60 * 1000));

    private Settings settings;
    private Date startTime;


    @Override
    protected void setUp() throws Exception {
        super.setUp();    //To change body of overridden methods use File | Settings | File Templates.
        TestHelper.setupLogging();
        final File localDBPath = new File(TestHelper.getParameter("localDBPath"));
        final File configFile = new File(TestHelper.getParameter("configurationFile"));
        final ConfigurationReader reader = new ConfigurationReader(configFile);
        config = reader.getConfiguration();

        localDB = LocalDBFactory.getInstance(
                localDBPath,
                false,
                null,
                config
        );

        //localDB.truncate(LocalDB.DB.EVENTLOG_EVENTS);
        //System.out.println(localDB.size(LocalDB.DB.EVENTLOG_EVENTS));
        //new TimeDuration(1,TimeUnit.HOURS).pause();

        { // open localDBLogger based on configuration settings;
            final int maxEvents = (int) reader.getConfiguration().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
            final long maxAgeMs = reader.getConfiguration().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE) * (long) 1000;
            final LocalDBLoggerSettings settings = new LocalDBLoggerSettings.Builder().setMaxEvents(maxEvents).setMaxAge(new TimeDuration(maxAgeMs)).setFlags(Collections.<LocalDBLoggerSettings.Flag>emptySet()).createLocalDBLoggerSettings();
            localDBLogger = new LocalDBLogger(null, localDB, settings);
        }

        settings = new Settings();
        settings.threads = 10;
        settings.testDuration = new TimeDuration(3, TimeUnit.HOURS);
        settings.valueLength = 5000;
        settings.batchSize = 100;
    }

    private void out(String output) {
        System.out.println(JavaHelper.toIsoDate(new Date())+ " " + output);
    }

    public void testBulkAddEvents() throws InterruptedException {
        out("starting bulk add...  ");
        out("settings=" + JsonUtil.serialize(settings));
        startTime = new Date();
        final Timer timer = new Timer();

        final int threadCount = settings.threads;
        final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                threadCount,
                threadCount,
                1,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<Runnable>(threadCount + 1)
        );

        timer.scheduleAtFixedRate(new DebugOutputTimerTask(),5 * 1000, 30 * 1000);

        for (int loopCount = 0; loopCount < threadCount; loopCount++) {
            threadPoolExecutor.execute(new PopulatorThread());
        }

        threadPoolExecutor.shutdown();
        threadPoolExecutor.awaitTermination(1, TimeUnit.DAYS);
        timer.cancel();
        out("bulk operations completed");
        out("settings=" + JsonUtil.serialize(settings));
        out(" results=" + JsonUtil.serialize(makeResults()));
        outputDebugInfo();
    }


    private class PopulatorThread extends Thread {
        public void run() {
            final RandomValueMaker randomValueMaker = new RandomValueMaker(settings.valueLength);
            while (TimeDuration.fromCurrent(startTime).isShorterThan(settings.testDuration)) {
                final Collection<PwmLogEvent> events = makeEvents(randomValueMaker);
                for (final PwmLogEvent logEvent : events) {
                    localDBLogger.writeEvent(logEvent);
                    eventRateMeter.markEvents(1);
                    eventsAdded.incrementAndGet();
                }
            }
        }
    }

    private Collection<PwmLogEvent> makeEvents(final RandomValueMaker randomValueMaker) {
        final int count = settings.batchSize;
        final Collection<PwmLogEvent> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final String description = randomValueMaker.next();
            PwmLogEvent event = PwmLogEvent.createPwmLogEvent(
                    Instant.now(),
                    LocalDBLogger.class.getName(),
                    description, "", "", null, null, PwmLogLevel.TRACE);
            events.add(event);
        }

        return events;
    }

    private void outputDebugInfo() {
        final StringBuilder sb = new StringBuilder();
        sb.append("added ").append(numberFormat.format(eventsAdded.get()));
        sb.append(", size: ").append(StringUtil.formatDiskSize(FileSystemUtility.getFileDirectorySize(localDB.getFileLocation())));
        sb.append(", eventsInDb: ").append(figureEventsInDbStat());
        sb.append(", free: ").append(StringUtil.formatDiskSize(
                FileSystemUtility.diskSpaceRemaining(localDB.getFileLocation())));
        sb.append(", eps: ").append(eventRateMeter.readEventRate().setScale(0, RoundingMode.UP));
        sb.append(", remain: ").append(settings.testDuration.subtract(TimeDuration.fromCurrent(startTime)).asCompactString());
        sb.append(", tail: ").append(TimeDuration.fromCurrent(localDBLogger.getTailDate()).asCompactString());
        out(sb.toString());
    }

    private String figureEventsInDbStat() {
        final long maxEvents = config.readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
        final long eventCount = localDBLogger.getStoredEventCount();
        final Percent percent = new Percent(eventCount,maxEvents);
        return numberFormat.format(localDBLogger.getStoredEventCount()) + "/" + numberFormat.format(maxEvents)
                +  " (" + percent.pretty(2) + ")";
    }

    private Results makeResults() {
        Results results = new Results();
        results.dbClass = config.readAppProperty(AppProperty.LOCALDB_IMPLEMENTATION);
        results.duration = TimeDuration.fromCurrent(startTime).asCompactString();
        results.recordsAdded = eventsAdded.get();
        results.dbSize = StringUtil.formatDiskSize(FileSystemUtility.getFileDirectorySize(localDB.getFileLocation()));
        results.eventsInDb = figureEventsInDbStat();
        return results;
    }

    private class DebugOutputTimerTask extends TimerTask {
        public void run() {
            outputDebugInfo();
        }
    }

    private static class Settings implements Serializable {
        private TimeDuration testDuration;
        private int threads;
        private int valueLength;
        private int batchSize;
    }

    private static class Results implements Serializable {
        private String dbClass;
        private String duration;
        private int recordsAdded;
        private String dbSize;
        private String eventsInDb;
    }

    private static class RandomValueMaker {
        private int outputLength;
        final StringBuffer randomValue = new StringBuffer();
        final Random random = new Random();

        public RandomValueMaker(final int outputLength) {
            this.outputLength = outputLength;
            randomValue.append(PwmRandom.getInstance().alphaNumericString(outputLength * 50));
        }

        public String next() {
            final int randomPos = random.nextInt(randomValue.length() - 1);
            randomValue.replace(randomPos, randomPos + 1,String.valueOf(random.nextInt(9)));

            final int startPos = random.nextInt(randomValue.length() - outputLength);
            final int endPos = startPos + outputLength;


            return randomValue.substring(startPos,endPos);
        }
    }
}
