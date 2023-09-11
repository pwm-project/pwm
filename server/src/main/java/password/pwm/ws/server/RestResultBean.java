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

package password.pwm.ws.server;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import password.pwm.PwmDomain;
import password.pwm.config.DomainConfig;
import password.pwm.error.ErrorInformation;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Config;
import password.pwm.i18n.Message;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;

import java.lang.reflect.Type;
import java.util.Locale;

@Value
@Builder( toBuilder =  true, access = AccessLevel.PACKAGE )
public class RestResultBean<T>
{
    private final T data;
    private final boolean error;
    private final int errorCode;
    private final String errorMessage;
    private final String errorDetail;
    private final String successMessage;

    private final transient Class<T> classOfT;

    public static <T> RestResultBean<T> withData( final T data, final Class<T> classOfT )
    {
        return RestResultBean.<T>builder()
                .data( data )
                .classOfT( classOfT )
                .build();
    }

    public static <T> RestResultBean<T> fromError(
            final ErrorInformation errorInformation,
            final PwmDomain pwmDomain,
            final Locale locale,
            final DomainConfig config,
            final boolean forceDetail
    )
    {
        final String errorDetail =
                errorInformation != null
                        && ( forceDetail || pwmDomain != null && pwmDomain.determineIfDetailErrorMsgShown() )
                        ? errorInformation.toDebugStr()
                        : null;

        return RestResultBean.<T>builder()
                .error( errorInformation != null )
                .errorMessage( errorInformation == null ? null : errorInformation.toUserStr( locale, config ) )
                .errorDetail( errorDetail )
                .errorCode( errorInformation == null ? 0 : errorInformation.getError().getErrorCode() )
                .build();
    }

    public static <T> RestResultBean<T> fromError(
            final RestRequest restRequestBean,
            final ErrorInformation errorInformation
    )
    {
        final PwmDomain pwmDomain = restRequestBean.getDomain();
        final DomainConfig config = restRequestBean.getDomain().getConfig();
        final Locale locale = restRequestBean.getLocale();
        return fromError( errorInformation, pwmDomain, locale, config, false );
    }

    public static <T> RestResultBean<T> fromErrorWithData(
            final RestRequest restRequestBean,
            final ErrorInformation errorInformation,
            final T serializable
    )
    {
        final PwmDomain pwmDomain = restRequestBean.getDomain();
        final DomainConfig config = restRequestBean.getDomain().getConfig();
        final Locale locale = restRequestBean.getLocale();
        final RestResultBean<T> tempBean = fromError( errorInformation, pwmDomain, locale, config, false );
        return tempBean.toBuilder().data( serializable ).build();
    }


    public static <T> RestResultBean<T> fromError(
            final ErrorInformation errorInformation
    )
    {
        return fromError( errorInformation, null, null, null, false );
    }

    public static <T> RestResultBean<T> fromError(
            final ErrorInformation errorInformation,
            final boolean showDetails
    )
    {
        return fromError( errorInformation, null, null, null, showDetails );
    }

    public static <T> RestResultBean<T> fromError(
            final ErrorInformation errorInformation,
            final PwmRequest pwmRequest,
            final boolean forceDetail
    )
    {
        return fromError( errorInformation, pwmRequest.getPwmDomain(), pwmRequest.getLocale(), pwmRequest.getDomainConfig(), forceDetail );
    }

    public static RestResultBean<?> fromError(
            final ErrorInformation errorInformation,
            final PwmRequest pwmRequest
    )
    {
        return fromError( errorInformation, pwmRequest.getPwmDomain(), pwmRequest.getLocale(), pwmRequest.getDomainConfig(), false );
    }


    public static <T> RestResultBean<T> forSuccessMessage(
            final T data,
            final Locale locale,
            final DomainConfig config,
            final Message message,
            final String... fieldValues

    )
    {
        final String msgText = Message.getLocalizedMessage( locale, message, config, fieldValues );
        return RestResultBean.<T>builder()
                .successMessage( msgText )
                .data( data )
                .build();
    }

    public static <T> RestResultBean<T> forSuccessMessage(
            final Locale locale,
            final DomainConfig config,
            final Message message,
            final String... fieldValues

    )
    {
        final String msgText = Message.getLocalizedMessage( locale, message, config, fieldValues );
        return RestResultBean.<T>builder()
                .successMessage( msgText )
                .build();
    }

    public static <T> RestResultBean<T> forSuccessMessage(
            final T data,
            final PwmRequest pwmRequest,
            final Message message,
            final String... fieldValues
    )
    {
        return forSuccessMessage( data, pwmRequest.getLocale(), pwmRequest.getDomainConfig(), message, fieldValues );
    }

    public static <T> RestResultBean<T> forSuccessMessage(
            final T data,
            final RestRequest restRequest,
            final Message message,
            final String... fieldValues
    )
    {
        return forSuccessMessage( data, restRequest.getLocale(), restRequest.getDomain().getConfig(), message, fieldValues );
    }

    public static <T> RestResultBean<T> forSuccessMessage(
            final RestRequest restRequest,
            final Message message,
            final String... fieldValues
    )
    {
        return forSuccessMessage( restRequest.getLocale(), restRequest.getDomain().getConfig(), message, fieldValues );
    }

    public static <T> RestResultBean<T> forSuccessMessage(
            final PwmRequest pwmRequest,
            final Message message,
            final String... fieldValues
    )
    {
        return forSuccessMessage( pwmRequest.getLocale(), pwmRequest.getDomainConfig(), message, fieldValues );
    }

    public static <T> RestResultBean<T> forConfirmMessage(
            final Locale locale,
            final DomainConfig config,
            final Config message
    )
    {
        final String msgText = Config.getLocalizedMessage( locale, message, config );
        return RestResultBean.<T>builder()
                .successMessage( msgText )
                .build();
    }

    public static <T> RestResultBean<T> forConfirmMessage(
            final PwmRequest pwmRequest,
            final Config message
    )
    {
        return forConfirmMessage( pwmRequest.getLocale(), pwmRequest.getDomainConfig(), message );
    }


    public String toJson( final boolean prettyPrintJson )
    {
        final Type innerType;
        if ( data == null )
        {
            innerType = Object.class;
        }
        else if ( classOfT != null )
        {
            innerType = classOfT;
        }
        else
        {
            innerType = data.getClass();
        }

        return prettyPrintJson
                ? JsonFactory.get().serialize( this, RestResultBean.class, innerType, JsonProvider.Flag.PrettyPrint ) + "\n"
                : JsonFactory.get().serialize( this, RestResultBean.class, innerType );
    }
}
