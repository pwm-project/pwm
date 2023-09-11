/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.util.macro;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.LoginInfoBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestContext;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.user.UserInfo;

import java.util.Locale;

public record MacroRequest(
        PwmApplication pwmApplication,
        SessionLabel sessionLabel,
        UserInfo userInfo,
        LoginInfoBean loginInfoBean,
        MacroReplacer macroReplacer,
        UserInfo targetUserInfo,
        Locale userLocale
)
{
    public MacroRequest(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean,
            final MacroReplacer macroReplacer,
            final UserInfo targetUserInfo,
            final Locale userLocale
    )
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;
        this.userInfo = userInfo;
        this.loginInfoBean = loginInfoBean;
        this.macroReplacer = macroReplacer;
        this.targetUserInfo = targetUserInfo;
        this.userLocale = userLocale == null ? PwmConstants.DEFAULT_LOCALE : userLocale;
    }

    public static MacroRequest forStatic()
    {
        return new MacroRequest( null, null, null, null, null, null, null );
    }

    public static MacroRequest forUser(
            final PwmRequestContext pwmRequestContext,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        return forUser( pwmRequestContext.getPwmApplication(), pwmRequestContext.getLocale(), pwmRequestContext.getSessionLabel(), userIdentity );
    }

    public static MacroRequest forUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        return forUser( pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getLabel(), userIdentity );
    }

    public static MacroRequest forUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final MacroReplacer macroReplacer
    )
            throws PwmUnrecoverableException
    {
        return forUser( pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getLabel(), userIdentity, macroReplacer );
    }

    public static MacroRequest forUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean
    )
    {
        return new MacroRequest( pwmApplication, sessionLabel, userInfo, loginInfoBean, null, null, null );
    }

    public static MacroRequest forTargetUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean,
            final UserInfo targetUserInfo
    )
    {
        return new MacroRequest( pwmApplication, sessionLabel, userInfo, loginInfoBean, null, targetUserInfo, null );
    }

    public static MacroRequest forUser(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserInfo userInfo,
            final LoginInfoBean loginInfoBean,
            final MacroReplacer macroReplacer
    )
    {
        return new MacroRequest( pwmApplication, sessionLabel, userInfo, loginInfoBean, macroReplacer, null, null );
    }

    public static MacroRequest forUser(
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfoBean = UserInfoFactory.newUserInfoUsingProxy( pwmApplication, sessionLabel, userIdentity, userLocale );
        return new MacroRequest( pwmApplication, sessionLabel, userInfoBean, null, null, null, userLocale );
    }


    public static MacroRequest forUser(
            final PwmApplication pwmApplication,
            final Locale userLocale,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final MacroReplacer macroReplacer
    )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfoBean = UserInfoFactory.newUserInfoUsingProxy( pwmApplication, sessionLabel, userIdentity, userLocale );
        return new MacroRequest( pwmApplication, sessionLabel, userInfoBean, null, macroReplacer, null, userLocale );
    }

    public static MacroRequest forNonUserSpecific(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel
    )
    {
        return new MacroRequest( pwmApplication, sessionLabel, null, null, null, null, null );
    }

    public String expandMacros( final String input )
    {
        return MacroMachine.expandMacros( this, input );
    }
}
