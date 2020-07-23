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

package password.pwm.http.tag;

import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.i18n.Message;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;

/**
 * @author Jason D. Rivard
 */
public class SuccessMessageTag extends PwmAbstractTag
{

    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        try
        {
            final HttpServletRequest req = ( HttpServletRequest ) pageContext.getRequest();
            final PwmRequest pwmRequest = PwmRequest.forRequest( req, ( HttpServletResponse ) pageContext.getResponse() );

            final String successMsg = ( String ) pwmRequest.getAttribute( PwmRequestAttribute.SuccessMessage );

            final String outputMsg;
            if ( successMsg == null || successMsg.isEmpty() )
            {
                outputMsg = Message.getLocalizedMessage( pwmRequest.getLocale(), Message.Success_Unknown, pwmRequest.getConfig() );
            }
            else
            {
                if ( pwmRequest.isAuthenticated() )
                {
                    final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( );
                    outputMsg = macroMachine.expandMacros( successMsg );
                }
                else
                {
                    outputMsg = successMsg;
                }
            }

            pageContext.getOut().write( outputMsg );
        }
        catch ( final Exception e )
        {
            throw new JspTagException( e.getMessage() );
        }
        return EVAL_PAGE;
    }
}

