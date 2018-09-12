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

import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.bean.FormNonce;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.JspUtility;
import password.pwm.http.PwmRequest;
import password.pwm.http.state.SessionStateService;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.time.Instant;

public class PwmFormIDTag extends TagSupport
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmFormIDTag.class );

    private static String buildPwmFormID( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        if ( pwmRequest == null || pwmRequest.getPwmApplication() == null )
        {
            return "";
        }

        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        if ( pwmApplication == null )
        {
            return "";
        }
        final SessionStateService sessionStateService = pwmApplication.getSessionStateService();
        final String value = sessionStateService.getSessionStateInfo( pwmRequest );
        final FormNonce formID = new FormNonce(
                pwmRequest.getPwmSession().getLoginInfoBean().getGuid(),
                Instant.now(),
                pwmRequest.getPwmSession().getLoginInfoBean().getReqCounter(),
                value
        );
        return pwmRequest.getPwmApplication().getSecureService().encryptObjectToString( formID );
    }

    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        if ( PwmApplicationMode.determineMode( ( HttpServletRequest ) pageContext.getRequest() ) == PwmApplicationMode.ERROR )
        {
            return EVAL_PAGE;
        }

        try
        {
            final PwmRequest pwmRequest = JspUtility.getPwmRequest( pageContext );
            final String pwmFormID = buildPwmFormID( pwmRequest );

            pageContext.getOut().write( pwmFormID );
        }
        catch ( Exception e )
        {
            try
            {
                pageContext.getOut().write( "errorGeneratingPwmFormID" );
            }
            catch ( IOException e1 )
            {
                /* ignore */
            }
            LOGGER.error( "error during pwmFormIDTag output of pwmFormID: " + e.getMessage(), e );
        }
        return EVAL_PAGE;
    }
}
