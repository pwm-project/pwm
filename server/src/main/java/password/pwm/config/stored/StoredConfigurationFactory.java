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

import lombok.Builder;
import lombok.Value;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StoredConfigurationFactory
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( StoredConfigurationFactory.class );

    private static final StoredConfigSerializer SERIALIZER = new StoredConfigXmlSerializer();

    public static StoredConfiguration newConfig() throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = new StoredConfigurationImpl(  );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );

        StoredConfigurationUtil.initNewRandomSecurityKey( modifier );
        modifier.writeConfigProperty(
                ConfigurationProperty.CONFIG_IS_EDITABLE, Boolean.toString( true ) );
        modifier.writeConfigProperty(
                ConfigurationProperty.CONFIG_EPOCH, String.valueOf( 0 ) );


        return modifier.newStoredConfiguration();
    }

    public static StoredConfigurationModifier newModifiableConfig() throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = newConfig();
        return StoredConfigurationModifier.newModifier( storedConfiguration );
    }

    public static StoredConfiguration input( final InputStream inputStream )
            throws PwmUnrecoverableException, IOException
    {

        final StoredConfiguration storedConfiguration = SERIALIZER.readInput( inputStream );
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier(  storedConfiguration );
        ConfigurationCleaner.postProcessStoredConfig( modifier );
        return modifier.newStoredConfiguration();
    }

    public static void output(
            final StoredConfiguration storedConfiguration,
            final OutputStream outputStream
    )
            throws PwmUnrecoverableException, IOException
    {
        output( storedConfiguration, outputStream, StoredConfigurationFactory.OutputSettings.builder().build() );
    }

    public static void output(
            final StoredConfiguration storedConfiguration,
            final OutputStream outputStream,
            final OutputSettings outputSettings
    )
            throws PwmUnrecoverableException, IOException
    {
        SERIALIZER.writeOutput( storedConfiguration, outputStream, outputSettings );
    }



    @Value
    @Builder
    public static class OutputSettings
    {
        @Builder.Default
        private SecureOutputMode mode = SecureOutputMode.NORMAL;

        public enum SecureOutputMode
        {
            NORMAL,
            STRIPPED,
        }
    }
}

