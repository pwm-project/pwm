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

package password.pwm.config.stored;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.util.localdb.TestHelper;

import java.io.InputStream;

public class StoredConfigurationTest
{
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Configuration configuration;

    @BeforeClass
    public static void setUp() throws Exception
    {
        try ( InputStream xmlFile = ConfigurationCleanerTest.class.getResourceAsStream( "ConfigurationCleanerTest.xml" ) )
        {
            final StoredConfiguration storedConfiguration = StoredConfigurationFactory.input( xmlFile );
            configuration = new Configuration( storedConfiguration );
        }
    }

    @Test
    public void configurationHashTest()
            throws Exception
    {
        final PwmApplication pwmApplication = TestHelper.makeTestPwmApplication( temporaryFolder.newFolder(), configuration );
        final String configHash = configuration.configurationHash( pwmApplication.getSecureService() );
    }
}
