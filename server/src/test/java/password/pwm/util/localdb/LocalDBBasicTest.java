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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.PwmApplication;

import java.io.File;
import java.util.List;

public class LocalDBBasicTest
{
    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private LocalDB localDB;


    @Before
    public void setUp() throws Exception
    {
        final File localDbTestFolder = testFolder.newFolder( "test-stored-queue-test" );
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( localDbTestFolder );
        localDB = LocalDBFactory.getInstance( localDbTestFolder, false, pwmApplication.getPwmEnvironment(), pwmApplication.getConfig() );
    }

    @Test
    public void testBasicNarrative() throws LocalDBException
    {
        Assert.assertEquals( 0, localDB.size( LocalDB.DB.TEMP ) );

        localDB.put( LocalDB.DB.TEMP, "key1", "value1" );

        Assert.assertEquals( 1, localDB.size( LocalDB.DB.TEMP ) );
        Assert.assertEquals( "value1", localDB.get( LocalDB.DB.TEMP, "key1" ).orElseThrow() );
        Assert.assertTrue( localDB.contains( LocalDB.DB.TEMP, "key1" ) );

        localDB.remove( LocalDB.DB.TEMP, "key1" );
        Assert.assertEquals( 0, localDB.size( LocalDB.DB.TEMP ) );
        Assert.assertFalse( localDB.contains( LocalDB.DB.TEMP, "key1" ) );

        localDB.put( LocalDB.DB.TEMP, "key1", "value1" );
        localDB.put( LocalDB.DB.TEMP, "key2", "value2" );

        Assert.assertEquals( 2, localDB.size( LocalDB.DB.TEMP ) );
        Assert.assertEquals( "value1", localDB.get( LocalDB.DB.TEMP, "key1" ).orElseThrow() );
        Assert.assertEquals( "value2", localDB.get( LocalDB.DB.TEMP, "key2" ).orElseThrow() );
        Assert.assertTrue(  localDB.get( LocalDB.DB.TEMP, "key3" ).isEmpty() );
        Assert.assertFalse( localDB.contains( LocalDB.DB.TEMP, "key3" ) );

        localDB.removeAll( LocalDB.DB.TEMP, List.of( "key1", "key2" ) );
        Assert.assertEquals( 0, localDB.size( LocalDB.DB.TEMP ) );

    }
}
