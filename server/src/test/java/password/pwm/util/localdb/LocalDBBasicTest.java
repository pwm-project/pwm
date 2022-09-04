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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import password.pwm.PwmApplication;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.secure.PwmRandom;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Execution( ExecutionMode.SAME_THREAD )
public class LocalDBBasicTest
{
    private static final LocalDB.DB TEST_DB = LocalDB.DB.TEMP;

    private static final int BULK_COUNT = PwmRandom.getInstance().nextInt( 1_000 ) + 10_000;

    @TempDir
    public Path temporaryFolder;

    private LocalDB localDB;

    @BeforeEach
    public void setUp() throws Exception
    {
        final File localDbTestFolder = FileSystemUtility.createDirectory( temporaryFolder, "test-stored-queue-test" );
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( localDbTestFolder );
        localDB = LocalDBFactory.getInstance( localDbTestFolder, false, pwmApplication.getPwmEnvironment(), pwmApplication.getConfig() );
    }

    @Test
    public void testBasicNarrative() throws LocalDBException
    {
        Assertions.assertEquals( 0, localDB.size( LocalDB.DB.TEMP ) );

        localDB.put( TEST_DB, "key1", "value1" );

        Assertions.assertEquals( 1, localDB.size( TEST_DB ) );
        Assertions.assertEquals( "value1", localDB.get( TEST_DB, "key1" ).orElseThrow() );
        Assertions.assertTrue( localDB.contains( TEST_DB, "key1" ) );

        localDB.remove( TEST_DB, "key1" );
        Assertions.assertEquals( 0, localDB.size( TEST_DB ) );
        Assertions.assertFalse( localDB.contains( TEST_DB, "key1" ) );

        localDB.put( TEST_DB, "key1", "value1" );
        localDB.put( TEST_DB, "key2", "value2" );

        Assertions.assertEquals( 2, localDB.size( TEST_DB ) );
        Assertions.assertEquals( "value1", localDB.get( TEST_DB, "key1" ).orElseThrow() );
        Assertions.assertEquals( "value2", localDB.get( TEST_DB, "key2" ).orElseThrow() );
        Assertions.assertTrue(  localDB.get( TEST_DB, "key3" ).isEmpty() );
        Assertions.assertFalse( localDB.contains( TEST_DB, "key3" ) );

        localDB.removeAll( TEST_DB, List.of( "key1", "key2" ) );
        Assertions.assertEquals( 0, localDB.size( TEST_DB ) );
    }

    @Test
    public void testPut() throws LocalDBException
    {
        Assertions.assertTrue( localDB.get( TEST_DB, "testKey1" ).isEmpty() );
        localDB.put( TEST_DB, "testKey1", "testValue1" );
        Assertions.assertEquals( "testValue1", localDB.get( TEST_DB, "testKey1" ).orElseThrow() );
    }

    @Test
    public void testBulk() throws LocalDBException
    {
        localDB.truncate( TEST_DB );
        Assertions.assertEquals( 0, localDB.size( TEST_DB ) );

        final Set<String> keys = new HashSet<>();
        final Set<String> values = new HashSet<>();

        for ( int i = 0; i < BULK_COUNT; i++ )
        {
            final String key = "key" + i;
            final String value = "value" + i;
            keys.add( key );
            values.add( value );

            localDB.put( TEST_DB, key, value );
        }

        Assertions.assertEquals( BULK_COUNT, keys.size() );
        Assertions.assertEquals( BULK_COUNT, values.size() );
        Assertions.assertEquals( BULK_COUNT, localDB.size( TEST_DB ) );

        try ( LocalDB.LocalDBIterator<Map.Entry<String, String>> iter = localDB.iterator( TEST_DB ) )
        {
            for ( int i = 0; i < BULK_COUNT; i++ )
            {
                final Map.Entry<String, String> nextEntry = iter.next();
                final String key = nextEntry.getKey();
                final String value = nextEntry.getValue();
                Assertions.assertTrue( keys.contains( key ) );
                Assertions.assertTrue( values.contains( value ) );
                keys.remove( key );
                values.remove( value );

                if ( i < BULK_COUNT - 1 )
                {
                    Assertions.assertTrue( iter.hasNext() );
                }
            }

            Assertions.assertTrue( keys.isEmpty() );
            Assertions.assertTrue( values.isEmpty() );

            Assertions.assertFalse( iter.hasNext() );
        }
    }
}
