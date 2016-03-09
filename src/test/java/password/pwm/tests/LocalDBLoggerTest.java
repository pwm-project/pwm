/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.tests;

import junit.framework.TestCase;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.util.FileSystemUtility;
import password.pwm.util.Helper;
import password.pwm.util.Percent;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.logging.LocalDBLogger;
import password.pwm.util.logging.LocalDBLoggerSettings;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogLevel;
import password.pwm.util.secure.PwmRandom;

import java.io.File;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalDBLoggerTest extends TestCase {

    private static final int BULK_EVENT_COUNT = 150 * 1000 * 1000;

    private LocalDBLogger localDBLogger;
    private LocalDB localDB;
    private Configuration config;


    private final AtomicInteger eventsAdded = new AtomicInteger(0);
    private final AtomicInteger eventsRemaining = new AtomicInteger(0);
    final StringBuffer randomValue = new StringBuffer();
    final Random random = new Random();

    private EventRateMeter eventRateMeter = new EventRateMeter(new TimeDuration(60 * 1000));



    @Override
    protected void setUp() throws Exception {
        super.setUp();    //To change body of overridden methods use File | Settings | File Templates.
        password.pwm.tests.TestHelper.setupLogging();
        final File localDBPath = new File(password.pwm.tests.TestHelper.getParameter("localDBPath"));
        final File configFile = new File(password.pwm.tests.TestHelper.getParameter("configurationFile"));
        final ConfigurationReader reader = new ConfigurationReader(configFile);
        config = reader.getConfiguration();

        localDB = LocalDBFactory.getInstance(
                localDBPath,
                false,
                null,
                config
        );


        final int maxEvents = (int)reader.getConfiguration().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
        final long maxAgeMs = reader.getConfiguration().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE) * (long)1000;
        final LocalDBLoggerSettings settings = new LocalDBLoggerSettings(maxEvents, new TimeDuration(maxAgeMs), Collections.<LocalDBLoggerSettings.Flag>emptySet());

        localDBLogger = new LocalDBLogger(null, localDB, settings);

        {
            final int randomLength = 84000;
            while (randomValue.length() < randomLength) {
                randomValue.append(PwmRandom.getInstance().nextChar());
            }
        }
    }

    public void testBulkAddEvents() {
        final int startingSize = localDBLogger.getStoredEventCount();
        eventsRemaining.addAndGet(BULK_EVENT_COUNT);
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new DebugOutputTimerTask(),5 * 1000, 5 * 1000);

        for (int loopCount = 0; loopCount < 5; loopCount++) {
            final Thread populatorThread = new PopulatorThread();
            populatorThread.start();
        }

        while (eventsRemaining.get() > 0) {
            Helper.pause(5);
        }

        final long startWaitTime = System.currentTimeMillis();
        while (TimeDuration.fromCurrent(startWaitTime).isShorterThan(new TimeDuration(10, TimeUnit.HOURS)) && eventsRemaining.get() > 0) {
            Helper.pause(500);
        }
        Helper.pause(5000);

        timer.cancel();
    }


    private class PopulatorThread extends Thread {
        public void run() {
            int loopCount = 3;
            while (eventsRemaining.get() > 0) {
                final Collection<PwmLogEvent> events = makeEvents(loopCount);
                for (final PwmLogEvent logEvent : events) {
                    localDBLogger.writeEvent(logEvent);
                    eventRateMeter.markEvents(1);
                    eventsRemaining.decrementAndGet();
                    eventsAdded.incrementAndGet();
                }
            }
        }
    }

    private Collection<PwmLogEvent> makeEvents(final int count) {
        final Collection<PwmLogEvent> events = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            events.add(makeEvent());
        }

        //System.out.println("made "  + size + " events in " + TimeDuration.fromCurrent(startTime).asCompactString());
        return events;
    }

    private PwmLogEvent makeEvent() {
        final int randomPos = random.nextInt(randomValue.length() - 1);
        randomValue.replace(randomPos, randomPos + 1,String.valueOf(random.nextInt(9)));

        final int startPos = random.nextInt(randomValue.length() - 100);
        final int endPos = startPos + random.nextInt(randomValue.length() - startPos);

        final String description = randomValue.substring(startPos,endPos);
        return PwmLogEvent.createPwmLogEvent(
                new Date(System.currentTimeMillis()),
                LocalDBLogger.class.getName(),
                description, "", "", null, null, PwmLogLevel.TRACE);
    }

    private class DebugOutputTimerTask extends TimerTask {
        public void run() {
            final StringBuilder sb = new StringBuilder();
            final long maxEvents = config.readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
            final long eventCount = localDBLogger.getStoredEventCount();
            final Percent percent = new Percent(eventCount,maxEvents);
            sb.append(PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date()));
            sb.append(", added ").append(eventsAdded);
            sb.append(", db size: ").append(Helper.formatDiskSize(FileSystemUtility.getFileDirectorySize(localDB.getFileLocation())));
            sb.append(", events: ").append(localDBLogger.getStoredEventCount()).append("/").append(maxEvents);
            sb.append(" (").append(percent.pretty(3)).append(")");
            sb.append(", free space: ").append(Helper.formatDiskSize(
                    FileSystemUtility.diskSpaceRemaining(localDB.getFileLocation())));
            sb.append(", eps: ").append(eventRateMeter.readEventRate().setScale(0, RoundingMode.UP));
            System.out.println(sb);
        }
    }
}
