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

import password.pwm.PwmApplicationMode;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * @author Jason D. Rivard
 */
public abstract class PwmAbstractTag extends TagSupport
{
    @Override
    public int doEndTag( )
            throws JspTagException
    {
        if ( PwmApplicationMode.determineMode( ( HttpServletRequest ) pageContext.getRequest() ) == PwmApplicationMode.ERROR )
        {
            return EVAL_PAGE;
        }

        try
        {
            final HttpServletRequest req = ( HttpServletRequest ) pageContext.getRequest();
            final PwmRequest pwmRequest = PwmRequest.forRequest( req, ( HttpServletResponse ) pageContext.getResponse() );
            try
            {
                final String contents = generateTagBodyContents( pwmRequest );
                pageContext.getOut().write( contents );
            }
            catch ( final Throwable e )
            {
                getLogger().error( pwmRequest, () -> "error processing JSP tag "
                        + this.getClass().getName()
                        + ", error: " + e.getMessage(), e );
            }
        }
        catch ( final Throwable e )
        {
            throw new JspTagException( e.getMessage(), e );
        }
        return EVAL_PAGE;
    }

    protected abstract PwmLogger getLogger();

    protected abstract String generateTagBodyContents( PwmRequest pwmRequest )
            throws PwmUnrecoverableException;
}

