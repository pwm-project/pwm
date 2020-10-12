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

package password.pwm;

import password.pwm.bean.SessionLabel;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;

import java.io.Serializable;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public interface VerificationMethodSystem
{
    enum VerificationState
    {
        INPROGRESS,
        FAILED,
        COMPLETE,
    }

    interface UserPrompt
    {
        String getDisplayPrompt( );

        String getIdentifier( );
    }

    class UserPromptBean implements Serializable, UserPrompt
    {
        private String displayPrompt;
        private String identifier;

        @Override
        public String getDisplayPrompt( )
        {
            return displayPrompt;
        }

        public void setDisplayPrompt( final String displayPrompt )
        {
            this.displayPrompt = displayPrompt;
        }

        @Override
        public String getIdentifier( )
        {
            return identifier;
        }

        public void setIdentifier( final String identifier )
        {
            this.identifier = identifier;
        }
    }

    List<UserPrompt> getCurrentPrompts( ) throws PwmUnrecoverableException;

    String getCurrentDisplayInstructions( );

    ErrorInformation respondToPrompts( Map<String, String> answers ) throws PwmUnrecoverableException;

    VerificationState getVerificationState( );

    void init( PwmApplication pwmApplication, UserInfo userInfoBean, SessionLabel sessionLabel, Locale locale )
            throws PwmUnrecoverableException;
}
