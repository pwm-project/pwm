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

package password.pwm.health;

import password.pwm.i18n.Health;
import password.pwm.util.LocaleHelper;

import java.util.Locale;

public enum HealthMessage {
    LDAP_No_Connection                      (HealthStatus.WARN,     HealthTopic.LDAP),
    LDAP_Ad_History_Asn_Missing             (HealthStatus.WARN,     HealthTopic.LDAP),
    LDAP_AD_Unsecure                        (HealthStatus.WARN,     HealthTopic.LDAP),
    LDAP_AD_StaticIP                        (HealthStatus.WARN,     HealthTopic.LDAP),
    LDAP_ProxyTestSameUser                  (HealthStatus.CAUTION,  HealthTopic.Configuration),
    LDAP_TestUserUnavailable                (HealthStatus.CAUTION,  HealthTopic.LDAP),
    LDAP_TestUserUnexpected                 (HealthStatus.WARN,     HealthTopic.LDAP),
    LDAP_TestUserError                      (HealthStatus.WARN,     HealthTopic.LDAP),
    LDAP_TestUserPolicyError                (HealthStatus.WARN,     HealthTopic.LDAP),
    LDAP_TestUserNoTempPass                 (HealthStatus.WARN,     HealthTopic.LDAP),
    LDAP_TestUserOK                         (HealthStatus.GOOD,     HealthTopic.LDAP),
    Email_SendFailure                       (HealthStatus.WARN,     HealthTopic.Email),
    MissingResource                         (HealthStatus.DEBUG,    HealthTopic.Integrity),
    BrokenMethod                            (HealthStatus.DEBUG,    HealthTopic.Integrity),
    Config_MissingProxyDN                   (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_MissingProxyPassword             (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_NoSiteURL                        (HealthStatus.WARN,     HealthTopic.Configuration),
    Config_RequireHttps                     (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_LDAPWireTrace                    (HealthStatus.WARN,     HealthTopic.Configuration),
    Config_PromiscuousLDAP                  (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_ShowDetailedErrors               (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_AddTestUser                      (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_ParseError                       (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_UsingLocalDBResponseStorage      (HealthStatus.CAUTION,  HealthTopic.Configuration),
    Config_WeakPassword                     (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_LDAPUnsecure                     (HealthStatus.CAUTION,  HealthTopic.Configuration),
    Config_ConfigMode                       (HealthStatus.CAUTION,  HealthTopic.Configuration),
    Config_MissingDB                        (HealthStatus.CAUTION,  HealthTopic.Configuration),
    Config_MissingLDAPResponseAttr          (HealthStatus.CAUTION,  HealthTopic.Configuration),
    Config_URLNotSecure                     (HealthStatus.CAUTION,  HealthTopic.Configuration),
    Config_PasswordPolicyProblem            (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_UserPermissionValidity           (HealthStatus.CONFIG,   HealthTopic.Configuration),
    Config_NoRecoveryEnabled                (HealthStatus.CAUTION,  HealthTopic.Configuration),
    Config_Certificate                      (HealthStatus.WARN,     HealthTopic.Configuration),
    LDAP_VendorsNotSame                     (HealthStatus.CONFIG,   HealthTopic.LDAP),
    LDAP_OK                                 (HealthStatus.GOOD,     HealthTopic.LDAP),
    LDAP_RecentlyUnreachable                (HealthStatus.CAUTION,  HealthTopic.LDAP),
    CryptoTokenWithNewUserVerification      (HealthStatus.CAUTION,  HealthTopic.Configuration),
    TokenServiceError                       (HealthStatus.WARN,     HealthTopic.TokenService),
    Java_HighThreads                        (HealthStatus.CAUTION,  HealthTopic.Platform),
    Java_SmallHeap                          (HealthStatus.CAUTION,  HealthTopic.Platform),
    Java_OK                                 (HealthStatus.GOOD,     HealthTopic.Platform),
    LocalDB_OK                              (HealthStatus.GOOD,     HealthTopic.LocalDB),
    LocalDB_BAD                             (HealthStatus.WARN,     HealthTopic.LocalDB),
    LocalDB_NEW                             (HealthStatus.WARN,     HealthTopic.LocalDB),
    LocalDB_CLOSED                          (HealthStatus.WARN,     HealthTopic.LocalDB),
    ServiceClosed_LocalDBUnavail            (HealthStatus.CAUTION,  HealthTopic.Application),
    ServiceClosed_AppReadOnly               (HealthStatus.CAUTION,  HealthTopic.Application),
    SMS_SendFailure                         (HealthStatus.WARN,     HealthTopic.SMS),

    ;

    private final HealthStatus status;
    private final HealthTopic topic;

    HealthMessage(
            HealthStatus status,
            HealthTopic topic
    )
    {
        this.status = status;
        this.topic = topic;
    }

    public HealthStatus getStatus()
    {
        return status;
    }

    public HealthTopic getTopic()
    {
        return topic;
    }

    public String getKey() {
        return HealthMessage.class.getSimpleName() + "_" + this.toString();
    }

    public String getDescription(final Locale locale, final password.pwm.config.Configuration config, final String[] fields) {
        return LocaleHelper.getLocalizedMessage(locale, this.getKey(), config, Health.class, fields);
    }
}
