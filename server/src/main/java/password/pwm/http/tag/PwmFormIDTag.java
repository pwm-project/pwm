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
        catch ( final Exception e )
        {
            try
            {
                pageContext.getOut().write( "errorGeneratingPwmFormID" );
            }
            catch ( final IOException e1 )
            {
                /* ignore */
            }
            LOGGER.error( () -> "error during pwmFormIDTag output of pwmFormID: " + e.getMessage(), e );
        }
        return EVAL_PAGE;
    }
}
