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
import password.pwm.util.pwmdb.PwmDB;
import password.pwm.util.pwmdb.PwmDBException;
import password.pwm.util.pwmdb.PwmDBFactory;

import java.io.File;

public class PwmDBTest extends TestCase {

    private final PwmDB.DB TEST_DB = PwmDB.DB.TEMP;
    private PwmDB pwmDB;

    @Override
    protected void setUp() throws Exception {
        super.setUp();    //To change body of overridden methods use File | Settings | File Templates.
        TestHelper.setupLogging();
        final File fileLocation = new File(TestHelper.getParameter("pwmDBlocation"));
        pwmDB = PwmDBFactory.getInstance(fileLocation, null, null, false);
        pwmDB.truncate(TEST_DB);
        Assert.assertEquals(0,pwmDB.size(TEST_DB));
    }

    public void testPut() throws PwmDBException {
        Assert.assertNull(pwmDB.get(TEST_DB,"testKey1"));
        pwmDB.put(TEST_DB,"testKey1","testValue1");
        Assert.assertEquals(pwmDB.get(TEST_DB,"testKey1"),"testValue1");
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (pwmDB != null) {
            pwmDB.close();
            pwmDB = null;
        }
    }

}
