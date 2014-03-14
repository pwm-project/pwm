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

package password.pwm.health;

import password.pwm.i18n.Health;
import password.pwm.i18n.LocaleHelper;

import java.util.Locale;

public enum HealthMessage {
    LDAP_No_Connection                      (HealthStatus.WARN,     HealthTopic.LDAP),
    LDAP_Ad_History_Asn_Missing             (HealthStatus.WARN,     HealthTopic.LDAP),
    Email_SendFailure                       (HealthStatus.WARN,     HealthTopic.Email),
    MissingResource                         (HealthStatus.DEBUG,    HealthTopic.Integrity),
    BrokenMethod                            (HealthStatus.DEBUG,    HealthTopic.Integrity),
    Config_UsingLocalDBResponseStorage      (HealthStatus.CAUTION,  HealthTopic.Configuration),
    LDAP_VendorsNotSame                     (HealthStatus.CONFIG,   HealthTopic.LDAP),
    Health_Config_ConfigMode                (HealthStatus.CAUTION,  HealthTopic.Configuration),
    LDAP_OK                                 (HealthStatus.GOOD,     HealthTopic.LDAP),
    CryptoTokenWithNewUserVerification      (HealthStatus.CAUTION,  HealthTopic.Configuration),
    TokenServiceError                       (HealthStatus.WARN,     HealthTopic.TokenService),
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
