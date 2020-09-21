/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http.servlet.configguide;

import password.pwm.config.PwmSetting;

public enum ConfigGuideFormField
{
    PARAM_TEMPLATE_LDAP( PwmSetting.TEMPLATE_LDAP ),
    PARAM_TEMPLATE_STORAGE( PwmSetting.TEMPLATE_STORAGE ),

    PARAM_APP_SITEURL( PwmSetting.PWM_SITE_URL ),

    PARAM_LDAP_HOST( null ),
    PARAM_LDAP_PORT( null ),
    PARAM_LDAP_SECURE( null ),
    PARAM_LDAP_PROXY_DN( PwmSetting.LDAP_PROXY_USER_DN ),
    PARAM_LDAP_PROXY_PW( PwmSetting.LDAP_PROXY_USER_PASSWORD ),

    PARAM_LDAP_CONTEXT( PwmSetting.LDAP_CONTEXTLESS_ROOT ),
    PARAM_LDAP_TEST_USER_ENABLED( null ),
    PARAM_LDAP_TEST_USER( PwmSetting.LDAP_TEST_USER_DN ),
    PARAM_LDAP_ADMIN_USER( PwmSetting.QUERY_MATCH_PWM_ADMIN ),

    PARAM_DB_CLASSNAME( PwmSetting.DATABASE_CLASS ),
    PARAM_DB_CONNECT_URL( PwmSetting.DATABASE_URL ),
    PARAM_DB_USERNAME( PwmSetting.DATABASE_USERNAME ),
    PARAM_DB_PASSWORD( PwmSetting.DATABASE_PASSWORD ),
    PARAM_DB_VENDOR( PwmSetting.DB_VENDOR_TEMPLATE ),

    PARAM_TELEMETRY_ENABLE( PwmSetting.PUBLISH_STATS_ENABLE ),
    PARAM_TELEMETRY_DESCRIPTION( PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION ),

    CHALLENGE_RESPONSE_DATA( null ),

    PARAM_CONFIG_PASSWORD( null ),;

    private final PwmSetting pwmSetting;

    ConfigGuideFormField( final PwmSetting pwmSetting )
    {
        this.pwmSetting = pwmSetting;
    }

    public PwmSetting getPwmSetting( )
    {
        return pwmSetting;
    }
}
