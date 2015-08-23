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
 */

package password.pwm.config.profile;

import password.pwm.config.*;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.util.PasswordData;

import java.security.cert.X509Certificate;
import java.util.*;

public abstract class AbstractProfile implements Profile, SettingReader {

    final protected String identifier;
    final protected Map<PwmSetting,StoredValue> storedValueMap;

    protected AbstractProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        this.identifier = identifier;
        this.storedValueMap = storedValueMap;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    public List<UserPermission> readSettingAsUserPermission(final PwmSetting setting) {
        final StoredValue value = storedValueMap.get(setting);
        return Configuration.JavaTypeConverter.valueToUserPermissions(value);
    }

    public String readSettingAsString(final PwmSetting setting) {
        return Configuration.JavaTypeConverter.valueToString(storedValueMap.get(setting));
    }

    @Override
    public List<String> readSettingAsStringArray(final PwmSetting setting) {
        return Configuration.JavaTypeConverter.valueToStringArray(storedValueMap.get(setting));
    }

    @Override
    public List<FormConfiguration> readSettingAsForm(final PwmSetting pwmSetting) {
        return Configuration.JavaTypeConverter.valueToForm(storedValueMap.get(pwmSetting));
    }

    @Override
    public <E extends Enum<E>> Set<E> readSettingAsOptionList(final PwmSetting setting, Class<E> enumClass) {
        return Configuration.JavaTypeConverter.valueToOptionList(setting, storedValueMap.get(setting), enumClass);
    }

    @Override
    public <E extends Enum<E>> E readSettingAsEnum(PwmSetting setting, Class<E> enumClass) {
        return Configuration.JavaTypeConverter.valueToEnum(setting, storedValueMap.get(setting), enumClass);
    }

    public List<ActionConfiguration> readSettingAsAction(final PwmSetting setting) {
        return Configuration.JavaTypeConverter.valueToAction(setting, storedValueMap.get(setting));
    }

    @Override
    public X509Certificate[] readSettingAsCertificate(final PwmSetting setting) {
        if (PwmSettingSyntax.X509CERT != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read X509CERT value for setting: " + setting.toString());
        }
        if (storedValueMap.containsKey(setting)) {
            return (X509Certificate[])storedValueMap.get(setting).toNativeObject();
        }
        return new X509Certificate[0];
    }

    @Override
    public boolean readSettingAsBoolean(final PwmSetting setting) {
        return Configuration.JavaTypeConverter.valueToBoolean(storedValueMap.get(setting));
    }

    @Override
    public long readSettingAsLong(final PwmSetting setting) {
        return Configuration.JavaTypeConverter.valueToLong(storedValueMap.get(setting));
    }

    @Override
    public String readSettingAsLocalizedString(final PwmSetting setting, final Locale locale) {
        return Configuration.JavaTypeConverter.valueToLocalizedString(storedValueMap.get(setting), locale);
    }

    @Override
    public PasswordData readSettingAsPassword(final PwmSetting setting) {
        return Configuration.JavaTypeConverter.valueToPassword(storedValueMap.get(setting));
    }

    @Override
    public List<UserPermission> getPermissionMatches() {
        return readSettingAsUserPermission(profileType().getQueryMatch());
    }

    static Map<PwmSetting,StoredValue> makeValueMap(
            final StoredConfiguration storedConfiguration,
            final String identifier,
            final PwmSettingCategory pwmSettingCategory
    ) {
        final Map<PwmSetting,StoredValue> valueMap = new LinkedHashMap<>();
        for (final PwmSetting setting : pwmSettingCategory.getSettings()) {
            final StoredValue value = storedConfiguration.readSetting(setting, identifier);
            valueMap.put(setting, value);
        }
        return valueMap;
    }
}
