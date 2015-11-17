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
import password.pwm.config.StoredValue;
import password.pwm.i18n.Config;
import password.pwm.util.LocaleHelper;
import password.pwm.util.StringUtil;

import java.io.Serializable;
import java.util.*;

public class ConfigChangeLogImpl implements Serializable, ConfigChangeLog {
    private final Map<StoredConfigReference,StoredValue> changeLog = new LinkedHashMap<>();
    private final Map<StoredConfigReference,StoredValue> originalValue = new LinkedHashMap<>();
    private final StorageEngine storedConfiguration;

    public ConfigChangeLogImpl(StorageEngine storageEngine) {
        this.storedConfiguration = storageEngine;
    }

    @Override
    public boolean isModified() {
        return !changeLog.isEmpty();
    }

    @Override
    public String changeLogAsDebugString(final Locale locale, boolean asHtml) {
        final Map<String,String> outputMap = new TreeMap<>();
        final String SEPARATOR = LocaleHelper.getLocalizedMessage(locale, Config.Display_SettingNavigationSeparator, null);

        for (final StoredConfigReference configReference : changeLog.keySet()) {
            switch (configReference.getRecordType()) {
                case SETTING: {
                    final PwmSetting pwmSetting = PwmSetting.forKey(configReference.getRecordID());
                    final StoredValue currentValue = storedConfiguration.read(configReference);
                    final String keyName = pwmSetting.toMenuLocationDebug(configReference.getProfileID(), locale);
                    final String debugValue = currentValue.toDebugString(locale);
                    outputMap.put(keyName,debugValue);
                }
                break;

                /*
                case LOCALE_BUNDLE: {
                    final String key = (String) configReference.recordID;
                    final String bundleName = key.split("!")[0];
                    final String keys = key.split("!")[1];
                    final Map<String,String> currentValue = readLocaleBundleMap(bundleName,keys);
                    final String debugValue = JsonUtil.serializeMap(currentValue, JsonUtil.Flag.PrettyPrint);
                    outputMap.put("LocaleBundle" + SEPARATOR + bundleName + " " + keys,debugValue);
                }
                break;
                */
            }
        }
        final StringBuilder output = new StringBuilder();
        if (outputMap.isEmpty()) {
            output.append("No setting changes.");
        } else {
            for (final String keyName : outputMap.keySet()) {
                final String value = outputMap.get(keyName);
                if (asHtml) {
                    output.append("<div class=\"changeLogKey\">");
                    output.append(keyName);
                    output.append("</div><div class=\"changeLogValue\">");
                    output.append(StringUtil.escapeHtml(value));
                    output.append("</div>");
                } else {
                    output.append(keyName);
                    output.append("\n");
                    output.append(" Value: ");
                    output.append(value);
                    output.append("\n");
                }
            }
        }
        return output.toString();
    }

    @Override
    public void updateChangeLog(final StoredConfigReference reference, final StoredValue newValue) {
        changeLog.put(reference,newValue);
        originalValue.put(reference, null);
    }

    @Override
    public void updateChangeLog(final StoredConfigReference reference, final StoredValue currentValue, final StoredValue newValue) {
        if (originalValue.containsKey(reference)) {
            if (newValue.equals(originalValue.get(reference))) {
                originalValue.remove(reference);
                changeLog.remove(reference);
            }
        } else {
            originalValue.put(reference, currentValue);
            changeLog.put(reference, newValue);
        }
    }

    @Override
    public Collection<StoredConfigReference> changedValues() {
        return changeLog.keySet();
    }
}
