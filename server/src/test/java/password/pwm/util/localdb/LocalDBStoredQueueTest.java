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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import password.pwm.PwmApplication;
import password.pwm.util.java.FileSystemUtility;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class LocalDBStoredQueueTest
{
    private static final int MAX_PROBLEM_SIZE = 10_000;

    @TempDir
    public Path temporaryFolder;

    private LocalDB localDB;
    private LocalDBStoredQueue localDBStoredQueue;

    @BeforeEach
    public void setUp() throws Exception
    {
        final File localDbTestFolder = FileSystemUtility.createDirectory( temporaryFolder, "test-stored-queue-test" );
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( localDbTestFolder );
        localDB = LocalDBFactory.getInstance( localDbTestFolder, false, pwmApplication.getPwmEnvironment(), pwmApplication.getConfig() );
        localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue( localDB, LocalDB.DB.TEMP, true );
    }

    @AfterEach
    public void shutdown() throws Exception
    {
        localDB.close();
        localDBStoredQueue = null;
    }

    @Test
    public void testStoredQueueSimple() throws LocalDBException
    {
        Assertions.assertEquals( 0, localDBStoredQueue.size() );

        localDBStoredQueue.add( "one" );
        Assertions.assertEquals( 1, localDBStoredQueue.size() );

        localDBStoredQueue.add( "two" );
        Assertions.assertEquals( 2, localDBStoredQueue.size() );

        localDBStoredQueue.add( "three" );
        Assertions.assertEquals( 3, localDBStoredQueue.size() );

        localDBStoredQueue.removeFirst();
        Assertions.assertEquals( 2, localDBStoredQueue.size() );

        {
            final List<String> values = new ArrayList<>( localDBStoredQueue );
            Assertions.assertEquals( 2, values.size() );
        }

        {
            final List<String> values = new ArrayList<>( localDBStoredQueue );
            Assertions.assertEquals( 2, values.size() );
        }
    }

    @Test
    public void testStoredQueueBulk() throws LocalDBException
    {
        Assertions.assertEquals( 0, localDBStoredQueue.size() );

        addValues( localDBStoredQueue, MAX_PROBLEM_SIZE );

        Assertions.assertEquals( MAX_PROBLEM_SIZE, localDBStoredQueue.size() );
        Assertions.assertEquals( String.valueOf( MAX_PROBLEM_SIZE - 1 ), localDBStoredQueue.getFirst() );
        Assertions.assertEquals( "0", localDBStoredQueue.getLast() );

        for ( int i = ( MAX_PROBLEM_SIZE - 1 ); i > -1; i-- )
        {
            localDBStoredQueue.removeLast();
            Assertions.assertEquals( i, localDBStoredQueue.size() );
        }
    }

    @Test
    public void testStoredQueueIterators()
    {
        Assertions.assertEquals( 0, localDBStoredQueue.size() );

        addValues( localDBStoredQueue, MAX_PROBLEM_SIZE );

        Assertions.assertEquals( MAX_PROBLEM_SIZE, localDBStoredQueue.size() );

        {
            final Iterator<String> iter = localDBStoredQueue.descendingIterator();
            for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
            {
                Assertions.assertTrue( iter.hasNext() );
                Assertions.assertTrue( iter.hasNext() );
                Assertions.assertEquals( String.valueOf( i ), iter.next() );
            }
            Assertions.assertFalse( iter.hasNext() );

            boolean seenNoSuchElementException = false;
            try
            {
                iter.next();
            }
            catch ( final NoSuchElementException e )
            {
                seenNoSuchElementException = true;
            }
            Assertions.assertTrue( seenNoSuchElementException );
        }

        {
            final Iterator<String> iter = localDBStoredQueue.iterator();
            for ( int i = ( MAX_PROBLEM_SIZE - 1 ); i > -1; i-- )
            {
                Assertions.assertTrue( iter.hasNext() );
                Assertions.assertTrue( iter.hasNext() );
                Assertions.assertEquals( String.valueOf( i ), iter.next() );
            }
            Assertions.assertFalse( iter.hasNext() );

            boolean seenNoSuchElementException = false;
            try
            {
                iter.next();
            }
            catch ( final NoSuchElementException e )
            {
                seenNoSuchElementException = true;
            }
            Assertions.assertTrue( seenNoSuchElementException );
        }
    }

    @Test
    public void testRemoveLast()
    {
        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( String.valueOf( i ) );
            Assertions.assertEquals( String.valueOf( i ), localDBStoredQueue.removeLast() );
        }

        localDBStoredQueue.clear();

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addLast( String.valueOf( i ) );
            Assertions.assertEquals( String.valueOf( i ), localDBStoredQueue.removeLast() );
        }

        localDBStoredQueue.clear();

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( String.valueOf( i ) );
        }

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            Assertions.assertEquals( String.valueOf( i ), localDBStoredQueue.removeLast() );
        }
    }

    @Test
    public void testAddFirst()
    {
        final String[] aBunchOfString = {
                "One",
                "Two",
                "Three",
                "Four",
        };

        for ( final String loopString : aBunchOfString )
        {
            localDBStoredQueue.addFirst( loopString );
        }

        for ( int i = aBunchOfString.length - 1; i >= 0; i-- )
        {
            Assertions.assertEquals( aBunchOfString[i], localDBStoredQueue.removeFirst() );
        }
    }

    @Test
    public void testAddLast()
    {
        final String[] aBunchOfString = {
                "One",
                "Two",
                "Three",
                "Four",
        };

        for ( final String loopString : aBunchOfString )
        {
            localDBStoredQueue.addLast( loopString );
        }

        for ( int i = aBunchOfString.length - 1; i >= 0; i-- )
        {
            Assertions.assertEquals( aBunchOfString[i], localDBStoredQueue.removeLast() );
        }
    }


    @Test
    public void testIsEmptyAfterAddRemoveFirst()
    {
        localDBStoredQueue.addFirst( "Something" );
        boolean empty = localDBStoredQueue.isEmpty();
        Assertions.assertFalse( empty );
        localDBStoredQueue.removeFirst();

        empty = localDBStoredQueue.isEmpty();
        Assertions.assertTrue( empty, "Should be empty after adding then removing" );

    }

    @Test
    public void testIsEmptyAfterAddRemoveLast()
    {
        localDBStoredQueue.addLast( "Something" );
        Assertions.assertFalse( localDBStoredQueue.isEmpty() );
        localDBStoredQueue.removeLast();
        Assertions.assertTrue( localDBStoredQueue.isEmpty(), "Should be empty after adding then removing" );

    }

    @Test
    public void testIsEmptyAfterAddFirstRemoveLast()
    {
        Assertions.assertTrue( localDBStoredQueue.isEmpty() );
        localDBStoredQueue.addFirst( "Something" );
        Assertions.assertFalse( localDBStoredQueue.isEmpty() );
        localDBStoredQueue.removeLast();
        Assertions.assertTrue( localDBStoredQueue.isEmpty(), "Should be empty after adding then removing" );
    }

    @Test
    public void testIsEmptyAfterAddLastRemoveFirst()
    {
        localDBStoredQueue.addLast( "Something" );
        Assertions.assertFalse( localDBStoredQueue.isEmpty() );
        localDBStoredQueue.removeFirst();
        Assertions.assertTrue( localDBStoredQueue.isEmpty(), "Should be empty after adding then removing" );
    }

    @Test
    public void testIsEmptyAfterMultipleAddRemove()
    {
        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( "Something" );
            Assertions.assertFalse( localDBStoredQueue.isEmpty(), "Should not be empty after " + i + " item added" );
        }

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            Assertions.assertFalse( localDBStoredQueue.isEmpty(), "Should not be empty after " + i + " item removed" );
            localDBStoredQueue.removeLast();
        }

        Assertions.assertTrue( localDBStoredQueue.isEmpty(),
                "Should be empty after adding and removing " + MAX_PROBLEM_SIZE + " elements." );
    }

    @Test
    public void testMultipleFillAndEmpty()
    {
        final int outerLoopIterations = 10;
        final int innerLoopIterations = 1_000;

        localDBStoredQueue.clear();

        for ( int tries = 0; tries < outerLoopIterations; tries++ )
        {
            for ( int i = 0; i < innerLoopIterations; i++ )
            {
                localDBStoredQueue.addFirst( String.valueOf( i ) );
            }

            Assertions.assertFalse( localDBStoredQueue.isEmpty() );
            int counter = 0;
            while ( !localDBStoredQueue.isEmpty() )
            {
                Assertions.assertEquals( String.valueOf( counter ), localDBStoredQueue.removeLast() );
                counter++;
            }

            Assertions.assertTrue( localDBStoredQueue.isEmpty() );

            for ( int j = 0; j < innerLoopIterations; j++ )
            {
                localDBStoredQueue.addLast( String.valueOf( j ) );
            }

            Assertions.assertFalse( localDBStoredQueue.isEmpty() );

            counter = 0;
            while ( !localDBStoredQueue.isEmpty() )
            {
                Assertions.assertEquals( String.valueOf( counter ), localDBStoredQueue.removeFirst() );
                counter++;
            }

            Assertions.assertTrue( localDBStoredQueue.isEmpty() );
        }
    }

    @Test
    public void testSize()
    {
        Assertions.assertEquals( 0, localDBStoredQueue.size() );
        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( "Something" );
            Assertions.assertEquals( i + 1, localDBStoredQueue.size() );
        }

        for ( int i = MAX_PROBLEM_SIZE; i > 0; i-- )
        {
            Assertions.assertEquals( i, localDBStoredQueue.size() );
            localDBStoredQueue.removeLast();
        }

        Assertions.assertEquals( 0, localDBStoredQueue.size() );
    }

    @Test
    public void testAddNull()
    {
        try
        {
            localDBStoredQueue.addFirst( null );
            Assertions.fail( "Should have thrown a NullPointerException" );
        }
        catch ( final NullPointerException npe )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assertions.fail( "Wrong exception catched." + e );
        }

        try
        {
            localDBStoredQueue.addLast( null );
            Assertions.fail( "Should have thrown a NullPointerException" );
        }
        catch ( final NullPointerException npe )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assertions.fail( "Wrong exception cached." + e );
        }
    }

    @Test
    public void testRemoveFirst()
    {
        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( String.valueOf( i ) );
            Assertions.assertEquals( String.valueOf( i ), localDBStoredQueue.removeFirst() );
        }

        localDBStoredQueue.clear();

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addLast( String.valueOf( i ) );
            Assertions.assertEquals( String.valueOf( i ), localDBStoredQueue.removeFirst() );
        }

        localDBStoredQueue.clear();

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addLast( String.valueOf( i ) );
        }

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            Assertions.assertEquals( String.valueOf( i ), localDBStoredQueue.removeFirst() );
        }

    }

    @Test
    public void testRemoveEmpty()
    {
        try
        {
            Assertions.assertTrue( localDBStoredQueue.isEmpty() );
            localDBStoredQueue.removeFirst();
            Assertions.fail( "Expected a NoSuchElementException" );
        }
        catch ( final NoSuchElementException nsee )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assertions.fail( "Unexpected exception : " + e );
        }

        try
        {
            Assertions.assertTrue( localDBStoredQueue.isEmpty() );
            localDBStoredQueue.removeLast();
            Assertions.fail( "Expected a NoSuchElementException" );
        }
        catch ( final NoSuchElementException nsee )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assertions.fail( "Unexpected exception : " + e );
        }

        try
        {
            Assertions.assertTrue( localDBStoredQueue.isEmpty() );

            for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
            {
                localDBStoredQueue.addLast( String.valueOf( i ) );
            }
            for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
            {
                localDBStoredQueue.removeLast();
            }
            localDBStoredQueue.removeLast();
            Assertions.fail( "Expected a NoSuchElementException" );
        }
        catch ( final NoSuchElementException nsee )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assertions.fail( "Unexpected exception : " + e );
        }
    }

    @Test
    public void testIteratorRemoveNotSupported()
    {
        final Iterator<String> anIterator = localDBStoredQueue.iterator();
        try
        {
            anIterator.remove();
            Assertions.fail( "Should have thrown an UnsupportedOperationException" );
        }
        catch ( final UnsupportedOperationException uoe )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assertions.fail( "Unexpected exception : " + e );
        }
    }

    @Test
    public void testMultipleIterator()
    {
        for ( int i = 0; i < MAX_PROBLEM_SIZE / 1000; i++ )
        {

            localDBStoredQueue.clear();
            for ( int j = 0; j < i; j++ )
            {
                localDBStoredQueue.addLast( String.valueOf( j ) );
            }

            @SuppressWarnings( "rawtypes" )
            final Iterator[] someIterators = {
                    localDBStoredQueue.iterator(),
                    localDBStoredQueue.iterator(),
                    localDBStoredQueue.iterator(),
                    localDBStoredQueue.iterator(),
                    localDBStoredQueue.iterator(),
                    localDBStoredQueue.iterator(),
            };

            @SuppressWarnings( "unchecked" )
            final Iterator<String>[] manyStringIterators =
                    ( Iterator<String>[] ) someIterators;

            for ( int iterID = 0; iterID < manyStringIterators.length; iterID++ )
            {
                int index = 0;
                while ( manyStringIterators[iterID].hasNext() )
                {
                    Assertions.assertEquals(
                            String.valueOf( index ),
                            manyStringIterators[iterID].next(),
                            "Iterator #" + iterID + " failed:\n" );
                    index++;
                }
            }

        }
    }

    @Test
    public void testQueueBehavior()
    {
        final String[] aBunchOfString = {
                "One",
                "Two",
                "Three",
                "Four",
        };

        for ( final String loopString : aBunchOfString )
        {
            localDBStoredQueue.addFirst( loopString );
        }

        for ( final String loopString : aBunchOfString )
        {
            Assertions.assertEquals( loopString, localDBStoredQueue.removeLast() );
        }
    }

    @Test
    public void testStackBehavior()
    {

        final String[] aBunchOfString = {
                "One",
                "Two",
                "Three",
                "Four",
        };

        for ( final String loopString : aBunchOfString )
        {
            localDBStoredQueue.addFirst( loopString );
        }

        for ( int i = aBunchOfString.length - 1; i >= 0; i-- )
        {
            Assertions.assertEquals( aBunchOfString[i], localDBStoredQueue.removeFirst() );
        }
    }

    private static void addValues( final LocalDBStoredQueue localDBStoredQueue, final int count )
    {
        final List<String> addValues = new ArrayList<>();
        for ( int i = 0; i < count; i++ )
        {
            addValues.add( String.valueOf( i ) );
        }
        localDBStoredQueue.addAll( addValues );
    }
}
