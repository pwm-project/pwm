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

package password.pwm.i18n;

public enum ConfigGuide implements PwmDisplayBundle
{

    ldap_admin_description,
    ldap_admin_title,
    ldap_admin_title_proxy_dn,
    ldap_admin_title_proxy_pw,
    ldap_cert_description,
    ldap_context_description,
    ldap_context_admin_title,
    ldap_context_admin_description,
    ldap_server_description,
    ldap_server_title,
    ldap_server_title_hostname,
    ldap_server_title_port,
    ldap_server_title_secure,
    ldap_telemetry_enable_title,
    ldap_telemetry_description_title,
    ldap_testuser_description,
    password_description,
    password_title,
    password_title_verify,
    template_description,
    title,;

    @Override
    public String getKey( )
    {
        return this.toString();
    }
}
