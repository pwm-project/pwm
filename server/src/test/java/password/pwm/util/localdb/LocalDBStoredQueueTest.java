/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.PwmApplication;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

public class LocalDBStoredQueueTest
{
    private static final int MAX_PROBLEM_SIZE = 100;

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private LocalDBStoredQueue localDBStoredQueue;

    @Before
    public void setUp() throws Exception
    {
        final File localDbTestFolder = testFolder.newFolder( "test-stored-queue-test" );
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( localDbTestFolder );
        final LocalDB localDB = LocalDBFactory.getInstance( localDbTestFolder, false, pwmApplication.getPwmEnvironment(), pwmApplication.getConfig() );
        localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue( localDB, LocalDB.DB.TEMP, true );
    }

    @Test
    public void testStoredQueueSimple() throws LocalDBException
    {
        Assert.assertEquals( 0, localDBStoredQueue.size() );

        localDBStoredQueue.add( "one" );
        Assert.assertEquals( 1, localDBStoredQueue.size() );

        localDBStoredQueue.add( "two" );
        Assert.assertEquals( 2, localDBStoredQueue.size() );

        localDBStoredQueue.add( "three" );
        Assert.assertEquals( 3, localDBStoredQueue.size() );

        localDBStoredQueue.removeFirst();
        Assert.assertEquals( 2, localDBStoredQueue.size() );

        {
            final List<String> values = localDBStoredQueue.stream().collect( Collectors.toList() );
            Assert.assertEquals( 2, values.size() );
        }
    }

    @Test
    public void testStoredQueueBulk() throws LocalDBException
    {
        addValues( localDBStoredQueue, MAX_PROBLEM_SIZE );

        Assert.assertEquals( MAX_PROBLEM_SIZE, localDBStoredQueue.size() );
        Assert.assertEquals( "99", localDBStoredQueue.getFirst() );
        Assert.assertEquals( "0", localDBStoredQueue.getLast() );

        for ( int i = 99; i > -1; i-- )
        {
            localDBStoredQueue.removeLast();
            Assert.assertEquals( i, localDBStoredQueue.size() );
        }
    }

    @Test
    public void testStoredQueueIterators()
    {
        addValues( localDBStoredQueue, MAX_PROBLEM_SIZE );

        {
            final Iterator<String> iter = localDBStoredQueue.descendingIterator();
            for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
            {
                Assert.assertEquals( String.valueOf( i ), iter.next() );
            }
        }

        {
            final Iterator<String> iter = localDBStoredQueue.iterator();
            for ( int i = ( MAX_PROBLEM_SIZE - 1 ); i > -1; i-- )
            {
                Assert.assertEquals( String.valueOf( i ), iter.next() );
            }
        }
    }

    @Test
    public void testRemoveLast()
    {
        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( String.valueOf( i ) );
            Assert.assertEquals( String.valueOf( i ), localDBStoredQueue.removeLast() );
        }

