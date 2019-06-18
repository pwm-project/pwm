/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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
import password.pwm.bean.LoginInfoBean;
import password.pwm.ldap.UserInfo;

import java.util.regex.Pattern;

public interface MacroImplementation
{
    enum Scope
    {
        Static,
        System,
        User,
    }

    enum Sequence
    {
        normal,
        post,
    }

    Pattern getRegExPattern( );

    String replaceValue( String matchValue, MacroRequestInfo macroRequestInfo )
            throws MacroParseException;

    interface MacroRequestInfo
    {
        PwmApplication getPwmApplication( );

        UserInfo getUserInfo( );

        LoginInfoBean getLoginInfoBean( );
    }

    MacroDefinitionFlag[] flags( );

    default Sequence getSequence( )
    {
        return Sequence.normal;
    }

    enum MacroDefinitionFlag
    {
        SensitiveValue,
        OnlyDebugLogging,
    }
}
