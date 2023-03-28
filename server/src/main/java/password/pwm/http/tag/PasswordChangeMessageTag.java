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

package password.pwm.http.tag;

import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.error.PwmException;
import password.pwm.http.PwmRequest;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.util.Optional;

/**
 * @author Jason D. Rivard
 */
public class PasswordChangeMessageTag extends TagSupport
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PasswordChangeMessageTag.class );


    @Override
    public int doEndTag( )
            throws JspTagException
    {
        try
        {
            final PwmRequest pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(), ( HttpServletResponse ) pageContext.getResponse() );

            final PwmPasswordPolicy pwmPasswordPolicy = PasswordRequirementsTag.readPasswordPolicy( pwmRequest );

            final Optional<String> passwordPolicyChangeMessage = pwmPasswordPolicy.getChangeMessage( pwmRequest.getLocale() );

            if ( passwordPolicyChangeMessage.isPresent() )
            {
                final MacroRequest macroRequest = pwmRequest.getMacroMachine( );
                final String expandedMessage = macroRequest.expandMacros( passwordPolicyChangeMessage.get() );
                pageContext.getOut().write( expandedMessage );
            }
        }
        catch ( final IOException | PwmException e )
        {
            LOGGER.error( () -> "unexpected error during password change message generation: " + e.getMessage(), e );
            throw new JspTagException( e.getMessage() );
        }
        return EVAL_PAGE;
    }
}

