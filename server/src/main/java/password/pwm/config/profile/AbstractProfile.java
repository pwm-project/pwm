/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.SettingReader;
import password.pwm.config.StoredValue;
import password.pwm.config.value.data.UserPermission;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.VerificationMethodValue;
import password.pwm.util.PasswordData;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class AbstractProfile implements Profile, SettingReader {

    protected final String identifier;
    protected final Map<PwmSetting,StoredValue> storedValueMap;

    protected AbstractProfile(final String identifier, final Map<PwmSetting, StoredValue> storedValueMap) {
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
    public <E extends Enum<E>> Set<E> readSettingAsOptionList(final PwmSetting setting, final Class<E> enumClass) {
        return Configuration.JavaTypeConverter.valueToOptionList(setting, storedValueMap.get(setting), enumClass);
    }

    @Override
    public <E extends Enum<E>> E readSettingAsEnum(final PwmSetting setting, final Class<E> enumClass) {
        return Configuration.JavaTypeConverter.valueToEnum(setting, storedValueMap.get(setting), enumClass);
    }

    public List<ActionConfiguration> readSettingAsAction(final PwmSetting setting) {
        return Configuration.JavaTypeConverter.valueToAction(setting, storedValueMap.get(setting));
    }

    @Override
    public List<X509Certificate> readSettingAsCertificate(final PwmSetting setting) {
        if (PwmSettingSyntax.X509CERT != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read X509CERT value for setting: " + setting.toString());
        }
        if (storedValueMap.containsKey(setting)) {
            final X509Certificate[] arrayCert = (X509Certificate[])storedValueMap.get(setting).toNativeObject();
            return arrayCert == null ? Collections.emptyList() : Arrays.asList(arrayCert);
        }
        return Collections.emptyList();
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
        final List<PwmSettingCategory> categories = new ArrayList<>();
        categories.add(pwmSettingCategory);
        categories.addAll(pwmSettingCategory.getChildCategories());
        for (final PwmSettingCategory category : categories) {
            for (final PwmSetting setting : category.getSettings()) {
                final StoredValue value = storedConfiguration.readSetting(setting, identifier);
                valueMap.put(setting, value);
            }
        }
        return valueMap;
    }

    public Set<IdentityVerificationMethod> readVerificationMethods(final PwmSetting pwmSetting, final VerificationMethodValue.EnabledState enabledState) {
        final Set<IdentityVerificationMethod> result = new LinkedHashSet<>();
        final StoredValue configValue = storedValueMap.get(pwmSetting);
        final VerificationMethodValue.VerificationMethodSettings verificationMethodSettings = (VerificationMethodValue.VerificationMethodSettings)configValue.toNativeObject();

        for (final IdentityVerificationMethod recoveryVerificationMethods : IdentityVerificationMethod.availableValues()) {
            if (verificationMethodSettings.getMethodSettings().containsKey(recoveryVerificationMethods)) {
                if (verificationMethodSettings.getMethodSettings().get(recoveryVerificationMethods).getEnabledState() == enabledState) {
                    result.add(recoveryVerificationMethods);
                }
            }
        }
        return result;
    }

}
