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

package password.pwm.util.debug;

import password.pwm.PwmConstants;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;

class ConfigurationFileItemGenerator implements AppItemGenerator
{
    @Override
    public String getFilename()
    {
        return PwmConstants.DEFAULT_CONFIG_FILE_FILENAME;
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
    {
        final StoredConfiguration storedConfiguration = debugItemInput.getObfuscatedAppConfig().getStoredConfiguration();

        // temporary output stream required because .toXml closes stream.
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final StoredConfigurationFactory.OutputSettings outputSettings = StoredConfigurationFactory.OutputSettings.builder()
                .mode( StoredConfigurationFactory.OutputSettings.SecureOutputMode.STRIPPED )
                .build();
        StoredConfigurationFactory.output( storedConfiguration, byteArrayOutputStream, outputSettings );
        outputStream.write( byteArrayOutputStream.toByteArray() );
    }
}
