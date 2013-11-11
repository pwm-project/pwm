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
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.ConfigurationReader;
import password.pwm.config.PwmSetting;
import password.pwm.util.*;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.stats.EventRateMeter;

import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class PwmDBLoggerTest extends TestCase {

    private static final int BULK_EVENT_COUNT = 150 * 1000 * 1000;

    private LocalDBLogger localDBLogger;
    private LocalDB pwmDB;
    private Configuration config;
    private int maxSize;

    private int eventsAdded;
    private int eventsRemaining;
    final StringBuffer randomValue = new StringBuffer();
    final Random random = new Random();

    private EventRateMeter eventRateMeter = new EventRateMeter(new TimeDuration(5 * 60 * 1000));


    @Override
    protected void setUp() throws Exception {
        super.setUp();    //To change body of overridden methods use File | Settings | File Templates.
        TestHelper.setupLogging();
        final File fileLocation = new File(TestHelper.getParameter("pwmDBlocation"));
        final File configFileLocation = new File(TestHelper.getParameter("pwmConfigurationLocation"));
        final ConfigurationReader reader = new ConfigurationReader(configFileLocation);
        config = reader.getConfiguration();

        pwmDB = LocalDBFactory.getInstance(
                fileLocation,
                false,
                null
        );

        maxSize = (int)reader.getConfiguration().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
        final long maxAge = reader.getConfiguration().readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_AGE) * 1000l;

        localDBLogger = new LocalDBLogger(pwmDB, maxSize, maxAge);

        {
            final int randomLength = 4000;
            while (randomValue.length() < randomLength) {
                randomValue.append(PwmRandom.getInstance().nextChar());
            }
        }
    }

    public void testBulkAddEvents() {
        final int startingSize = localDBLogger.getStoredEventCount();
        eventsRemaining = BULK_EVENT_COUNT;
        eventsAdded = 0;
        final Timer timer = new Timer();
        timer.scheduleAtFixedRate(new DebugOutputTimerTask(),5 * 1000, 5 * 1000);

        for (int loopCount = 0; loopCount < 1; loopCount++) {
            final Thread populatorThread = new PopulatorThread();
            populatorThread.start();
        }

        while (eventsRemaining > 0) {
            Helper.pause(5);
        }

        final long startWaitTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startWaitTime < 30 * 1000 && localDBLogger.getPendingEventCount() > 0) {
            Helper.pause(500);
        }
        Helper.pause(5000);

        if (startingSize + BULK_EVENT_COUNT >= maxSize) {
            Assert.assertEquals(localDBLogger.getStoredEventCount(), maxSize);
        } else {
            Assert.assertEquals(localDBLogger.getStoredEventCount(), startingSize + BULK_EVENT_COUNT);
        }
        timer.cancel();
    }

    private class PopulatorThread extends Thread {
        public void run() {
            final int loopCount = 10;
            while (eventsRemaining > 0) {
                while (localDBLogger.getPendingEventCount() >= PwmConstants.PWMDB_LOGGER_MAX_QUEUE_SIZE - (loopCount + 1)) {
                    Helper.pause(1);
                }
                final Collection<PwmLogEvent> events = makeBulkEvents(loopCount);
                for (final PwmLogEvent logEvent : events) {
                    localDBLogger.writeEvent(logEvent);
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
            final int randomPos = random.nextInt(randomValue.length() - 1);
            randomValue.replace(randomPos, randomPos + 1,String.valueOf(random.nextInt(9)));

            final StringBuilder description = new StringBuilder(randomValue);
            final PwmLogEvent event = new PwmLogEvent(
                    new Date(System.currentTimeMillis()),
                    LocalDBLogger.class.getName(),
                    description.toString(), "", "", null, PwmLogLevel.TRACE);
            events.add(event);
        }

        return events;
    }

    private class DebugOutputTimerTask extends TimerTask {
        public void run() {
            final StringBuilder sb = new StringBuilder();
            final long maxEvents = config.readSettingAsLong(PwmSetting.EVENTS_PWMDB_MAX_EVENTS);
            final long eventCount = localDBLogger.getStoredEventCount();
            final double percentComplete = (double)eventCount / (double)maxEvents * 100f;
            final NumberFormat numberFormat = NumberFormat.getNumberInstance();
            numberFormat.setMaximumFractionDigits(3);
            numberFormat.setMinimumFractionDigits(3);
            sb.append(new SimpleDateFormat("HH:mm:ss").format(new Date()));
            sb.append(", added ").append(eventsAdded);
            sb.append(", db size: ").append(Helper.formatDiskSize(Helper.getFileDirectorySize(pwmDB.getFileLocation())));
            sb.append(", events: ").append(localDBLogger.getStoredEventCount()).append("/").append(maxEvents);
            sb.append(" (").append(numberFormat.format(percentComplete)).append("%)");
            sb.append(", free space: ").append(Helper.formatDiskSize(Helper.diskSpaceRemaining(pwmDB.getFileLocation())));
            sb.append(", pending: ").append(localDBLogger.getPendingEventCount());
            sb.append(", txn size: ").append(localDBLogger.getTransactionSize());
            sb.append(", eps: ").append(eventRateMeter.readEventRate().setScale(0, RoundingMode.UP));
            System.out.println(sb);
        }
    }
}
