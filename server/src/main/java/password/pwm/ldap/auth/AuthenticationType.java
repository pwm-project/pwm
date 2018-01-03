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
