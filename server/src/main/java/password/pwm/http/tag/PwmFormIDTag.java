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

import password.pwm.bean.FormNonce;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.state.SessionStateService;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;

public class PwmFormIDTag extends PwmAbstractTag
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmFormIDTag.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    @Override
    protected String generateTagBodyContents( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        return buildPwmFormID( pwmRequest );
    }

    private static String buildPwmFormID( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        final SessionStateService sessionStateService = pwmRequest.getPwmDomain().getSessionStateService();
        final String value = sessionStateService.getSessionStateInfo( pwmRequest );
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final FormNonce formID = new FormNonce(
                pwmSession.getLoginInfoBean().getGuid(),
                Instant.now(),
                pwmSession.getLoginInfoBean().getReqCounter(),
                value
        );
        return pwmRequest.getPwmDomain().getSecureService().encryptObjectToString( formID );
    }
}
