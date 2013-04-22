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

import junit.framework.TestCase;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.localdb.LocalDBStoredQueue;

import java.io.File;

public class PwmDBStoredQueueTest extends TestCase {

    private static final int SIZE = 5;

    private LocalDBStoredQueue storedQueue;
    private LocalDB pwmDB;

    @Override
    protected void setUp() throws Exception {

        super.setUp();    //To change body of overridden methods use File | Settings | File Templates.
        TestHelper.setupLogging();
        final File fileLocation = new File(TestHelper.getParameter("pwmDBlocation"));
        pwmDB = LocalDBFactory.getInstance(fileLocation, null, null, false, null);
        storedQueue = LocalDBStoredQueue.createPwmDBStoredQueue(pwmDB, LocalDB.DB.TEMP);
    }

    private void populatedQueue(final int n, final LocalDBStoredQueue storedQueue) {
        storedQueue.clear();
        assertTrue(storedQueue.isEmpty());
        for (int i = 0; i < n; ++i)
            assertTrue(storedQueue.offer(String.valueOf(i)));
        assertFalse(storedQueue.isEmpty());
        assertEquals(n, storedQueue.size());
    }


    /**
     * isEmpty is true before add, false after
     */
    public void testEmpty() {
        storedQueue.clear();
        assertTrue(storedQueue.isEmpty());

        storedQueue.add("value1");
        assertFalse(storedQueue.isEmpty());
        storedQueue.add("value2");
        storedQueue.remove();
        storedQueue.remove();
        assertTrue(storedQueue.isEmpty());
    }

    /**
     * size changes when elements added and removed
     */
    public void testSize() {
        storedQueue.clear();
        assertTrue(storedQueue.isEmpty());

        populatedQueue(SIZE, storedQueue);
        assertEquals(SIZE, storedQueue.size());
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(SIZE - i, storedQueue.size());
            storedQueue.remove();
        }
        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, storedQueue.size());
            storedQueue.add(String.valueOf(i));
        }
    }

    /**
     * Offer succeeds
     */
    public void testOffer() {
        storedQueue.clear();
        assertTrue(storedQueue.isEmpty());

        assertTrue(storedQueue.offer(String.valueOf(0)));
        assertTrue(storedQueue.offer(String.valueOf(1)));
        assertEquals(2, storedQueue.size());
    }

    /**
     * add succeeds
     */
    public void testAdd() {
        storedQueue.clear();
        assertTrue(storedQueue.isEmpty());

        for (int i = 0; i < SIZE; ++i) {
            assertEquals(i, storedQueue.size());
            assertTrue(storedQueue.add(String.valueOf(i)));
        }
    }

    /**
     * poll succeeds unless empty
     */
    public void testPoll() {
        storedQueue.clear();
        assertTrue(storedQueue.isEmpty());

        populatedQueue(SIZE, storedQueue);

        for (int i = SIZE - 1; i >= 0; i--) {
            System.out.println("iteration " + i);
            assertEquals(i, Integer.parseInt(storedQueue.poll()));
        }

        assertNull(storedQueue.poll());
    }

    /**
     * peek returns next element, or null if empty
     */
    public void testPeek() {
        storedQueue.clear();
        assertTrue(storedQueue.isEmpty());

        populatedQueue(SIZE, storedQueue);

        int initialSize = storedQueue.size();
        assertNotNull(storedQueue.peek());
        assertEquals(initialSize, storedQueue.size());
    }

    @Override
    protected void tearDown() throws Exception {
        System.out.println("tearing down");
        super.tearDown();
        if (storedQueue != null) {
            storedQueue = null;
        }
        if (pwmDB != null) {
            pwmDB.close();
            pwmDB = null;
        }
    }
}