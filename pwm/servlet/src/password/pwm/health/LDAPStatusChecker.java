/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

package password.pwm.health;

import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.provider.ChaiProvider;
import password.pwm.ContextManager;
import password.pwm.Helper;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LDAPStatusChecker implements HealthChecker {

    public List<HealthRecord> doHealthCheck(final ContextManager contextManager) {
        final StoredConfiguration storedConfig = contextManager.getConfigReader().getStoredConfiguration();
        final ErrorInformation result = doLdapStatusCheck(storedConfig);
        if (result == null || result.getError().equals(PwmError.CONFIG_LDAP_SUCCESS)) {
            final HealthRecord hr = new HealthRecord(HealthRecord.HealthStatus.GOOD,"LDAP Connectivity","All configured LDAP servers are reachable");
            return Collections.singletonList(hr);
        }

        final HealthRecord hr = new HealthRecord(HealthRecord.HealthStatus.WARN,"LDAP Connectivity",result.toDebugStr());
        return Collections.singletonList(hr);
    }

    public static ErrorInformation doLdapStatusCheck(final StoredConfiguration storedconfiguration) {
        final Configuration config = new Configuration(storedconfiguration);

        ChaiProvider chaiProvider = null;
        try {
            chaiProvider = Helper.createChaiProvider(
                    config,
                    config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_DN),
                    config.readSettingAsString(PwmSetting.LDAP_PROXY_USER_PASSWORD),
                    config.readSettingAsInt(PwmSetting.LDAP_PROXY_IDLE_TIMEOUT));
            chaiProvider.getDirectoryVendor();

            final String contextlessRootSettingName = PwmSetting.LDAP_CONTEXTLESS_ROOT.getCategory().getLabel(Locale.getDefault()) + "-" + PwmSetting.LDAP_CONTEXTLESS_ROOT.getLabel(Locale.getDefault());

            try {
                final ChaiEntry contextlessRootEntry = ChaiFactory.createChaiEntry(config.readSettingAsString(PwmSetting.LDAP_CONTEXTLESS_ROOT),chaiProvider);
                if (!contextlessRootEntry.isValid()) {
                    final String errorString = "setting '" + contextlessRootSettingName  + "' value does not appear to be correct";
                    return new ErrorInformation(PwmError.CONFIG_LDAP_FAILURE,errorString,errorString);
                }
            } catch (Exception e) {
                final String errorString = "error verifying setting '" + contextlessRootSettingName  + "' " + e.getMessage();
                return new ErrorInformation(PwmError.CONFIG_LDAP_FAILURE,errorString,errorString);
            }

            return new ErrorInformation(PwmError.CONFIG_LDAP_SUCCESS);
        } catch (Exception e) {
            final String errorString = "error connecting to ldap server: " + e.getMessage();
            return new ErrorInformation(PwmError.CONFIG_LDAP_FAILURE,errorString,errorString);
        } finally {
            if (chaiProvider != null) {
                try {
                    chaiProvider.close();
                } catch (Exception e) {
                    // don't care.
                }
            }
        }
    }
}
