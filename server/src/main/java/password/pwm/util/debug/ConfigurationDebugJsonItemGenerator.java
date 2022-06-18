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
import password.pwm.config.value.StoredValue;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.util.TreeMap;

class ConfigurationDebugJsonItemGenerator implements AppItemGenerator
{
    @Override
    public String getFilename()
    {
        return "configuration-debug.json";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream )
            throws IOException
    {
        final StoredConfiguration storedConfiguration = debugItemInput.getObfuscatedAppConfig().getStoredConfiguration();
        final TreeMap<String, Object> outputObject = new TreeMap<>();

        CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                .filter( k -> k.getRecordType() == StoredConfigKey.RecordType.SETTING )
                .forEach( k ->
                {
                    final String key = k.getLabel( PwmConstants.DEFAULT_LOCALE );
                    final StoredValue value = storedConfiguration.readStoredValue( k ).orElseThrow();
                    outputObject.put( key, value );
                } );


        final String jsonOutput = JsonFactory.get().serializeMap( outputObject, JsonProvider.Flag.PrettyPrint );
        outputStream.write( jsonOutput.getBytes( PwmConstants.DEFAULT_CHARSET ) );
    }
}