        localDBStoredQueue.clear();

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addLast( String.valueOf( i ) );
            Assert.assertEquals( String.valueOf( i ), localDBStoredQueue.removeLast() );
        }

        localDBStoredQueue.clear();

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( String.valueOf( i ) );
        }

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            Assert.assertEquals( String.valueOf( i ), localDBStoredQueue.removeLast() );
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
            Assert.assertEquals( aBunchOfString[i], localDBStoredQueue.removeFirst() );
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
            Assert.assertEquals( aBunchOfString[i], localDBStoredQueue.removeLast() );
        }
    }


    @Test
    public void testIsEmptyAfterAddRemoveFirst()
    {
        localDBStoredQueue.addFirst( "Something" );
        boolean empty = localDBStoredQueue.isEmpty();
        Assert.assertFalse( empty );
        localDBStoredQueue.removeFirst();

        empty = localDBStoredQueue.isEmpty();
        Assert.assertTrue( "Should be empty after adding then removing",
                empty );

    }

    @Test
    public void testIsEmptyAfterAddRemoveLast()
    {
        localDBStoredQueue.addLast( "Something" );
        Assert.assertFalse( localDBStoredQueue.isEmpty() );
        localDBStoredQueue.removeLast();
        Assert.assertTrue( "Should be empty after adding then removing",
                localDBStoredQueue.isEmpty() );

    }

    @Test
    public void testIsEmptyAfterAddFirstRemoveLast()
    {
        localDBStoredQueue.addFirst( "Something" );
        Assert.assertFalse( localDBStoredQueue.isEmpty() );
        localDBStoredQueue.removeLast();
        Assert.assertTrue( "Should be empty after adding then removing",
                localDBStoredQueue.isEmpty() );
    }

    @Test
    public void testIsEmptyAfterAddLastRemoveFirst()
    {
        localDBStoredQueue.addLast( "Something" );
        Assert.assertFalse( localDBStoredQueue.isEmpty() );
        localDBStoredQueue.removeFirst();
        Assert.assertTrue( "Should be empty after adding then removing",
                localDBStoredQueue.isEmpty() );
    }

    @Test
    public void testIsEmptyAfterMultipleAddRemove()
    {
        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( "Something" );
            Assert.assertFalse( "Should not be empty after " + i + " item added",
                    localDBStoredQueue.isEmpty() );
        }

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            Assert.assertFalse( "Should not be empty after " + i + " item removed",
                    localDBStoredQueue.isEmpty() );
            localDBStoredQueue.removeLast();
        }

        Assert.assertTrue( "Should be empty after adding and removing "
                        + MAX_PROBLEM_SIZE + " elements.",
                localDBStoredQueue.isEmpty() );
    }

    @Test
    public void testMultipleFillAndEmpty()
    {
        for ( int tries = 0; tries < 50; tries++ )
        {
            for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
            {
                localDBStoredQueue.addFirst( String.valueOf( i ) );
            }

            Assert.assertFalse( localDBStoredQueue.isEmpty() );
            int counter = 0;
            while ( !localDBStoredQueue.isEmpty() )
            {
                Assert.assertEquals( String.valueOf( counter ), localDBStoredQueue.removeLast() );
                counter++;
            }

            Assert.assertTrue( localDBStoredQueue.isEmpty() );

            for ( int j = 0; j < MAX_PROBLEM_SIZE; j++ )
            {
                localDBStoredQueue.addLast( String.valueOf( j ) );
            }

            Assert.assertFalse( localDBStoredQueue.isEmpty() );

            counter = 0;
            while ( !localDBStoredQueue.isEmpty() )
            {
                Assert.assertEquals( String.valueOf( counter ), localDBStoredQueue.removeFirst() );
                counter++;
            }

            Assert.assertTrue( localDBStoredQueue.isEmpty() );
        }
    }

    @Test
    public void testSize()
    {
        Assert.assertEquals( 0, localDBStoredQueue.size() );
        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( "Something" );
            Assert.assertEquals( i + 1, localDBStoredQueue.size() );
        }

        for ( int i = MAX_PROBLEM_SIZE; i > 0; i-- )
        {
            Assert.assertEquals( i, localDBStoredQueue.size() );
            localDBStoredQueue.removeLast();
        }

        Assert.assertEquals( 0, localDBStoredQueue.size() );
    }

    @Test
    public void testAddNull()
    {
        try
        {
            localDBStoredQueue.addFirst( null );
            Assert.fail( "Should have thrown a NullPointerException" );
        }
        catch ( final NullPointerException npe )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assert.fail( "Wrong exception catched." + e );
        }

        try
        {
            localDBStoredQueue.addLast( null );
            Assert.fail( "Should have thrown a NullPointerException" );
        }
        catch ( final NullPointerException npe )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assert.fail( "Wrong exception catched." + e );
        }
    }

    @Test
    public void testRemoveFirst()
    {
        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addFirst( String.valueOf( i ) );
            Assert.assertEquals( String.valueOf( i ), localDBStoredQueue.removeFirst() );
        }

        localDBStoredQueue.clear();

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addLast( String.valueOf( i ) );
            Assert.assertEquals( String.valueOf( i ), localDBStoredQueue.removeFirst() );
        }

        localDBStoredQueue.clear();

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            localDBStoredQueue.addLast( String.valueOf( i ) );
        }

        for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
        {
            Assert.assertEquals( String.valueOf( i ), localDBStoredQueue.removeFirst() );
        }

    }

    @Test
    public void testRemoveEmpty()
    {
        try
        {
            Assert.assertTrue( localDBStoredQueue.isEmpty() );
            localDBStoredQueue.removeFirst();
            Assert.fail( "Expected a NoSuchElementException" );
        }
        catch ( final NoSuchElementException nsee )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assert.fail( "Unexpected exception : " + e );
        }

        try
        {
            Assert.assertTrue( localDBStoredQueue.isEmpty() );
            localDBStoredQueue.removeLast();
            Assert.fail( "Expected a NoSuchElementException" );
        }
        catch ( final NoSuchElementException nsee )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assert.fail( "Unexpected exception : " + e );
        }

        try
        {
            Assert.assertTrue( localDBStoredQueue.isEmpty() );

            for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
            {
                localDBStoredQueue.addLast( String.valueOf( i ) );
            }
            for ( int i = 0; i < MAX_PROBLEM_SIZE; i++ )
            {
                localDBStoredQueue.removeLast();
            }
            localDBStoredQueue.removeLast();
            Assert.fail( "Expected a NoSuchElementException" );
        }
        catch ( final NoSuchElementException nsee )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assert.fail( "Unexpected exception : " + e );
        }
    }

    @Test
    public void testIteratorRemoveNotSupported()
    {
        final Iterator<String> anIterator = localDBStoredQueue.iterator();
        try
        {
            anIterator.remove();
            Assert.fail( "Should have thrown an UnsupportedOperationException" );
        }
        catch ( final UnsupportedOperationException uoe )
        {
            // Continue
        }
        catch ( final Exception e )
        {
            Assert.fail( "Unexpected exception : " + e );
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
                    Assert.assertEquals( "Iterator #" + iterID + " failed:\n",
                            String.valueOf( index ),
                            manyStringIterators[iterID].next() );
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
            Assert.assertEquals( loopString, localDBStoredQueue.removeLast() );
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
            Assert.assertEquals( aBunchOfString[i], localDBStoredQueue.removeFirst() );
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
