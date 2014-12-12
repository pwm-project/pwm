/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.config.policy;

import password.pwm.PwmConstants;
import password.pwm.config.*;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class ForgottenPasswordProfile extends AbstractProfile {

    public ForgottenPasswordProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        super(identifier, storedValueMap);
    }

    @Override
    public String getDisplayName(Locale locale) {
        return null;
    }

    public static ForgottenPasswordProfile makeFromStoredConfiguration(final StoredConfiguration storedConfiguration, final String identifier) {
        final Map<PwmSetting,StoredValue> valueMap = new LinkedHashMap<>();
        for (final PwmSetting setting : PwmSettingCategory.RECOVERY_PROFILE.getSettings()) {
            final StoredValue value = storedConfiguration.readSetting(setting, PwmConstants.PROFILE_ID_DEFAULT.equals(identifier) ? "" : identifier);
            valueMap.put(setting, value);
        }
        return new ForgottenPasswordProfile(identifier, valueMap);

    }

}
