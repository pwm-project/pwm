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

package password.pwm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

class EnvironmentPropertyTest
{
    @TempDir
    private Path temporaryPath;

    @Test
    public void testEnvironmentSystemProperties()
    {
        System.setProperty( EnvironmentProperty.applicationPath.conicalJavaOptionSystemName( null ), "/tmp/example" );
        final Map<EnvironmentProperty, String> map = EnvironmentProperty.readApplicationParams( null, null );
        Assertions.assertEquals( "/tmp/example", map.get( EnvironmentProperty.applicationPath ) );
    }

    @Test
    public void testAppPropertyFileProperties()
            throws Exception
    {
        {
            final Path propFilePath = temporaryPath.resolve( PwmConstants.DEFAULT_ENVIRONMENT_PROPERTIES_FILENAME );
            final Properties outputProps = new Properties();
            outputProps.setProperty( EnvironmentProperty.InstanceID.name(), "TEST-VALUE-22" );
            try ( OutputStream outputStream = Files.newOutputStream( propFilePath ) )
            {
                outputProps.store( outputStream, "test" );
            }
        }

        final Map<EnvironmentProperty, String> props = EnvironmentProperty.readApplicationParams( temporaryPath, null );

        Assertions.assertEquals( "TEST-VALUE-22", props.get( EnvironmentProperty.InstanceID ) );
    }
}
