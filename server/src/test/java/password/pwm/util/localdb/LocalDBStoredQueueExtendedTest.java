/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.TimeDuration;

import java.io.File;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class LocalDBStoredQueueExtendedTest
{
    @TempDir
    public static Path temporaryFolder;

    private static final int SIZE = 5;

    private static LocalDBStoredQueue storedQueue;
    private static LocalDB localDB;

    private static final boolean ENABLE_DEBUG_OUTPUT = false;

    @BeforeAll
    public static void setUp() throws Exception
    {

        TestHelper.setupLogging();
        final File fileLocation = FileSystemUtility.createDirectory( temporaryFolder, "localdb-storedqueue-test" );
        localDB = LocalDBFactory.getInstance( fileLocation, false, null, null );
        storedQueue = LocalDBStoredQueue.createLocalDBStoredQueue( localDB, LocalDB.DB.TEMP, ENABLE_DEBUG_OUTPUT );
    }

    private void populatedQueue( final int n, final LocalDBStoredQueue storedQueue )
    {
        storedQueue.clear();
        Assertions.assertTrue( storedQueue.isEmpty() );
        for ( int i = 0; i < n; ++i )
        {
            Assertions.assertTrue( storedQueue.offer( String.valueOf( i ) ) );
        }
        Assertions.assertFalse( storedQueue.isEmpty() );
        Assertions.assertEquals( n, storedQueue.size() );
    }


    /**
     * isEmpty is true before add, false after.
     */
    @Test
    public void testEmpty()
    {
        storedQueue.clear();
        Assertions.assertTrue( storedQueue.isEmpty() );
    }

    @Test
    public void testRemove()
    {
        storedQueue.clear();
        storedQueue.add( "value1" );
        storedQueue.add( "value2" );
        Assertions.assertEquals( 2, storedQueue.size() );
        storedQueue.remove();
        Assertions.assertEquals( 1, storedQueue.size() );
        storedQueue.remove();
        Assertions.assertTrue( storedQueue.isEmpty() );
        Assertions.assertEquals( 0, storedQueue.size() );

        try
        {
            storedQueue.remove();
            Assertions.fail();
        }
        catch ( final NoSuchElementException e )
        {
            Assertions.assertTrue( true );
        }
        catch ( final Exception e )
        {
            Assertions.fail();
        }

        Assertions.assertTrue( storedQueue.isEmpty() );
        Assertions.assertEquals( 0, storedQueue.size() );
    }

    @Test
    public void testDequeue()
    {
        storedQueue.clear();
        Assertions.assertEquals( 0, storedQueue.size() );
        storedQueue.addLast( "value3" );
        Assertions.assertEquals( 1, storedQueue.size() );
        storedQueue.addLast( "value4" );
        Assertions.assertEquals( 2, storedQueue.size() );
        storedQueue.addFirst( "value2" );
        Assertions.assertEquals( 3, storedQueue.size() );
        storedQueue.addFirst( "value1" );
        Assertions.assertEquals( 4, storedQueue.size() );
        storedQueue.addFirst( "value0" );
        Assertions.assertEquals( 5, storedQueue.size() );

        {
            final Iterator<String> iter = storedQueue.iterator();
            Assertions.assertTrue( iter.hasNext() );
            Assertions.assertEquals( "value0", iter.next() );
            Assertions.assertEquals( "value1", iter.next() );
            Assertions.assertEquals( "value2", iter.next() );
            Assertions.assertEquals( "value3", iter.next() );
            Assertions.assertEquals( "value4", iter.next() );
        }

        {
            final Iterator<String> iter = storedQueue.descendingIterator();
            Assertions.assertTrue( iter.hasNext() );
            Assertions.assertEquals( "value4", iter.next() );
            Assertions.assertEquals( "value3", iter.next() );
            Assertions.assertEquals( "value2", iter.next() );
            Assertions.assertEquals( "value1", iter.next() );
            Assertions.assertEquals( "value0", iter.next() );
        }

        Assertions.assertEquals( 5, storedQueue.size() );
        Assertions.assertEquals( "value0", storedQueue.removeFirst() );
        Assertions.assertEquals( 4, storedQueue.size() );
        Assertions.assertEquals( "value4", storedQueue.removeLast() );
        Assertions.assertEquals( 3, storedQueue.size() );

        Assertions.assertEquals( "value3", storedQueue.peekLast() );
        Assertions.assertEquals( "value1", storedQueue.peekFirst() );
        Assertions.assertEquals( "value3", storedQueue.pollLast() );
        Assertions.assertEquals( "value1", storedQueue.pollFirst() );
        Assertions.assertEquals( "value2", storedQueue.peek() );
        Assertions.assertEquals( "value2", storedQueue.peekLast() );
        Assertions.assertEquals( "value2", storedQueue.peekFirst() );
        Assertions.assertEquals( 1, storedQueue.size() );
    }

    /**
     * size changes when elements added and removed.
     */
    @Test
    public void testSize()
    {
        storedQueue.clear();
        Assertions.assertTrue( storedQueue.isEmpty() );

        populatedQueue( SIZE, storedQueue );
        Assertions.assertEquals( SIZE, storedQueue.size() );
        for ( int i = 0; i < SIZE; ++i )
        {
            TimeDuration.of( 100, TimeDuration.Unit.MILLISECONDS ).pause();
            Assertions.assertEquals( SIZE - i, storedQueue.size() );
            storedQueue.remove();
        }
        for ( int i = 0; i < SIZE; ++i )
        {
            Assertions.assertEquals( i, storedQueue.size() );
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
        Assertions.assertTrue( storedQueue.isEmpty() );

        Assertions.assertTrue( storedQueue.offer( String.valueOf( 0 ) ) );
        Assertions.assertTrue( storedQueue.offer( String.valueOf( 1 ) ) );
        Assertions.assertEquals( 2, storedQueue.size() );
    }

    /**
     * add succeeds.
     */
    @Test
    public void testAdd()
    {
        storedQueue.clear();
        Assertions.assertTrue( storedQueue.isEmpty() );

        for ( int i = 0; i < SIZE; ++i )
        {
            Assertions.assertEquals( i, storedQueue.size() );
            Assertions.assertTrue( storedQueue.add( String.valueOf( i ) ) );
        }
    }

    /**
     * poll succeeds unless empty.
     */
    @Test
    public void testPoll()
    {
        storedQueue.clear();
        Assertions.assertTrue( storedQueue.isEmpty() );

        populatedQueue( SIZE, storedQueue );

        for ( int i = SIZE - 1; i >= 0; i-- )
        {
            Assertions.assertEquals( i, Integer.parseInt( storedQueue.poll() ) );
        }

        Assertions.assertNull( storedQueue.poll() );
    }

    /**
     * peek returns next element, or null if empty.
     */
    @Test
    public void testPeek()
    {
        storedQueue.clear();
        Assertions.assertTrue( storedQueue.isEmpty() );

        populatedQueue( SIZE, storedQueue );

        final int initialSize = storedQueue.size();
        Assertions.assertNotNull( storedQueue.peek() );
        Assertions.assertEquals( initialSize, storedQueue.size() );
    }

    @AfterAll
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
