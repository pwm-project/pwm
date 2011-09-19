/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class PwmDBLoggerTest extends TestCase {

    private static final int MAX_SIZE = 40 * 1000 * 1000 ;
    private static final long MAG_AGE_MS = 1000;
    private static final int BULK_EVENT_SIZE = 20 * 1000 * 1000;

    private PwmDBLogger pwmDBLogger;
    private PwmDB pwmDB;

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
        pwmDBLogger = new PwmDBLogger(pwmDB,  MAX_SIZE, MAG_AGE_MS);

    }

    public void testBulkAddEvents() {
        final int startingSize = pwmDBLogger.getStoredEventCount();
        final int writesPerCycle = 1000;
        int eventsRemaining = BULK_EVENT_SIZE;
        int eventsAdded = 0;

        while (eventsRemaining > 0) {
            while (pwmDBLogger.getPendingEventCount() >= writesPerCycle) Helper.pause(100);

            final Collection<PwmLogEvent> events = makeBulkEvents(writesPerCycle + 1);
            for (final PwmLogEvent logEvent : events) {
                pwmDBLogger.writeEvent(logEvent);
                eventsRemaining--;
                eventsAdded++;
                if (eventsAdded % (10 * 1000) == 0) {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("added ").append(eventsAdded).append(", ").append(eventsRemaining).append(" remaining");
                    sb.append(", db size: ").append(Helper.formatDiskSize(Helper.getFileDirectorySize(pwmDB.getFileLocation())));
                    sb.append(", stored event count: ").append(pwmDBLogger.getStoredEventCount());
                    sb.append(", free space: ").append(Helper.formatDiskSize(Helper.diskSpaceRemaining(pwmDB.getFileLocation())));
                    System.out.println(sb);
                }
            }
        }

        final long startWaitTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startWaitTime < 30 * 1000 && pwmDBLogger.getPendingEventCount() > 0) {
            Helper.pause(500);
        }

        if (startingSize + BULK_EVENT_SIZE >= MAX_SIZE) {
            Assert.assertEquals(pwmDBLogger.getStoredEventCount(), MAX_SIZE);
        } else {
            Assert.assertEquals(pwmDBLogger.getStoredEventCount(), startingSize + BULK_EVENT_SIZE);
        }
    }


    private static Collection<PwmLogEvent> makeBulkEvents(final int count) {

        final Collection<PwmLogEvent> events = new ArrayList<PwmLogEvent>();
        final PwmRandom random = PwmRandom.getInstance();
        final String randomDescr = random.alphaNumericString(1024 * 4);

        for (int i = 0; i < count; i++) {
            final StringBuilder description = new StringBuilder();
            description.append("bulk insert event: ").append(System.currentTimeMillis()).append(" ");
            description.append(randomDescr);

            final PwmLogEvent event = new PwmLogEvent(
                    new Date(),
                    PwmDBLogger.class.getName(),
                    description.toString(), "", "", null, PwmLogLevel.TRACE);
            events.add(event);
        }

        return events;
    }

}
