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

package password.pwm;

import password.pwm.config.PwmSetting;
import password.pwm.util.logging.PwmLogger;

/**
 * @author Jason D. Rivard
 */
public enum Permission {
    PWMADMIN(PwmSetting.QUERY_MATCH_PWM_ADMIN),
    CHANGE_PASSWORD(PwmSetting.QUERY_MATCH_CHANGE_PASSWORD),
    ACTIVATE_USER(PwmSetting.ACTIVATE_USER_QUERY_MATCH),
    SETUP_RESPONSE(PwmSetting.QUERY_MATCH_SETUP_RESPONSE),
    SETUP_OTP_SECRET(PwmSetting.OTP_SETUP_USER_PERMISSION),
    GUEST_REGISTRATION(PwmSetting.GUEST_ADMIN_GROUP),
    PEOPLE_SEARCH(PwmSetting.PEOPLE_SEARCH_QUERY_MATCH),
    PROFILE_UPDATE(PwmSetting.UPDATE_PROFILE_QUERY_MATCH),
    WEBSERVICE(PwmSetting.WEBSERVICES_QUERY_MATCH),
    WEBSERVICE_THIRDPARTY(PwmSetting.WEBSERVICES_THIRDPARTY_QUERY_MATCH),

    ;


// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(Permission.class);

    private PwmSetting pwmSetting;

// -------------------------- STATIC METHODS --------------------------


// --------------------------- CONSTRUCTORS ---------------------------

    Permission(final PwmSetting pwmSetting)
    {
        this.pwmSetting = pwmSetting;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public PwmSetting getPwmSetting()
    {
        return pwmSetting;
    }

// -------------------------- ENUMERATIONS --------------------------

    public enum PERMISSION_STATUS {
        UNCHECKED,
        GRANTED,
        DENIED
    }
}
