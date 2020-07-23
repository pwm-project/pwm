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

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspException;
import javax.servlet.jsp.tagext.BodyContent;
import javax.servlet.jsp.tagext.BodyTagSupport;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PwmScriptTag extends BodyTagSupport
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmScriptTag.class );

    // match start and end <script> tags
    private static final Pattern SCRIPT_TAG_PATTERN = Pattern.compile( "<\\s*script.*?>|<\\s*\\/\\s*script\\s*.*?>" );

    public int doStartTag( )
            throws JspException
    {
        return EVAL_BODY_BUFFERED;
    }

    public int doAfterBody( )
    {
        try
        {
            final PwmRequest pwmRequest = PwmRequest.forRequest( ( HttpServletRequest ) pageContext.getRequest(), ( HttpServletResponse ) pageContext.getResponse() );
            final BodyContent bc = getBodyContent();
            if ( bc != null )
            {
                final String tagBody = bc.getString();
                final String strippedTagBody = stripHtmlScriptTags( tagBody );
                final String output = "<script type=\"text/javascript\" nonce=\"" + pwmRequest.getCspNonce() + "\">"
                        + strippedTagBody
                        + "</script><noscript></noscript>";
                getPreviousOut().write( output );
            }
        }
        catch ( final IOException e )
        {
            LOGGER.error( () -> "IO error while processing PwmScriptTag: " + e.getMessage() );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( () -> "error while processing PwmScriptTag: " + e.getMessage() );
        }
        return SKIP_BODY;
    }

    private static String stripHtmlScriptTags( final String input )
    {
        if ( input == null )
        {
            return null;
        }

        final Matcher matcher = SCRIPT_TAG_PATTERN.matcher( input );
        return matcher.replaceAll( "" );
    }
}
