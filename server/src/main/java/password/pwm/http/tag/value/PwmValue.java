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

package password.pwm.http.tag.value;

import password.pwm.util.java.CollectionUtil;

import java.util.Set;

public enum PwmValue
{
    cspNonce( new PwmValueHandlers.CspNonceHandlerPwm() ),
    homeURL( new PwmValueHandlers.HomeUrlHandlerPwm() ),
    passwordFieldType( new PwmValueHandlers.PasswordFieldTypeHandlerPwm() ),
    responseFieldType( new PwmValueHandlers.ResponseFieldTypeHandlerPwm() ),
    customJavascript( new PwmValueHandlers.CustomJavascriptHandlerPwm(), Flag.DoNotEscape ),
    currentJspFilename( new PwmValueHandlers.CurrentJspFilenameHandlerPwm() ),
    instanceID( new PwmValueHandlers.InstanceIDHandlerPwm() ),
    headerMenuNotice( new PwmValueHandlers.HeaderMenuNoticeHandlerPwm() ),
    clientETag( new PwmValueHandlers.ClientETag() ),
    localeCode( new PwmValueHandlers.LocaleCodeHandlerPwm() ),
    localeDir( new PwmValueHandlers.LocaleDirHandlerPwm() ),
    localeFlagFile( new PwmValueHandlers.LocaleFlagFileHandlerPwm() ),
    localeName( new PwmValueHandlers.LocaleNameHandlerPwm() ),
    inactiveTimeRemaining( new PwmValueHandlers.InactiveTimeRemainingHandlerPwm() ),
    sessionID( new PwmValueHandlers.SessionIDPwmValue() ),;

    private final PwmValueHandler pwmValueHandler;
    private final Set<Flag> flags;

    enum Flag
    {
        DoNotEscape,
    }

    PwmValue( final PwmValueHandler pwmValueHandler, final Flag... flags )
    {
        this.pwmValueHandler = pwmValueHandler;
        this.flags = CollectionUtil.enumSetFromArray( flags );
    }

    public PwmValueHandler getValueOutput( )
    {
        return pwmValueHandler;
    }

    public Set<Flag> getFlags( )
    {
        return flags;
    }


}
