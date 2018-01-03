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
    PARAM_LDAP_ADMIN_GROUP( PwmSetting.QUERY_MATCH_PWM_ADMIN ),

    PARAM_DB_CLASSNAME( PwmSetting.DATABASE_CLASS ),
    PARAM_DB_CONNECT_URL( PwmSetting.DATABASE_URL ),
    PARAM_DB_USERNAME( PwmSetting.DATABASE_USERNAME ),
    PARAM_DB_PASSWORD( PwmSetting.DATABASE_PASSWORD ),
    PARAM_DB_VENDOR( PwmSetting.DB_VENDOR_TEMPLATE ),

    PARAM_TELEMETRY_ENABLE( PwmSetting.PUBLISH_STATS_ENABLE ),
    PARAM_TELEMETRY_DESCRIPTION( PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION ),

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
