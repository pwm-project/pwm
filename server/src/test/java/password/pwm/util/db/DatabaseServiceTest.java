/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

package password.pwm.util.db;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.PasswordValue;
import password.pwm.config.value.StringValue;
import password.pwm.util.PasswordData;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.localdb.TestHelper;

import java.io.File;
import java.util.Map;

public class DatabaseServiceTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final DatabaseTable TEST_TABLE = DatabaseTable.PWM_META;
    private static final int TEST_COUNTS = 10_000;

    static PwmApplication makeDbApp( final TemporaryFolder temporaryFolder )
            throws Exception
    {
        final File dbFilePath = temporaryFolder.newFolder();
        final String jdbcUrl = "jdbc:h2:mem:pwmtest;DB_CLOSE_DELAY=-1;MODE=MySQL;NON_KEYWORDS=id,value";

        final StoredConfigurationModifier config = StoredConfigurationFactory.newModifiableConfig();
        config.writeSetting( PwmSetting.DATABASE_CLASS, null,
                new StringValue( "org.h2.Driver" ), null );
        config.writeSetting( PwmSetting.DATABASE_URL, null,
                new StringValue( jdbcUrl ), null );
        config.writeSetting( PwmSetting.DATABASE_USERNAME, null,
                new StringValue( "username" ), null );
        config.writeSetting( PwmSetting.DATABASE_PASSWORD, null,
                new PasswordValue( PasswordData.forStringValue( "username" ) ), null );

        return TestHelper.makeTestPwmApplication(
                dbFilePath,
                new Configuration( config.newStoredConfiguration() ) );
    }

    static DatabaseAccessor makeAccessor( final TemporaryFolder temporaryFolder )
            throws Exception
    {
        final PwmApplication pwmApplication = makeDbApp( temporaryFolder );
        final DatabaseService databaseService = pwmApplication.getDatabaseService();

        ( ( DatabaseAccessorImpl ) databaseService.getAccessor() ).clearTable( TEST_TABLE );

        return databaseService.getAccessor();
    }


    @Test
    public void testGetPut() throws Exception
    {
        final DatabaseAccessor accessor = makeAccessor( temporaryFolder );
        accessor.put( TEST_TABLE, "key1", "value1" );
        final String returnValue = accessor.get( TEST_TABLE, "key1" );
        Assert.assertEquals( "value1", returnValue );
    }

    @Test
    public void testSize() throws Exception
    {
        final DatabaseAccessor accessor = makeAccessor( temporaryFolder );
        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            accessor.put( TEST_TABLE, i + "key", i + "value" );
            Assert.assertEquals( i + 1, accessor.size( TEST_TABLE ) );
        }
    }

    @Test
    public void testIterator() throws Exception
    {
        final DatabaseAccessor accessor = makeAccessor( temporaryFolder );
        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            final String value = i + "value";
            accessor.put( TEST_TABLE, key, value );
        }

        try ( ClosableIterator<Map.Entry<String, String>> iterator = accessor.iterator( TEST_TABLE ) )
        {
            int loopCounter = 0;
            while ( iterator.hasNext() )
            {
                final String key = loopCounter + "key";
                final String value = loopCounter + "value";
                final Map.Entry<String, String> entry = iterator.next();
                Assert.assertEquals( entry.getKey(), key );
                Assert.assertEquals( entry.getValue(), value );
                Assert.assertThrows( UnsupportedOperationException.class, iterator::remove );
                loopCounter++;
            }
            Assert.assertEquals( TEST_COUNTS, loopCounter );
        }
    }

    @Test
    public void testGets() throws Exception
    {
        final DatabaseAccessor accessor = makeAccessor( temporaryFolder );
        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            final String value = i + "value";
            accessor.put( TEST_TABLE, key, value );
        }

        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            final String value = i + "value";
            Assert.assertEquals( value, accessor.get( TEST_TABLE, key ) );
        }

        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            Assert.assertNotEquals( "badvalue", accessor.get( TEST_TABLE, key ) );
        }

        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            Assert.assertNull( accessor.get( TEST_TABLE, key + "badkey" ) );
        }
    }

    @Test
    public void testContains() throws Exception
    {
        final DatabaseAccessor accessor = makeAccessor( temporaryFolder );
        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            final String value = i + "value";
            accessor.put( TEST_TABLE, key, value );
        }

        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            Assert.assertTrue( accessor.contains( TEST_TABLE, key ) );
        }

        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            Assert.assertFalse( accessor.contains( TEST_TABLE, key + "false" ) );
        }
    }

    @Test
    public void testRemoves() throws Exception
    {
        final DatabaseAccessor accessor = makeAccessor( temporaryFolder );
        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            final String value = i + "value";
            accessor.put( TEST_TABLE, key, value );
        }

        for ( int i = 0; i < TEST_COUNTS; i++ )
        {
            final String key = i + "key";
            Assert.assertTrue( accessor.contains( TEST_TABLE, key ) );
            Assert.assertEquals( TEST_COUNTS - i, accessor.size( TEST_TABLE ) );
            accessor.remove( TEST_TABLE, key );
            Assert.assertFalse( accessor.contains( TEST_TABLE, key ) );
            Assert.assertEquals( ( TEST_COUNTS - i ) - 1, accessor.size( TEST_TABLE ) );
        }
    }

    @Test
    public void testPutIfAbsent() throws Exception
    {
        final DatabaseAccessor accessor = makeAccessor( temporaryFolder );

        Assert.assertEquals( 0, accessor.size( TEST_TABLE ) );
        Assert.assertTrue( accessor.putIfAbsent( TEST_TABLE, "key1", "value1" ) );
        Assert.assertFalse( accessor.putIfAbsent( TEST_TABLE, "key1", "value2" ) );
        Assert.assertEquals( "value1", accessor.get( TEST_TABLE, "key1" ) );
        Assert.assertEquals( 1, accessor.size( TEST_TABLE ) );
    }
}
