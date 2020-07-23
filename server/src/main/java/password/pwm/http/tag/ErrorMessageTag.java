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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;

/**
 * @author Jason D. Rivard
 */
public class ErrorMessageTag extends PwmAbstractTag
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ErrorMessageTag.class );

    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        try
        {
            final PwmRequest pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(), ( HttpServletResponse ) pageContext.getResponse() );
            PwmApplication pwmApplication = null;
            try
            {
                pwmApplication = ContextManager.getPwmApplication( pageContext.getRequest() );
            }
            catch ( final PwmException e )
            {
                /* noop */
            }

            if ( pwmRequest == null || pwmApplication == null )
            {
                return EVAL_PAGE;
            }

            final ErrorInformation error = ( ErrorInformation ) pwmRequest.getAttribute( PwmRequestAttribute.PwmErrorInfo );

            if ( error != null )
            {
                final boolean allowHtml = Boolean.parseBoolean( pwmRequest.getConfig().readAppProperty( AppProperty.HTTP_ERRORS_ALLOW_HTML ) );
                final boolean showErrorDetail = pwmApplication.determineIfDetailErrorMsgShown();

                String outputMsg = error.toUserStr( pwmRequest.getPwmSession(), pwmApplication );
                if ( !allowHtml )
                {
                    outputMsg = StringUtil.escapeHtml( outputMsg );
                }

                if ( showErrorDetail )
                {
                    final String errorDetail = error.toDebugStr() == null ? "" : " { " + error.toDebugStr() + " }";
                    // detail should always be escaped - it may contain untrusted data
                    outputMsg += "<span class='errorDetail'>" + StringUtil.escapeHtml( errorDetail ) + "</span>";
                }

                outputMsg = outputMsg.replace( "\n", "<br/>" );

                final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( );
                outputMsg = macroMachine.expandMacros( outputMsg );

                pageContext.getOut().write( outputMsg );
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            /* app not running */
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error executing error message tag: " + e.getMessage(), e );
            throw new JspTagException( e.getMessage() );
        }
        return EVAL_PAGE;
    }
}
