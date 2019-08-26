/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class ConfigChangeLogImpl implements Serializable, ConfigChangeLog
{
    private final Map<StoredConfigReference, StoredValue> changeLog = new LinkedHashMap<>();
    private final Map<StoredConfigReference, StoredValue> originalValue = new LinkedHashMap<>();
    private final transient StorageEngine storedConfiguration;

    public ConfigChangeLogImpl( final StorageEngine storageEngine )
    {
        this.storedConfiguration = storageEngine;
    }

    @Override
    public boolean isModified( )
    {
        return !changeLog.isEmpty();
    }

    @Override
    public String changeLogAsDebugString( final Locale locale, final boolean asHtml )
    {
        final Map<String, String> outputMap = new TreeMap<>();

        for ( final StoredConfigReference configReference : changeLog.keySet() )
        {
            switch ( configReference.getRecordType() )
            {
                case SETTING:
                {
                    final PwmSetting pwmSetting = PwmSetting.forKey( configReference.getRecordID() );
                    final StoredValue currentValue = storedConfiguration.read( configReference );
                    final String keyName = pwmSetting.toMenuLocationDebug( configReference.getProfileID(), locale );
                    final String debugValue = currentValue.toDebugString( locale );
                    outputMap.put( keyName, debugValue );
                }
                break;

                /*
                case LOCALE_BUNDLE: {
                    final String SEPARATOR = LocaleHelper.getLocalizedMessage(locale, Config.Display_SettingNavigationSeparator, null);
                    final String key = (String) configReference.recordID;
                    final String bundleName = key.split("!")[0];
                    final String keys = key.split("!")[1];
                    final Map<String,String> currentValue = readLocaleBundleMap(bundleName,keys);
                    final String debugValue = JsonUtil.serializeMap(currentValue, JsonUtil.Flag.PrettyPrint);
                    outputMap.put("LocaleBundle" + SEPARATOR + bundleName + " " + keys,debugValue);
                }
                break;
                */

                default:
                    //continue
                    break;
            }
        }
        final StringBuilder output = new StringBuilder();
        if ( outputMap.isEmpty() )
        {
            output.append( "No setting changes." );
        }
        else
        {
            for ( final Map.Entry<String, String> entry : outputMap.entrySet() )
            {
                final String keyName = entry.getKey();
                final String value = entry.getValue();
                if ( asHtml )
                {
                    output.append( "<div class=\"changeLogKey\">" );
                    output.append( keyName );
                    output.append( "</div><div class=\"changeLogValue\">" );
                    output.append( StringUtil.escapeHtml( value ) );
                    output.append( "</div>" );
                }
                else
                {
                    output.append( keyName );
                    output.append( "\n" );
                    output.append( " Value: " );
                    output.append( value );
                    output.append( "\n" );
                }
            }
        }
        return output.toString();
    }

    @Override
    public void updateChangeLog( final StoredConfigReference reference, final StoredValue newValue )
    {
        changeLog.put( reference, newValue );
        originalValue.put( reference, null );
    }

    @Override
    public void updateChangeLog( final StoredConfigReference reference, final StoredValue currentValue, final StoredValue newValue )
    {
        if ( originalValue.containsKey( reference ) )
        {
            if ( newValue.equals( originalValue.get( reference ) ) )
            {
                originalValue.remove( reference );
                changeLog.remove( reference );
            }
        }
        else
        {
            originalValue.put( reference, currentValue );
            changeLog.put( reference, newValue );
        }
    }

    @Override
    public Collection<StoredConfigReference> changedValues( )
    {
        return changeLog.keySet();
    }
}
