/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.util.localdb;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.util.java.TimeDuration;

import java.io.File;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class LocalDBStoredQueueExtendedTest
{
    @ClassRule
    public static TemporaryFolder temporaryFolder = new TemporaryFolder( );

    private static final int SIZE = 5;

    private static LocalDBStoredQueue storedQueue;
    private static LocalDB localDB;

    private static final boolean ENABLE_DEBUG_OUTPUT = false;

    @BeforeClass
    public static void setUp() throws Exception
    {

        TestHelper.setupLogging();
        final File fileLocation = temporaryFolder.newFolder( "localdb-storedqueue-test" );
        localDB = LocalDBFactory.getInstance( fileLocation, false, null, null );
        storedQueue = LocalDBStoredQueue.createLocalDBStoredQueue( localDB, LocalDB.DB.TEMP, ENABLE_DEBUG_OUTPUT );
    }

    private void populatedQueue( final int n, final LocalDBStoredQueue storedQueue )
    {
        storedQueue.clear();
        Assert.assertTrue( storedQueue.isEmpty() );
        for ( int i = 0; i < n; ++i )
        {
            Assert.assertTrue( storedQueue.offer( String.valueOf( i ) ) );
        }
        Assert.assertFalse( storedQueue.isEmpty() );
        Assert.assertEquals( n, storedQueue.size() );
    }


    /**
     * isEmpty is true before add, false after.
     */
    @Test
    public void testEmpty()
    {
        storedQueue.clear();
        Assert.assertTrue( storedQueue.isEmpty() );
    }

    @Test
    public void testRemove()
    {
        storedQueue.clear();
        storedQueue.add( "value1" );
        storedQueue.add( "value2" );
        Assert.assertEquals( 2, storedQueue.size() );
        storedQueue.remove();
        Assert.assertEquals( 1, storedQueue.size() );
        storedQueue.remove();
        Assert.assertTrue( storedQueue.isEmpty() );
        Assert.assertEquals( 0, storedQueue.size() );

        try
        {
            storedQueue.remove();
            Assert.assertTrue( false );
        }
        catch ( final NoSuchElementException e )
        {
            Assert.assertTrue( true );
        }
        catch ( final Exception e )
        {
            Assert.assertTrue( false );
        }

        Assert.assertTrue( storedQueue.isEmpty() );
        Assert.assertEquals( 0, storedQueue.size() );
    }

    @Test
    public void testDequeue()
    {
        storedQueue.clear();
        Assert.assertEquals( 0, storedQueue.size() );
        storedQueue.addLast( "value3" );
        Assert.assertEquals( 1, storedQueue.size() );
        storedQueue.addLast( "value4" );
        Assert.assertEquals( 2, storedQueue.size() );
        storedQueue.addFirst( "value2" );
        Assert.assertEquals( 3, storedQueue.size() );
        storedQueue.addFirst( "value1" );
        Assert.assertEquals( 4, storedQueue.size() );
        storedQueue.addFirst( "value0" );
        Assert.assertEquals( 5, storedQueue.size() );

        {
            final Iterator<String> iter = storedQueue.iterator();
            iter.hasNext();
            Assert.assertEquals( "value0", iter.next() );
            Assert.assertEquals( "value1", iter.next() );
            Assert.assertEquals( "value2", iter.next() );
            Assert.assertEquals( "value3", iter.next() );
            Assert.assertEquals( "value4", iter.next() );
        }

        {
            final Iterator<String> iter = storedQueue.descendingIterator();
            iter.hasNext();
            Assert.assertEquals( "value4", iter.next() );
            Assert.assertEquals( "value3", iter.next() );
            Assert.assertEquals( "value2", iter.next() );
            Assert.assertEquals( "value1", iter.next() );
            Assert.assertEquals( "value0", iter.next() );
        }

        Assert.assertEquals( 5, storedQueue.size() );
        Assert.assertEquals( "value0", storedQueue.removeFirst() );
        Assert.assertEquals( 4, storedQueue.size() );
        Assert.assertEquals( "value4", storedQueue.removeLast() );
        Assert.assertEquals( 3, storedQueue.size() );

        Assert.assertEquals( "value3", storedQueue.peekLast() );
        Assert.assertEquals( "value1", storedQueue.peekFirst() );
        Assert.assertEquals( "value3", storedQueue.pollLast() );
        Assert.assertEquals( "value1", storedQueue.pollFirst() );
        Assert.assertEquals( "value2", storedQueue.peek() );
        Assert.assertEquals( "value2", storedQueue.peekLast() );
        Assert.assertEquals( "value2", storedQueue.peekFirst() );
        Assert.assertEquals( 1, storedQueue.size() );
    }

    /**
     * size changes when elements added and removed.
     */
    @Test
    public void testSize()
    {
        storedQueue.clear();
        Assert.assertTrue( storedQueue.isEmpty() );

        populatedQueue( SIZE, storedQueue );
        Assert.assertEquals( SIZE, storedQueue.size() );
        for ( int i = 0; i < SIZE; ++i )
        {
            TimeDuration.of( 100, TimeDuration.Unit.MILLISECONDS ).pause();
            Assert.assertEquals( SIZE - i, storedQueue.size() );
            storedQueue.remove();
        }
        for ( int i = 0; i < SIZE; ++i )
        {
            Assert.assertEquals( i, storedQueue.size() );
            storedQueue.add( String.valueOf( i ) );
        }
    }

    /**
     * Offer succeeds.
     */
    @Test
    public void testOffer()
    {
        storedQueue.clear();
        Assert.assertTrue( storedQueue.isEmpty() );

        Assert.assertTrue( storedQueue.offer( String.valueOf( 0 ) ) );
        Assert.assertTrue( storedQueue.offer( String.valueOf( 1 ) ) );
        Assert.assertEquals( 2, storedQueue.size() );
    }

    /**
     * add succeeds.
     */
    @Test
    public void testAdd()
    {
        storedQueue.clear();
        Assert.assertTrue( storedQueue.isEmpty() );

        for ( int i = 0; i < SIZE; ++i )
        {
            Assert.assertEquals( i, storedQueue.size() );
            Assert.assertTrue( storedQueue.add( String.valueOf( i ) ) );
        }
    }

    /**
     * poll succeeds unless empty.
     */
    @Test
    public void testPoll()
    {
        storedQueue.clear();
        Assert.assertTrue( storedQueue.isEmpty() );

        populatedQueue( SIZE, storedQueue );

        for ( int i = SIZE - 1; i >= 0; i-- )
        {
            Assert.assertEquals( i, Integer.parseInt( storedQueue.poll() ) );
        }

        Assert.assertNull( storedQueue.poll() );
    }

    /**
     * peek returns next element, or null if empty.
     */
    @Test
    public void testPeek()
    {
        storedQueue.clear();
        Assert.assertTrue( storedQueue.isEmpty() );

        populatedQueue( SIZE, storedQueue );

        final int initialSize = storedQueue.size();
        Assert.assertNotNull( storedQueue.peek() );
        Assert.assertEquals( initialSize, storedQueue.size() );
    }

    @AfterClass
    public static void tearDown() throws Exception
    {
        if ( storedQueue != null )
        {
            storedQueue = null;
        }
        if ( localDB != null )
        {
            localDB.close();
            localDB = null;
        }
    }
}
