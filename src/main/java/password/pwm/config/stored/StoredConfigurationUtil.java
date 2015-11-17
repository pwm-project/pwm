/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package password.pwm.config.stored;

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;

import java.io.Serializable;
import java.util.*;

public abstract class StoredConfigurationUtil {
    public static List<String> profilesForSetting
            (final PwmSetting pwmSetting,
             final StoredConfiguration storedConfiguration
            )
    {
        if (!pwmSetting.getCategory().hasProfiles() && pwmSetting.getSyntax() != PwmSettingSyntax.PROFILE) {
            throw new IllegalArgumentException("cannot build profile list for non-profile setting " + pwmSetting.toString());
        }

        final PwmSetting profileSetting;
        if (pwmSetting.getSyntax() == PwmSettingSyntax.PROFILE) {
            profileSetting = pwmSetting;
        } else {
            profileSetting = pwmSetting.getCategory().getProfileSetting();
        }

        final Object nativeObject = storedConfiguration.readSetting(profileSetting).toNativeObject();
        final List<String> settingValues = (List<String>)nativeObject;
        final LinkedList<String> profiles = new LinkedList<>();
        profiles.addAll(settingValues);
        for (Iterator<String> iterator = profiles.iterator(); iterator.hasNext();) {
            final String profile = iterator.next();
            if (profile == null || profile.length() < 1) {
                iterator.remove();
            }
        }
        return Collections.unmodifiableList(profiles);
    }

    public static List<StoredConfigReference> modifiedSettings(final StoredConfiguration storedConfiguration) {
        final List<StoredConfigReference> returnObj = new ArrayList<>();

        for (final PwmSetting setting : PwmSetting.values()) {
            if (setting.getSyntax() != PwmSettingSyntax.PROFILE && !setting.getCategory().hasProfiles()) {
                if (!storedConfiguration.isDefaultValue(setting, null)) {
                    final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                            StoredConfigReference.RecordType.SETTING,
                            setting.getKey(),
                            null
                    );
                    returnObj.add(storedConfigReference);
                }
            }
        }

        for (final PwmSettingCategory category : PwmSettingCategory.values()) {
            if (category.hasProfiles()) {
                for (final String profileID : profilesForSetting(category.getProfileSetting(), storedConfiguration)) {
                    for (final PwmSetting setting : category.getSettings()) {
                        if (!storedConfiguration.isDefaultValue(setting, profileID)) {
                            final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                                    StoredConfigReference.RecordType.SETTING,
                                    setting.getKey(),
                                    profileID
                            );
                            returnObj.add(storedConfigReference);
                        }
                    }
                }
            }
        }

        return returnObj;
    }

    public static Serializable toJsonDebugObject(final StoredConfiguration storedConfiguration)
    {
        final TreeMap<String,Object> outputObject = new TreeMap<>();

        for (final StoredConfigReference storedConfigReference : modifiedSettings(storedConfiguration)) {
            final PwmSetting setting = PwmSetting.forKey(storedConfigReference.getRecordID());
            if (setting != null) {
                final StoredValue value = storedConfiguration.readSetting(setting, storedConfigReference.getProfileID());
                outputObject.put(setting.getKey(), value.toDebugJsonObject(null));
            }
        }
        return outputObject;
    }
}
