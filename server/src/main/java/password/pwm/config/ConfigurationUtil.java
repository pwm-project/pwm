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

package password.pwm.config;

import password.pwm.config.option.DataStorageMethod;

import java.util.List;

/**
 * Static helper methods for reading {@link Configuration} values.
 */
public class ConfigurationUtil
{
    private ConfigurationUtil()
    {
    }

    public static List<DataStorageMethod> getCrReadPreference( final Configuration configuration )
    {
        final List<DataStorageMethod> readPreferences = configuration.getResponseStorageLocations( PwmSetting.FORGOTTEN_PASSWORD_READ_PREFERENCE );
        if ( readPreferences.size() == 1 && readPreferences.iterator().next() == DataStorageMethod.AUTO )
        {
            readPreferences.clear();
            if ( configuration.hasDbConfigured() )
            {
                readPreferences.add( DataStorageMethod.DB );
            }
            else
            {
                readPreferences.add( DataStorageMethod.LDAP );
            }
        }


        if ( configuration.readSettingAsBoolean( PwmSetting.EDIRECTORY_USE_NMAS_RESPONSES ) )
        {
            readPreferences.add( DataStorageMethod.NMAS );
        }

        return readPreferences;
    }

    public static List<DataStorageMethod> getCrWritePreference( final Configuration configuration )
    {
        final List<DataStorageMethod> writeMethods = configuration.getResponseStorageLocations( PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE );
        if ( writeMethods.size() == 1 && writeMethods.get( 0 ) == DataStorageMethod.AUTO )
        {
            writeMethods.clear();
            if ( configuration.hasDbConfigured() )
            {
                writeMethods.add( DataStorageMethod.DB );
            }
            else
            {
                writeMethods.add( DataStorageMethod.LDAP );
            }
        }
        if ( configuration.readSettingAsBoolean( PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES ) )
        {
            writeMethods.add( DataStorageMethod.NMAS );
        }
        return writeMethods;
    }
}
