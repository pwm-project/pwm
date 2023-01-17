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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import password.pwm.PwmApplication;
import password.pwm.util.java.FileSystemUtility;

import java.nio.file.Path;
import java.util.function.Function;
import java.util.stream.IntStream;

public class LocalDBStoredQueueRepairTest
{
    private static final int REOPEN_ITER_COUNT = 99;

    // can't use TEMP here since it is cleared every localdb open
    private static final LocalDB.DB DB_NAME = LocalDB.DB.PWM_META;

    @TempDir
    public Path temporaryFolder;

    @Test
    public void incrementReOpenTest()
            throws Exception
    {
        final TestContext testContext = new TestContext( temporaryFolder.resolve( "incrementReOpenTest" ) );
        for ( int i = 0; i < REOPEN_ITER_COUNT; i++ )
        {
            //System.out.println( "iter: " + testContext.execute( LocalDBStoredQueue::size ) );
            final int loopInt = i;
            Assertions.assertEquals( i, testContext.executeQueueFunction( LocalDBStoredQueue::size ) );
            testContext.executeQueueFunction( q -> q.add( String.valueOf( loopInt ) ) );
        }
    }

    @Test
    public void decrementReOpenTest()
            throws Exception
    {
        final TestContext testContext = new TestContext( temporaryFolder.resolve( "decrementReOpenTest" ) );
        testContext.executeQueueFunction( q -> q.addAll( IntStream.range( 0, REOPEN_ITER_COUNT ).mapToObj( String::valueOf ).toList() ) );
        for ( int i = ( REOPEN_ITER_COUNT - 1 ); i >= 0; i-- )
        {
            testContext.executeQueueFunction( LocalDBStoredQueue::removeLast );
            Assertions.assertEquals( i, testContext.executeQueueFunction( LocalDBStoredQueue::size ) );
        }
    }

    @Test
    public void removeTailKeyReOpenTest()
            throws Exception
    {
        final TestContext testContext = new TestContext( temporaryFolder.resolve( "removeTailKeyReOpenTest" ) );
        testContext.executeQueueFunction( q -> q.addAll( IntStream.range( 0, REOPEN_ITER_COUNT ).mapToObj( String::valueOf ).toList() ) );
        Assertions.assertEquals( REOPEN_ITER_COUNT, testContext.executeQueueFunction( LocalDBStoredQueue::size ) );

        // delete the tail KEY.
        testContext.executeLocalDBFunction( ldb ->
        {
            try
            {
                return ldb.remove( DB_NAME, LocalDBStoredQueue.Position.zero().key() );
            }
            catch ( final LocalDBException e )
            {
                throw new RuntimeException( e );
            }
        } );

        Assertions.assertEquals( REOPEN_ITER_COUNT - 1, testContext.executeQueueFunction( LocalDBStoredQueue::size ) );
        Assertions.assertEquals( String.valueOf( REOPEN_ITER_COUNT - 1 ), testContext.executeQueueFunction( LocalDBStoredQueue::peekFirst ) );
        Assertions.assertEquals( String.valueOf( 1 ), testContext.executeQueueFunction( LocalDBStoredQueue::peekLast ) );

    }

    @Test
    public void removeHeadKeyReOpenTest()
            throws Exception
    {
        final TestContext testContext = new TestContext( temporaryFolder.resolve( "removeHeadKeyReOpenTest" ) );
        testContext.executeQueueFunction( q -> q.addAll( IntStream.range( 0, REOPEN_ITER_COUNT ).mapToObj( String::valueOf ).toList() ) );
        Assertions.assertEquals( REOPEN_ITER_COUNT, testContext.executeQueueFunction( LocalDBStoredQueue::size ) );

        // delete the tail KEY.
        testContext.executeLocalDBFunction( ldb ->
        {
            try
            {
                return ldb.remove( DB_NAME, new LocalDBStoredQueue.Position( REOPEN_ITER_COUNT - 1 ).key() );
            }
            catch ( final LocalDBException e )
            {
                throw new RuntimeException( e );
            }
        } );

        Assertions.assertEquals( REOPEN_ITER_COUNT - 1, testContext.executeQueueFunction( LocalDBStoredQueue::size ) );
        Assertions.assertEquals( String.valueOf( REOPEN_ITER_COUNT - 2 ), testContext.executeQueueFunction( LocalDBStoredQueue::peekFirst ) );
        Assertions.assertEquals( String.valueOf( 0 ), testContext.executeQueueFunction( LocalDBStoredQueue::peekLast ) );
    }

    private static class TestContext
    {
        private final Path localDbTestFolder;
        private final PwmApplication pwmApplication;

        TestContext( final Path temporaryFolder )
                throws Exception
        {
            localDbTestFolder = FileSystemUtility.createDirectory( temporaryFolder, "test-localdb-reopen-test" );
            pwmApplication = TestHelper.makeTestPwmApplication( localDbTestFolder );
        }

        public <T> T executeQueueFunction( final Function<LocalDBStoredQueue, T> function )
                throws Exception
        {
            final LocalDB localDB = LocalDBFactory.getInstance( localDbTestFolder, false, pwmApplication.getPwmEnvironment(), pwmApplication.getConfig() );
            final LocalDBStoredQueue storedQueue = LocalDBStoredQueue.createLocalDBStoredQueue( localDB, DB_NAME, true );
            try
            {
                return function.apply( storedQueue );
            }
            finally
            {
                localDB.close();
            }
        }

        public <T> T executeLocalDBFunction( final Function<LocalDB, T> function )
                throws Exception
        {
            final LocalDB localDB = LocalDBFactory.getInstance( localDbTestFolder, false, pwmApplication.getPwmEnvironment(), pwmApplication.getConfig() );
            try
            {
                return function.apply( localDB );
            }
            finally
            {
                localDB.close();
            }
        }
    }
}

