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

package password.pwm.http.tag.conditional;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.Permission;
import password.pwm.PwmApplicationMode;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyTagSupport;

public class PwmIfTag extends BodyTagSupport
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmIfTag.class );

    private PwmIfTest test;
    private Permission permission;
    private boolean negate;
    private PwmRequestFlag requestFlag;
    private PwmSetting setting;

    public void setTest( final PwmIfTest test )
    {
        this.test = test;
    }

    public void setPermission( final Permission permission )
    {
        this.permission = permission;
    }

    public void setNegate( final boolean negate )
    {
        this.negate = negate;
    }

    public void setRequestFlag( final PwmRequestFlag requestFlag )
    {
        this.requestFlag = requestFlag;
    }

    public void setSetting( final PwmSetting setting )
    {
        this.setting = setting;
    }

    @Override
    public int doStartTag( )
            throws JspException
    {

        boolean showBody = false;
        if ( PwmApplicationMode.determineMode( ( HttpServletRequest ) pageContext.getRequest() ) != PwmApplicationMode.ERROR )
        {
            if ( test != null )
            {
                try
                {

                    final PwmRequest pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(),
                            ( HttpServletResponse ) pageContext.getResponse() );

                    final PwmIfTest testEnum = test;
                    if ( testEnum != null )
                    {
                        try
                        {
                            final PwmIfOptions options = new PwmIfOptions( negate, permission, setting, requestFlag );
                            showBody = testEnum.passed( pwmRequest, options );
                        }
                        catch ( final ChaiUnavailableException e )
                        {
                            LOGGER.error( () -> "error testing jsp if '" + testEnum.toString() + "', error: " + e.getMessage() );
                        }
                    }
                    else
                    {
                        final String errorMsg = "unknown test name '" + test + "' in pwm:If jsp tag!";
                        LOGGER.warn( pwmRequest, () -> errorMsg );
                    }
                }
                catch ( final PwmUnrecoverableException e )
                {
                    LOGGER.error( () -> "error executing PwmIfTag for test '" + test + "', error: " + e.getMessage() );
                }
            }
        }

        if ( negate )
        {
            showBody = !showBody;
        }

        return showBody ? EVAL_BODY_INCLUDE : SKIP_BODY;
    }
}

