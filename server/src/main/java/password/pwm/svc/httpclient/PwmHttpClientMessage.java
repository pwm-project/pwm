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

package password.pwm.svc.httpclient;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.http.HttpEntityDataType;
import password.pwm.http.HttpHeader;
import password.pwm.util.java.ImmutableByteArray;
import password.pwm.util.java.StringUtil;

import java.util.Map;
import java.util.Optional;

interface PwmHttpClientMessage
{
    int getRequestID();

    String getBody();

    Map<String, String> getHeaders();

    HttpEntityDataType getDataType();

    ImmutableByteArray getBinaryBody();

    default boolean isBinary()
    {
        return getDataType() == HttpEntityDataType.ByteArray;
    }

    default boolean hasBody()
    {
        return isBinary()
                ? getBinaryBody() != null && !getBinaryBody().isEmpty()
                : !StringUtil.isEmpty( getBody() );
    }

    static long sizeImpl( final PwmHttpClientMessage pwmHttpClientMessage )
    {
        long size = pwmHttpClientMessage.getBody() == null ? 0 : pwmHttpClientMessage.getBody().length();
        size += pwmHttpClientMessage.getBinaryBody() == null ? 0 : pwmHttpClientMessage.getBinaryBody().size();
        if ( pwmHttpClientMessage.getHeaders() != null )
        {
            size += pwmHttpClientMessage.getHeaders().entrySet().stream()
                    .map( e -> e.getValue().length() + e.getKey().length() )
                    .reduce( 0, Integer::sum );
        }
        return size;
    }

    static String entityToDebugString(
            final String topLine,
            final PwmApplication pwmApplication,
            final PwmHttpClientConfiguration pwmHttpClientConfiguration,
            final PwmHttpClientMessage pwmHttpClientMessage
    )
    {
        final boolean emptyBody = !pwmHttpClientMessage.hasBody();
        final StringBuilder msg = new StringBuilder();

        if ( topLine != null )
        {
            msg.append( topLine );
        }

        msg.append( " id=" ).append( pwmHttpClientMessage.getRequestID() ).append( ") " );

        if ( emptyBody )
        {
            msg.append( " (no body)" );
        }

        msg.append( outputHeadersToLog( pwmHttpClientMessage ) );

        if ( !emptyBody )
        {
            msg.append( outputBodyToLog( pwmHttpClientMessage, pwmApplication, pwmHttpClientConfiguration ) );
        }

        return msg.toString();
    }

    private static String outputHeadersToLog( final PwmHttpClientMessage pwmHttpClientMessage )
    {
        final Map<String, String> headers = pwmHttpClientMessage.getHeaders();

        if ( headers != null )
        {
            final StringBuilder msg = new StringBuilder();
            for ( final Map.Entry<String, String> headerEntry : headers.entrySet() )
            {
                msg.append( '\n' );
                final Optional<HttpHeader> httpHeader = HttpHeader.forHttpHeader( headerEntry.getKey() );
                if ( httpHeader.isPresent() )
                {
                    final boolean sensitive = httpHeader.get().isSensitive();
                    msg.append( "  header: " ).append( httpHeader.get().getHttpName() ).append( '=' );

                    if ( sensitive )
                    {
                        msg.append( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT );
                    }
                    else
                    {
                        msg.append( headerEntry.getValue() );
                    }
                }
                else
                {
                    // We encountered a header name that doesn't have a corresponding enum in HttpHeader,
                    // so we can't check the sensitive flag.
                    msg.append( "  header: " ).append( headerEntry.getKey() ).append( '=' ).append( headerEntry.getValue() );
                }
            }
            return msg.toString();
        }

        return "";
    }

    private static String outputBodyToLog(
            final PwmHttpClientMessage pwmHttpClientMessage,
            final PwmApplication pwmApplication,
            final PwmHttpClientConfiguration pwmHttpClientConfiguration
    )
    {
        final StringBuilder msg = new StringBuilder( "\n  body: " );

        if ( pwmHttpClientMessage.isBinary() )
        {
            final ImmutableByteArray binaryBody = pwmHttpClientMessage.getBinaryBody();
            if ( binaryBody != null && !binaryBody.isEmpty() )
            {
                msg.append( "[binary, " ).append( binaryBody.size() ).append( " bytes]" );
            }
            else
            {
                msg.append( "[no data]" );
            }
        }
        else
        {
            final String body = pwmHttpClientMessage.getBody();
            if ( StringUtil.isEmpty( body ) )
            {
                msg.append( "[no data]" );
            }
            else
            {
                msg.append( '[' ).append( body.length() ).append( " chars] " );

                final boolean alwaysOutput = pwmApplication.getConfig().readBooleanAppProperty( AppProperty.HTTP_CLIENT_ALWAYS_LOG_ENTITIES );
                if ( alwaysOutput || !pwmHttpClientConfiguration.isMaskBodyDebugOutput() )
                {
                    msg.append( body );
                }
                else
                {
                    msg.append( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT );
                }
            }
        }

        return msg.toString();
    }
}
