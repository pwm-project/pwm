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
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;

public abstract class StoredConfigurationUtil
{
    public static List<String> profilesForSetting
            ( final PwmSetting pwmSetting,
              final StoredConfiguration storedConfiguration
            )
    {
        if ( !pwmSetting.getCategory().hasProfiles() && pwmSetting.getSyntax() != PwmSettingSyntax.PROFILE )
        {
            throw new IllegalArgumentException( "cannot build profile list for non-profile setting " + pwmSetting.toString() );
        }

        final PwmSetting profileSetting;
        if ( pwmSetting.getSyntax() == PwmSettingSyntax.PROFILE )
        {
            profileSetting = pwmSetting;
        }
        else
        {
            profileSetting = pwmSetting.getCategory().getProfileSetting();
        }

        return profilesForProfileSetting( profileSetting, storedConfiguration );
    }

    public static List<String> profilesForCategory(
            final PwmSettingCategory category,
            final StoredConfiguration storedConfiguration
    )
    {
        final PwmSetting profileSetting = category.getProfileSetting();

        return profilesForProfileSetting( profileSetting, storedConfiguration );
    }

    private static List<String> profilesForProfileSetting(
            final PwmSetting pwmSetting,
            final StoredConfiguration storedConfiguration
    )
    {
        if ( !pwmSetting.getCategory().hasProfiles() && pwmSetting.getSyntax() != PwmSettingSyntax.PROFILE )
        {
            throw new IllegalArgumentException( "cannot build profile list for non-profile setting " + pwmSetting.toString() );
        }

        final PwmSetting profileSetting;
        if ( pwmSetting.getSyntax() == PwmSettingSyntax.PROFILE )
        {
            profileSetting = pwmSetting;
        }
        else
        {
            profileSetting = pwmSetting.getCategory().getProfileSetting();
        }

        final Object nativeObject = storedConfiguration.readSetting( profileSetting ).toNativeObject();
        final List<String> settingValues = ( List<String> ) nativeObject;
        final LinkedList<String> profiles = new LinkedList<>( settingValues );
        profiles.removeIf( profile -> StringUtil.isEmpty( profile ) );
        return Collections.unmodifiableList( profiles );

    }


    public static List<StoredConfigReference> modifiedSettings( final StoredConfiguration storedConfiguration )
    {
        final List<StoredConfigReference> returnObj = new ArrayList<>();

        for ( final PwmSetting setting : PwmSetting.values() )
        {
            if ( setting.getSyntax() != PwmSettingSyntax.PROFILE && !setting.getCategory().hasProfiles() )
            {
                if ( !storedConfiguration.isDefaultValue( setting, null ) )
                {
                    final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                            StoredConfigReference.RecordType.SETTING,
                            setting.getKey(),
                            null
                    );
                    returnObj.add( storedConfigReference );
                }
            }
        }

        for ( final PwmSettingCategory category : PwmSettingCategory.values() )
        {
            if ( category.hasProfiles() )
            {
                for ( final String profileID : profilesForSetting( category.getProfileSetting(), storedConfiguration ) )
                {
                    for ( final PwmSetting setting : category.getSettings() )
                    {
                        if ( !storedConfiguration.isDefaultValue( setting, profileID ) )
                        {
                            final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                                    StoredConfigReference.RecordType.SETTING,
                                    setting.getKey(),
                                    profileID
                            );
                            returnObj.add( storedConfigReference );
                        }
                    }
                }
            }
        }

        return returnObj;
    }

    public static Serializable toJsonDebugObject( final StoredConfiguration storedConfiguration )
    {
        final TreeMap<String, Object> outputObject = new TreeMap<>();

        for ( final StoredConfigReference storedConfigReference : modifiedSettings( storedConfiguration ) )
        {
            final PwmSetting setting = PwmSetting.forKey( storedConfigReference.getRecordID() );
            if ( setting != null )
            {
                final StoredValue value = storedConfiguration.readSetting( setting, storedConfigReference.getProfileID() );
                outputObject.put( setting.getKey(), value.toDebugJsonObject( null ) );
            }
        }
        return outputObject;
    }

}
