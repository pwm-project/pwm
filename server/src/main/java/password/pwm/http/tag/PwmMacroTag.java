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

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

public class PwmMacroTag extends TagSupport
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmMacroTag.class );

    private String value;

    public String getValue( )
    {
        return value;
    }

    public void setValue( final String value )
    {
        this.value = value;
    }

    public int doEndTag( )
            throws JspTagException
    {
        try
        {
            final PwmRequest pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(), ( HttpServletResponse ) pageContext.getResponse() );
            final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( );
            final String outputValue = macroMachine.expandMacros( value );
            pageContext.getOut().write( outputValue );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( () -> "error while processing PwmMacroTag: " + e.getMessage() );
        }
        catch ( final Exception e )
        {
            throw new JspTagException( e.getMessage(), e );
        }
        return EVAL_PAGE;
    }
}
