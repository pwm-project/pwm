/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.ws.server;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Config;
import password.pwm.i18n.Message;
import password.pwm.util.java.JsonUtil;

import java.io.Serializable;
import java.util.Locale;

@Value
@Builder( toBuilder =  true )
public class RestResultBean implements Serializable
{
    private boolean error;
    private int errorCode;
    private String errorMessage;
    private String errorDetail;
    private String successMessage;
    private Serializable data;

    public static RestResultBean withData( final Serializable data )
    {
        return RestResultBean.builder()
                .data( data )
                .build();
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation,
            final PwmApplication pwmApplication,
            final Locale locale,
            final Configuration config,
            final boolean forceDetail
    )
    {
        final String errorDetail =
                errorInformation != null
                        && ( forceDetail || pwmApplication != null && pwmApplication.determineIfDetailErrorMsgShown() )
                ? errorInformation.toDebugStr()
                : null;

        return RestResultBean.builder()
                .error( errorInformation != null )
                .errorMessage( errorInformation == null ? null : errorInformation.toUserStr( locale, config ) )
                .errorDetail( errorDetail )
                .errorCode( errorInformation == null ? 0 : errorInformation.getError().getErrorCode() )
                .build();
    }

    public static RestResultBean fromError(
            final RestRequest restRequestBean,
            final ErrorInformation errorInformation
    )
    {
        final PwmApplication pwmApplication = restRequestBean.getPwmApplication();
        final Configuration config = restRequestBean.getPwmApplication().getConfig();
        final Locale locale = restRequestBean.getLocale();
        return fromError( errorInformation, pwmApplication, locale, config, false );
    }

    public static RestResultBean fromErrorWithData(
            final RestRequest restRequestBean,
            final ErrorInformation errorInformation,
            final Serializable serializable
    )
    {
        final PwmApplication pwmApplication = restRequestBean.getPwmApplication();
        final Configuration config = restRequestBean.getPwmApplication().getConfig();
        final Locale locale = restRequestBean.getLocale();
        return fromError( errorInformation, pwmApplication, locale, config, false ).toBuilder().data( serializable ).build();
    }


    public static RestResultBean fromError(
            final ErrorInformation errorInformation
    )
    {
        return fromError( errorInformation, null, null, null, false );
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation,
            final boolean showDetails
    )
    {
        return fromError( errorInformation, null, null, null, showDetails );
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation,
            final PwmRequest pwmRequest,
            final boolean forceDetail
    )
    {
        return fromError( errorInformation, pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getConfig(), forceDetail );
    }

    public static RestResultBean fromError(
            final ErrorInformation errorInformation,
            final PwmRequest pwmRequest
    )
    {
        return fromError( errorInformation, pwmRequest.getPwmApplication(), pwmRequest.getLocale(), pwmRequest.getConfig(), false );
    }


    public static RestResultBean forSuccessMessage(
            final Serializable data,
            final Locale locale,
            final Configuration config,
            final Message message,
            final String... fieldValues

    )
    {
        final String msgText = Message.getLocalizedMessage( locale, message, config, fieldValues );
        return RestResultBean.builder()
                .successMessage( msgText )
                .data( data )
                .build();
    }

    public static RestResultBean forSuccessMessage(
            final Locale locale,
            final Configuration config,
            final Message message,
            final String... fieldValues

    )
    {
        final String msgText = Message.getLocalizedMessage( locale, message, config, fieldValues );
        return RestResultBean.builder()
                .successMessage( msgText )
                .build();
    }

    public static RestResultBean forSuccessMessage(
            final Serializable data,
            final PwmRequest pwmRequest,
            final Message message,
            final String... fieldValues
    )
    {
        return forSuccessMessage( data, pwmRequest.getLocale(), pwmRequest.getConfig(), message, fieldValues );
    }

    public static RestResultBean forSuccessMessage(
            final Serializable data,
            final RestRequest restRequest,
            final Message message,
            final String... fieldValues
    )
    {
        return forSuccessMessage( data, restRequest.getLocale(), restRequest.getConfig(), message, fieldValues );
    }

    public static RestResultBean forSuccessMessage(
            final RestRequest restRequest,
            final Message message,
            final String... fieldValues
    )
    {
        return forSuccessMessage( restRequest.getLocale(), restRequest.getConfig(), message, fieldValues );
    }

    public static RestResultBean forSuccessMessage(
            final PwmRequest pwmRequest,
            final Message message,
            final String... fieldValues
    )
    {
        return forSuccessMessage( pwmRequest.getLocale(), pwmRequest.getConfig(), message, fieldValues );
    }

    public static RestResultBean forConfirmMessage(
            final Locale locale,
            final Configuration config,
            final Config message
    )
    {
        final String msgText = Config.getLocalizedMessage( locale, message, config );
        return RestResultBean.builder()
            .successMessage( msgText )
            .build();
    }

    public static RestResultBean forConfirmMessage(
            final PwmRequest pwmRequest,
            final Config message
    )
    {
        return forConfirmMessage( pwmRequest.getLocale(), pwmRequest.getConfig(), message );
    }


    public String toJson( final boolean prettyPrintJson )
    {
        return prettyPrintJson
                ? JsonUtil.serialize( this, JsonUtil.Flag.PrettyPrint ) + "\n"
                : JsonUtil.serialize( this );
    }
}
