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
