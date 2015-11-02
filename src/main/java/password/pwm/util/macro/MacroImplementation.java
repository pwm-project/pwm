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

package password.pwm.util.macro;

import password.pwm.PwmApplication;
import password.pwm.bean.UserInfoBean;
import password.pwm.http.bean.LoginInfoBean;
import password.pwm.ldap.UserDataReader;

import java.util.regex.Pattern;

public interface MacroImplementation {
    public Pattern getRegExPattern();
    
    public String replaceValue(final String matchValue, final MacroRequestInfo macroRequestInfo) 
            throws MacroParseException;

    public interface MacroRequestInfo {
        PwmApplication getPwmApplication();
        UserInfoBean getUserInfoBean();
        LoginInfoBean getLoginInfoBean();
        UserDataReader getUserDataReader();
    }

    boolean isSensitive();
}
