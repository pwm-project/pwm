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

import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Display;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.util.Locale;

public class JspThrowableHandlerTag extends TagSupport
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( JspThrowableHandlerTag.class );

    @Override
    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        if ( pageContext.getErrorData() == null || pageContext.getErrorData().getThrowable() == null )
        {
            return EVAL_PAGE;
        }


        try
        {
            final Throwable jspThrowable = pageContext.getErrorData().getThrowable();
            final String exceptionStr = JavaHelper.throwableToString( jspThrowable );
            final String errorHash = SecureEngine.hash( exceptionStr, PwmHashAlgorithm.SHA1 );

            LOGGER.error( () -> "jsp error reference " + errorHash, jspThrowable );

            final String jspOutout = jspOutput( errorHash );
            pageContext.getOut().write( jspOutout );
        }
        catch ( final Exception e )
        {
            try
            {
                pageContext.getOut().write( "" );
            }
            catch ( final IOException e1 )
            {
                /* ignore */
            }
            LOGGER.error( () -> "error during pwmFormIDTag output of pwmFormID: " + e.getMessage() );
        }
        return EVAL_PAGE;
    }

    private String jspOutput( final String errorReference )
    {
        Locale userLocale = PwmConstants.DEFAULT_LOCALE;
        Configuration configuration = null;
        try
        {
            final PwmRequest pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(), ( HttpServletResponse ) pageContext.getResponse() );
            userLocale = pwmRequest.getLocale();
            configuration = pwmRequest.getConfig();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error during pwmFormIDTag output of pwmFormID: " + e.getMessage() );
        }
        final String[] strArgs = new String[]
                {
                        errorReference,
                };
        return LocaleHelper.getLocalizedMessage( userLocale, Display.Display_ErrorReference, configuration, strArgs );

    }
}
