/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.util.macro;

import password.pwm.PwmApplication;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.ldap.UserDataReader;

import java.util.regex.Pattern;

public interface MacroImplementation {
    enum Scope {
        Static,
        System,
        User,
    }

    Pattern getRegExPattern();

    String replaceValue(final String matchValue, final MacroRequestInfo macroRequestInfo)
            throws MacroParseException;

    interface MacroRequestInfo {
        PwmApplication getPwmApplication();
        UserInfoBean getUserInfoBean();
        LoginInfoBean getLoginInfoBean();
        UserDataReader getUserDataReader();
    }

    MacroDefinitionFlag[] flags();

    enum MacroDefinitionFlag {
        SensitiveValue,
        OnlyDebugLogging,
    }
}
