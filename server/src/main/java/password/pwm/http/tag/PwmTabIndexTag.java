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

import password.pwm.http.JspUtility;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;

public class PwmTabIndexTag extends TagSupport
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmTabIndexTag.class );

    @Override
    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        try
        {
            final PwmRequest pwmRequest = JspUtility.getPwmRequest( pageContext );
            final Integer currentCounter = (Integer) pwmRequest.getAttribute( PwmRequestAttribute.JspIndexTabCounter );
            final int nextCounter = currentCounter == null ? 1 : currentCounter + 1;
            pageContext.getOut().write( String.valueOf( nextCounter ) );
            pwmRequest.setAttribute( PwmRequestAttribute.JspIndexTabCounter, nextCounter );
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
            LOGGER.error( () -> "error during PwmTabIndexTag output: " + e.getMessage(), e );
        }
        return EVAL_PAGE;
    }
}
