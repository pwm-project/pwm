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

package password.pwm.util.debug;

import password.pwm.PwmConstants;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

class ConfigurationDebugTextItemGenerator implements AppItemGenerator
{
    @Override
    public String getFilename()
    {
        return "configuration-debug.txt";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream )
            throws IOException
    {
        final Locale locale = PwmConstants.DEFAULT_LOCALE;
        final StoredConfiguration storedConfiguration = debugItemInput.getObfuscatedAppConfig().getStoredConfiguration();

        final String headerString = "Configuration Debug Output for "
                + PwmConstants.PWM_APP_NAME + " "
                + PwmConstants.SERVLET_VERSION + "\n"
                +  "Timestamp: " + StringUtil.toIsoDate( storedConfiguration.modifyTime() ) + "\n"
                +  "This file is " + PwmConstants.DEFAULT_CHARSET.displayName() + " encoded\n"
                + '\n';
        DebugItemGenerator.writeString( outputStream, headerString );

        CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                .filter( k -> k.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                .map( storedConfigKey -> settingDebugOutput( locale, storedConfiguration, storedConfigKey ) )
                .forEach( line -> DebugItemGenerator.writeString( outputStream, line ) );

    }

    private static String settingDebugOutput(
            final Locale locale,
            final StoredConfiguration storedConfiguration,
            final StoredConfigKey storedConfigKey )
    {
        final String key = storedConfigKey.toPwmSetting().toMenuLocationDebug( storedConfigKey.getProfileID().orElse( null ), locale );
        final String value = storedConfiguration.readStoredValue( storedConfigKey ).orElseThrow().toDebugString( locale );
        return  ">> Setting > " + key
                + '\n'
                + value
                + '\n'
                + '\n';
    }
}
