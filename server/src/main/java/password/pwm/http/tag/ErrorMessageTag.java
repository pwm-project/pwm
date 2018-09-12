/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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
                pwmApplication = ContextManager.getPwmApplication( pageContext.getSession() );
            }
            catch ( PwmException e )
            { /* noop */ }

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

                final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( pwmApplication );
                outputMsg = macroMachine.expandMacros( outputMsg );

                pageContext.getOut().write( outputMsg );
            }
        }
        catch ( PwmUnrecoverableException e )
        {
            /* app not running */
        }
        catch ( Exception e )
        {
            LOGGER.error( "error executing error message tag: " + e.getMessage(), e );
            throw new JspTagException( e.getMessage() );
        }
        return EVAL_PAGE;
    }
}
