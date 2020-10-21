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

package password.pwm.ldap.auth;

public enum AuthenticationType
{
    UNAUTHENTICATED,

    // normal authentication
    AUTHENTICATED,

    // normal auth, but can't do ldap bind (ad pw expired, force change, etc)
    AUTH_BIND_INHIBIT,

    // auth via forgotten password or user activation or similar /public auth.
    AUTH_FROM_PUBLIC_MODULE,

    // auth via SSO method that did not supply the user's password
    AUTH_WITHOUT_PASSWORD,

    // authentication due to crypto request cookie from peer instance
    AUTH_FROM_REQ_COOKIE,
}
