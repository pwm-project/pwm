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

package password.pwm.ws.client.rest.naaf;

public enum NAAFLoginMethod {
    PASSWORD("PASSWORD:1",NAAFMethods.NAAFPasswordMethodHandler.class),
    LDAP_PASSWORD("LDAP_PASSWORD:1",NAAFMethods.NAAFLdapPasswordMethodHandler.class),
    SECURITY_QUESTIONS("SECQUEST:1",NAAFMethods.NAAFSecurityQuestionsMethodHandler.class),
    EMAIL_OTP("EMAIL_OTP:1",NAAFMethods.NAAFEmailOTPMethodHandler.class),
    SMS_OTP("SMS_OTP:1",NAAFMethods.NAAFSMSOTPMethodHandler.class),
    SMARTPHONE("SMARTPHONE:1",NAAFMethods.NAAFSmartphoneMethodHandler.class),
    RADIUS("RADIUS:1",NAAFMethods.NAAFSmartphoneMethodHandler.class),
    TOTP("TOTP:1",NAAFMethods.NAAFTOTPMethodHandler.class),
    HOTP("HOTP:1",NAAFMethods.NAAFHOTPMethodHandler.class),

    ;

    private final String naafName;
    private final Class<? extends NAAFMethodHandler> naafMethodHandler;

    NAAFLoginMethod(String naafName, Class<? extends NAAFMethodHandler> naafMethodHandler) {
        this.naafName = naafName;
        this.naafMethodHandler = naafMethodHandler;
    }

    public String getNaafName() {
        return naafName;
    }

    public Class<? extends NAAFMethodHandler> getNaafMethodHandler() {
        return naafMethodHandler;
    }

}
