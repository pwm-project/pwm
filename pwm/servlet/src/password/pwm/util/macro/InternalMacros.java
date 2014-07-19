/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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
import password.pwm.PwmConstants;
import password.pwm.bean.UserInfoBean;
import password.pwm.ldap.UserDataReader;
import password.pwm.util.PwmLogger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public abstract class InternalMacros {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(InternalMacros.class);

    public static final List<Class<? extends MacroImplementation>> INTERNAL_MACROS;
    static {
        final List<Class<? extends MacroImplementation>> defaultMacros = new ArrayList<>();
        defaultMacros.add(OtpSetupTimeMacro.class);
        defaultMacros.add(ResponseSetupTimeMacro.class);
        INTERNAL_MACROS = Collections.unmodifiableList(defaultMacros);
    }

    public static class OtpSetupTimeMacro extends AbstractMacro {
        UserInfoBean userInfoBean;

        public Pattern getRegExPattern()
        {
            return Pattern.compile("@OtpSetupTime@");
        }

        public String replaceValue(String matchValue)
        {
            if (userInfoBean != null && userInfoBean.getOtpUserRecord() != null && userInfoBean.getOtpUserRecord().getTimestamp() != null) {
                return PwmConstants.DEFAULT_DATETIME_FORMAT.format(userInfoBean.getOtpUserRecord().getTimestamp());
            }
            return null;
        }

        public void init(
                PwmApplication pwmApplication,
                UserInfoBean userInfoBean,
                UserDataReader userDataReader
        )
        {
            this.userInfoBean = userInfoBean;
        }
    }

    public static class ResponseSetupTimeMacro extends AbstractMacro {
        UserInfoBean userInfoBean;

        public Pattern getRegExPattern()
        {
            return Pattern.compile("@ResponseSetupTime@");
        }

        public String replaceValue(String matchValue)
        {
            if (userInfoBean != null && userInfoBean.getResponseInfoBean() != null && userInfoBean.getResponseInfoBean().getTimestamp() != null) {
                return PwmConstants.DEFAULT_DATETIME_FORMAT.format(userInfoBean.getResponseInfoBean().getTimestamp());
            }
            return null;
        }

        public void init(
                PwmApplication pwmApplication,
                UserInfoBean userInfoBean,
                UserDataReader userDataReader
        )
        {
            this.userInfoBean = userInfoBean;
        }
    }
}
