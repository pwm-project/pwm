/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import junit.framework.Assert;
import junit.framework.TestCase;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.util.*;
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBFactory;
import password.pwm.util.stats.EventRateMeter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class PwmDBLoggerTest extends TestCase {

    private static final int BULK_EVENT_SIZE = 50 * 1000 * 1000;

    private PwmDBLogger pwmDBLogger;
    private PwmDB pwmDB;
    private int maxSize;

    private int eventsAdded;
    private int eventsRemaining;
    final StringBuilder randomValue = new StringBuilder();
    final PwmRandom random = PwmRandom.getInstance();

    private EventRateMeter eventRateMeter = new EventRateMeter(new TimeDuration(2 * 60 * 1000));


    @Override
    protected void setUp() throws Exception {

        super.setUp();    //To change body of overridden methods use File | Settings | File Templates.
        TestHelper.setupLogging();
        final File fileLocation = new File(TestHelper.getParameter("pwmDBlocation"));
        final File configFileLocation = new File(TestHelper.getParameter("pwmConfigurationLocation"));
        final ConfigurationReader reader = new ConfigurationReader(configFileLocation);
        final Map<String,String> initStrings = Configuration.convertStringListToNameValuePair(reader.getConfiguration().readSettingAsStringArray(PwmSetting.PWMDB_INIT_STRING),"=");

        pwmDB = PwmDBFactory.getInstance(
                fileLocation,
                reader.getConfiguration().readSettingAsString(PwmSetting.PWMDB_IMPLEMENTATION),
                initStrings,
                false
        );

        maxSize = (int)reader.getConfiguration().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
        final long maxAge = reader.getConfiguration().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE) * 1000l;

        pwmDBLogger = new PwmDBLogger(pwmDB, maxSize, maxAge);

        {
            final int randomLength = 20 * 1000;
            while (randomValue.length() < randomLength) {
                randomValue.append(String.valueOf(random.nextChar()));
            }
        }
    }

    public void testBulkAddEvents() {
        final int startingSize = pwmDBLogger.getStoredEventCount();
        eventsRemaining = BULK_EVENT_SIZE;
        eventsAdded = 0;
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new DebugOutputTimerTask(),10 * 1000, 30 * 1000);
        final Thread populatorThread = new PopulatorThread();
        populatorThread.start();

        while (eventsRemaining > 0) {
            Helper.pause(500);
        }

        final long startWaitTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startWaitTime < 30 * 1000 && pwmDBLogger.getPendingEventCount() > 0) {
            Helper.pause(500);
        }

        if (startingSize + BULK_EVENT_SIZE >= maxSize) {
            Assert.assertEquals(pwmDBLogger.getStoredEventCount(), maxSize);
        } else {
            Assert.assertEquals(pwmDBLogger.getStoredEventCount(), startingSize + BULK_EVENT_SIZE);
        }
        timer.cancel();
    }

    private class PopulatorThread extends Thread {
        public void run() {
            final int loopCount = 100;
            while (eventsRemaining > 0) {
                while (pwmDBLogger.getPendingEventCount() >= 2000) Helper.pause(5);
                final Collection<PwmLogEvent> events = makeBulkEvents(loopCount);
                for (final PwmLogEvent logEvent : events) {
                    pwmDBLogger.writeEvent(logEvent);
                    eventRateMeter.markEvents(1);
                    eventsRemaining--;
                    eventsAdded++;
                }
            }
        }
    }


    private Collection<PwmLogEvent> makeBulkEvents(final int count) {

        final Collection<PwmLogEvent> events = new ArrayList<PwmLogEvent>();

        for (int i = 0; i < count; i++) {
            randomValue.deleteCharAt(random.nextInt(randomValue.length()));
            randomValue.insert(random.nextInt(randomValue.length()),String.valueOf(random.nextInt(9)));

            final StringBuilder description = new StringBuilder();
            description.append("bulk insert event: ").append(System.currentTimeMillis()).append(" ");
            description.append(randomValue);

            final PwmLogEvent event = new PwmLogEvent(
                    new Date(),
                    PwmDBLogger.class.getName(),
                    description.toString(), "", "", null, PwmLogLevel.TRACE);
            events.add(event);
        }

        return events;
    }

    private class DebugOutputTimerTask extends TimerTask {
        public void run() {
            final StringBuilder sb = new StringBuilder();
            sb.append(new SimpleDateFormat("HH:mm:ss").format(new Date()));
            sb.append(", added ").append(eventsAdded).append(", ").append(eventsRemaining).append(" remaining");
            sb.append(", db size: ").append(Helper.formatDiskSize(Helper.getFileDirectorySize(pwmDB.getFileLocation())));
            sb.append(", stored events: ").append(pwmDBLogger.getStoredEventCount());
            sb.append(", free space: ").append(Helper.formatDiskSize(Helper.diskSpaceRemaining(pwmDB.getFileLocation())));
            sb.append(", pending: ").append(pwmDBLogger.getPendingEventCount());
            sb.append(", eps: ").append(eventRateMeter.readEventRate(new TimeDuration(2 * 60 * 1000),new TimeDuration(1000)));
            System.out.println(sb);
        }
    }
}
