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
import password.pwm.util.java.JavaHelper;

import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Locale;

class ConfigurationDebugTextItemGenerator implements AppItemGenerator
{
    @Override
    public String getFilename()
    {
        return "configuration-debug.txt";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
    {
        final Locale locale = PwmConstants.DEFAULT_LOCALE;
        final StoredConfiguration storedConfiguration = debugItemInput.getObfuscatedAppConfig().getStoredConfiguration();

        final StringWriter writer = new StringWriter();
        writer.write( "Configuration Debug Output for "
                + PwmConstants.PWM_APP_NAME + " "
                + PwmConstants.SERVLET_VERSION + "\n" );
        writer.write( "Timestamp: " + JavaHelper.toIsoDate( storedConfiguration.modifyTime() ) + "\n" );
        writer.write( "This file is " + PwmConstants.DEFAULT_CHARSET.displayName() + " encoded\n" );
        writer.write( '\n' );

        CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                .filter( k -> k.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                .forEach( storedConfigKey ->
                {
                    final String key = storedConfigKey.toPwmSetting().toMenuLocationDebug( storedConfigKey.getProfileID(), locale );
                    final String value = storedConfiguration.readStoredValue( storedConfigKey ).orElseThrow().toDebugString( locale );
                    writer.write( ">> Setting > " + key );
                    writer.write( '\n' );
                    writer.write( value );
                    writer.write( '\n' );
                    writer.write( '\n' );
                } );

        outputStream.write( writer.toString().getBytes( PwmConstants.DEFAULT_CHARSET ) );
    }
}
