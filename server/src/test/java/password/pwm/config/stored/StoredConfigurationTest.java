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

package password.pwm.config.stored;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import password.pwm.PwmApplication;
import password.pwm.config.AppConfig;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.localdb.TestHelper;

import java.io.InputStream;
import java.nio.file.Path;

public class StoredConfigurationTest
{
    @TempDir
    public Path temporaryFolder;

    private static AppConfig appConfig;

    @BeforeAll
    public static void setUp() throws Exception
    {
        try ( InputStream xmlFile = ConfigurationCleanerTest.class.getResourceAsStream( "ConfigurationCleanerTest.xml" ) )
        {
            final StoredConfiguration storedConfiguration = StoredConfigurationFactory.input( xmlFile );
            appConfig = AppConfig.forStoredConfig( storedConfiguration );
        }
    }

    @Test
    public void configurationHashTest()
            throws Exception
    {
        final Path testFolder = FileSystemUtility.createDirectory( temporaryFolder, "test-configurationHashTest" );
        final PwmApplication pwmDomain = TestHelper.makeTestPwmApplication( testFolder, appConfig );
        final String configHash = StoredConfigurationUtil.valueHash( appConfig.getStoredConfiguration() );

        Assertions.assertTrue( !configHash.isEmpty() );
    }
}
