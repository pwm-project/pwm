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

import password.pwm.AppProperty;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

/**
 * @author Jason D. Rivard
 */
public class ErrorMessageTag extends PwmAbstractTag
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ErrorMessageTag.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    @Override
    protected String generateTagBodyContents( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final ErrorInformation error = ( ErrorInformation ) pwmRequest.getAttribute( PwmRequestAttribute.PwmErrorInfo );

        if ( error == null )
        {
            return "";
        }

        final boolean allowHtml = Boolean.parseBoolean(
                pwmRequest.getDomainConfig().readAppProperty( AppProperty.HTTP_ERRORS_ALLOW_HTML ) );

        final boolean showErrorDetail = pwmRequest.getPwmDomain().determineIfDetailErrorMsgShown();

        String outputMsg = error.toUserStr( pwmRequest.getLocale(), pwmRequest.getDomainConfig() );
        if ( !allowHtml )
        {
            outputMsg = StringUtil.escapeHtml( outputMsg );
        }

        if ( showErrorDetail )
        {
            final String errorDetail = error.toDebugStr() == null
                    ? ""
                    : " { " + error.toDebugStr() + " }";

            // detail should always be escaped - it may contain untrusted data
            outputMsg += "<span class='errorDetail'>"
                    + StringUtil.escapeHtml( errorDetail )
                    + "</span>";
        }

        outputMsg = outputMsg.replace( "\n", "<br/>" );

        final MacroRequest macroRequest = pwmRequest.getMacroMachine( );
        outputMsg = macroRequest.expandMacros( outputMsg );

        return outputMsg;
    }
}
