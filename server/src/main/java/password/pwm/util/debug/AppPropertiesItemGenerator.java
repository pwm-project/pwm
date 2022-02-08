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

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.AppConfig;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;

import java.io.OutputStream;
import java.util.Properties;

class AppPropertiesItemGenerator implements AppItemGenerator
{
    @Override
    public String getFilename()
    {
        return "appProperties.properties";
    }

    @Override
    public void outputItem( final AppDebugItemInput debugItemInput, final OutputStream outputStream ) throws Exception
    {

        final AppConfig config = debugItemInput.getObfuscatedAppConfig();
        final Properties outputProps = JavaHelper.newSortedProperties();

        for ( final AppProperty appProperty : AppProperty.values() )
        {
            outputProps.put( appProperty.getKey(), config.readAppProperty( appProperty ) );
        }

        outputStream.write( JsonFactory.get().serializeMap( outputProps ).getBytes( PwmConstants.DEFAULT_CHARSET ) );
    }
}
