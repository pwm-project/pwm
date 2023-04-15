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

package password.pwm.http.tag.value;

import password.pwm.http.PwmRequest;
import password.pwm.http.tag.PwmAbstractTag;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.jsp.PageContext;

/**
 * @author Jason D. Rivard
 */
public class PwmValueTag extends PwmAbstractTag
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

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    @Override
    protected String generateTagBodyContents( final PwmRequest pwmRequest )
    {
        final PwmValue value = getName();
        final String output = calcValue( pwmRequest, pageContext, value );
        return value.getFlags().contains( PwmValue.Flag.DoNotEscape )
                ? output
                : StringUtil.escapeHtml( output );
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
                LOGGER.error( () -> "error executing value tag option '" + value + "', error: " + e.getMessage() );
            }
        }

        return "";
    }
}
