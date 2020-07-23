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

import password.pwm.http.JspUtility;
import password.pwm.http.PwmRequest;
import password.pwm.http.tag.url.PwmUrlTag;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.jsp.tagext.TagSupport;

public class PwmScriptRefTag extends TagSupport
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmScriptRefTag.class );

    private String url;

    public String getUrl( )
    {
        return url;
    }

    public void setUrl( final String url )
    {
        this.url = url;
    }

    public int doEndTag( )
            throws javax.servlet.jsp.JspTagException
    {
        try
        {
            final PwmRequest pwmRequest = JspUtility.getPwmRequest( pageContext );
            final String cspNonce = pwmRequest.getCspNonce();

            String url = getUrl();
            url = PwmUrlTag.convertUrl( url );
            url = PwmUrlTag.insertContext( pageContext, url );
            url = PwmUrlTag.insertResourceNonce( pwmRequest.getPwmApplication(), url );

            final String output = "<script type=\"text/javascript\" nonce=\"" + cspNonce + "\" src=\"" + url + "\"></script><noscript></noscript>";
            pageContext.getOut().write( output );
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error during scriptRef output of pwmFormID: " + e.getMessage() );
        }
        return EVAL_PAGE;
    }

}
