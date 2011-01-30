/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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
import password.pwm.util.db.Berkeley_PwmDb;
import password.pwm.util.db.PwmDB;
import password.pwm.util.db.PwmDBFactory;
import password.pwm.util.db.PwmDBStoredQueue;

import java.io.File;

public class PwmDBStoredQueueTest extends TestCase {
    public void testStoredQueue() throws Exception {
        final File fileLocation = new File(TestHelper.getParameter("pwmDBlocation"));
        final PwmDB pwmDB = PwmDBFactory.getInstance(fileLocation, Berkeley_PwmDb.class.toString(), null);
        final PwmDBStoredQueue storedQueue = PwmDBStoredQueue.createPwmDBStoredQueue(pwmDB, PwmDB.DB.TEMP);

        storedQueue.clear();
        Assert.assertTrue(storedQueue.isEmpty());
    }
}
