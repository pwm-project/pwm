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
import password.pwm.util.Helper;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBFactory;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.secure.PwmRandom;

import java.io.File;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class LocalDBStoredQueueTest extends TestCase {

    private static final int SIZE = 5;

    private LocalDBStoredQueue storedQueue;
    private LocalDB localDB;

    @Override
    protected void setUp() throws Exception {

        super.setUp();    //To change body of overridden methods use File | Settings | File Templates.
        TestHelper.setupLogging();
        final File fileLocation = new File(TestHelper.getParameter("pwmDBlocation"));
        localDB = LocalDBFactory.getInstance(fileLocation, false, null, null);
        storedQueue = LocalDBStoredQueue.createLocalDBStoredQueue(localDB, LocalDB.DB.TEMP, true);
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
    }

    public void testRemove() {
        storedQueue.clear();
        storedQueue.add("value1");
        storedQueue.add("value2");
        assertEquals(2, storedQueue.size());
        storedQueue.remove();
        assertEquals(1, storedQueue.size());
        storedQueue.remove();
        assertTrue(storedQueue.isEmpty());
        assertEquals(0, storedQueue.size());

        try {
            storedQueue.remove();
            assertTrue(false);
        } catch (NoSuchElementException e) {
            assertTrue(true);
        } catch (Exception e) {
            assertTrue(false);
        }

        assertTrue(storedQueue.isEmpty());
        assertEquals(0, storedQueue.size());
    }

    public void testDequeue() {
        storedQueue.clear();
        assertEquals(0,storedQueue.size());
        storedQueue.addLast("value3");
        assertEquals(1,storedQueue.size());
        storedQueue.addLast("value4");
        assertEquals(2,storedQueue.size());
        storedQueue.addFirst("value2");
        assertEquals(3,storedQueue.size());
        storedQueue.addFirst("value1");
        assertEquals(4,storedQueue.size());
        storedQueue.addFirst("value0");
        assertEquals(5,storedQueue.size());

        {
            final Iterator<String> iter = storedQueue.iterator(); iter.hasNext();
            assertEquals("value0",iter.next());
            assertEquals("value1",iter.next());
            assertEquals("value2",iter.next());
            assertEquals("value3",iter.next());
            assertEquals("value4",iter.next());
        }

        {
            final Iterator<String> iter = storedQueue.descendingIterator(); iter.hasNext();
            assertEquals("value4",iter.next());
            assertEquals("value3",iter.next());
            assertEquals("value2",iter.next());
            assertEquals("value1",iter.next());
            assertEquals("value0",iter.next());
        }

        assertEquals(5,storedQueue.size());
        assertEquals("value0",storedQueue.removeFirst());
        assertEquals(4,storedQueue.size());
        assertEquals("value4",storedQueue.removeLast());
        assertEquals(3,storedQueue.size());

        assertEquals("value3",storedQueue.peekLast());
        assertEquals("value1",storedQueue.peekFirst());
        assertEquals("value3",storedQueue.pollLast());
        assertEquals("value1",storedQueue.pollFirst());
        assertEquals("value2",storedQueue.peek());
        assertEquals("value2",storedQueue.peekLast());
        assertEquals("value2",storedQueue.peekFirst());
        assertEquals(1,storedQueue.size());
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
            Helper.pause(PwmRandom.getInstance().nextInt(1000));
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
        if (localDB != null) {
            localDB.close();
            localDB = null;
        }
    }
}