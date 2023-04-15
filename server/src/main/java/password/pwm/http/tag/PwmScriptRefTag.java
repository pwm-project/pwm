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

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.tag.url.PwmUrlTag;
import password.pwm.util.logging.PwmLogger;

public class PwmScriptRefTag extends PwmAbstractTag
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

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    @Override
    protected String generateTagBodyContents( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final String cspNonce = pwmRequest.getCspNonce();

        String url = getUrl();
        url = PwmUrlTag.convertUrl( url );
        url = PwmUrlTag.insertContext( pageContext, url );
        url = PwmUrlTag.insertResourceNonce( pwmRequest.getPwmDomain(), url );

        return "<script type=\"text/javascript\" nonce=\""
                + cspNonce
                + "\" src=\""
                + url
                + "\"></script><noscript></noscript>";
    }
}
