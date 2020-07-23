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

package password.pwm.http.tag.value;

import password.pwm.PwmApplicationMode;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * @author Jason D. Rivard
 */
public class PwmValueTag extends TagSupport
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmValueTag.class );

    private PwmValue name;

    public PwmValue getName( )
    {
        return name;
    }

    public void setName( final PwmValue name )
    {
        this.name = name;
    }

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
                final PwmValue value = getName();
                final String output = calcValue( pwmRequest, pageContext, value );
                final String escapedOutput = value.getFlags().contains( PwmValue.Flag.DoNotEscape )
                        ? output
                        : StringUtil.escapeHtml( output );
                pageContext.getOut().write( escapedOutput );

            }
            catch ( final IllegalArgumentException e )
            {
                LOGGER.error( () -> "can't output requested value name '" + getName() + "'" );
            }
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( () -> "error while processing PwmValueTag: " + e.getMessage() );
        }
        catch ( final Exception e )
        {
            throw new JspTagException( e.getMessage(), e );
        }
        return EVAL_PAGE;
    }

    public String calcValue(
            final PwmRequest pwmRequest,
            final PageContext pageContext,
            final PwmValue value
    )
    {

        if ( value != null )
        {
            try
            {
                return value.getValueOutput().valueOutput( pwmRequest, pageContext );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "error executing value tag option '" + value.toString() + "', error: " + e.getMessage() );
            }
        }

        return "";
    }
}
