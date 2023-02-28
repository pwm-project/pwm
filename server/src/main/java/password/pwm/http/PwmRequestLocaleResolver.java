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

package password.pwm.http;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.DomainProperty;
import password.pwm.PwmConstants;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

class PwmRequestLocaleResolver
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmRequestLocaleResolver.class );

    private static final List<RequestLocaleReader> REQUEST_LOCALE_READERS = List.of(
            new RequestParamReader(),
            new CookieReader(),
            new HeaderReader() );

    private interface RequestLocaleReader
    {
        Optional<ResolvedLocale> readLocale( PwmRequest pwmRequest );
    }

    @Value
    private static class ResolvedLocale
    {
        private final Locale locale;
        private final String sourceName;
    }

    static Locale resolveRequestLocale( final PwmRequest pwmRequest )
    {
        final ResolvedLocale discoveredLocale = REQUEST_LOCALE_READERS.stream()
                .map( requestLocaleReader -> requestLocaleReader.readLocale( pwmRequest ) )
                .flatMap( Optional::stream )
                .findFirst()
                .orElse( new ResolvedLocale( PwmConstants.DEFAULT_LOCALE, "application default" ) );


        return discoveredLocale.getLocale();
    }

    private static Optional<Locale> resolveInputLocaleValueStr( final AppConfig appConfig, final String incomingLocaleStr )
    {
        if ( "default".equalsIgnoreCase( incomingLocaleStr ) )
        {
            return Optional.of( PwmConstants.DEFAULT_LOCALE );
        }

        final List<Locale> knownLocales = appConfig.getKnownLocales();
        final Locale requestedLocale = LocaleHelper.parseLocaleString( incomingLocaleStr );
        if ( knownLocales.contains( requestedLocale ) )
        {
            return Optional.of( requestedLocale );
        }

        return Optional.empty();
    }

    private static String readLocaleCookieName( final PwmRequest pwmRequest )
    {
        return pwmRequest.getDomainConfig().readDomainProperty( DomainProperty.HTTP_COOKIE_LOCALE_NAME );
    }

    private static class RequestParamReader implements RequestLocaleReader
    {
        @Override
        public Optional<ResolvedLocale> readLocale( final PwmRequest pwmRequest )
        {
            final DomainConfig domainConfig = pwmRequest.getDomainConfig();
            final String localeParamName = domainConfig.readAppProperty( AppProperty.HTTP_PARAM_NAME_LOCALE );

            if ( !StringUtil.isEmpty( localeParamName ) )
            {
                try
                {
                    final String paramLocaleValueStr = pwmRequest.readParameterAsString( localeParamName );
                    if ( !StringUtil.isEmpty( paramLocaleValueStr ) )
                    {
                        final Optional<Locale> requestedParamLocale = resolveInputLocaleValueStr( domainConfig.getAppConfig(), paramLocaleValueStr );
                        if ( requestedParamLocale.isPresent() )
                        {
                            writeLocaleToCookie( pwmRequest, paramLocaleValueStr );
                            return Optional.of( new ResolvedLocale( requestedParamLocale.get(), "http parameter '"
                                    + localeParamName + "'" ) );
                        }
                        else
                        {
                            //LOGGER.debug( pwmRequest, () -> "ignoring http request parameter '" + localeParamName + "', value does not resolve to known locale" );
                        }
                    }
                }
                catch ( final PwmUnrecoverableException e )
                {
                    //LOGGER.trace( pwmRequest, () -> "error reading http locale request parameter: " + e.getMessage() );
                }
            }

            return Optional.empty();
        }

        private void writeLocaleToCookie( final PwmRequest pwmRequest, final String localeStr )
        {
            final int cookieAgeSeconds = ( int ) pwmRequest.getAppConfig().readSettingAsLong( PwmSetting.LOCALE_COOKIE_MAX_AGE );
            if ( cookieAgeSeconds > 0 )
            {
                final String localeCookieName = readLocaleCookieName( pwmRequest );
                if ( !StringUtil.isEmpty( localeCookieName ) )
                {
                    try
                    {
                        pwmRequest.getPwmResponse().writeCookie(
                                localeCookieName,
                                localeStr,
                                cookieAgeSeconds,
                                PwmCookiePath.Domain
                        );
                    }
                    catch ( final PwmUnrecoverableException e )
                    {
                        //LOGGER.error( pwmRequest, () -> "error writing http locale request param to cookie: " + e.getMessage() );
                    }
                }
            }
        }
    }

    private static class CookieReader implements RequestLocaleReader
    {
        @Override
        public Optional<ResolvedLocale> readLocale( final PwmRequest pwmRequest )
        {
            final String localeCookieName = readLocaleCookieName( pwmRequest );
            final Optional<String> localeCookie = pwmRequest.readCookie( localeCookieName );
            if ( localeCookie.isPresent() )
            {
                final Optional<Locale> requestedCookieLocale = resolveInputLocaleValueStr( pwmRequest.getAppConfig(), localeCookie.get() );
                if ( requestedCookieLocale.isPresent() )
                {
                    return Optional.of( new ResolvedLocale( requestedCookieLocale.get(),
                            "http " + HttpHeader.SetCookie + " '" + localeCookieName + "' header" ) );
                }
                else
                {
                    //LOGGER.debug( pwmRequest, () -> "ignoring cookie locale value, value does not resolve to known locale" );
                }
            }

            return Optional.empty();
        }
    }

    private static class HeaderReader implements RequestLocaleReader
    {
        @Override
        public Optional<ResolvedLocale> readLocale( final PwmRequest pwmRequest )
        {
            final Enumeration<Locale> requestLocales = pwmRequest.getHttpServletRequest().getLocales();
            while ( requestLocales.hasMoreElements() )
            {
                final Locale nextRequestLocale = requestLocales.nextElement();
                final Optional<Locale> resolvedLocale = resolveInputLocaleValueStr( pwmRequest.getAppConfig(), nextRequestLocale.toString() );
                if ( resolvedLocale.isPresent() )
                {
                    return Optional.of( new ResolvedLocale( resolvedLocale.get(), "http " + HttpHeader.AcceptLanguage + " header" ) );
                }
                else
                {
                    //LOGGER.debug( pwmRequest, () -> "ignoring unknown locale value set in http request for locale '" + nextRequestLocale + "'" );
                }
            }

            return Optional.empty();
        }
    }
}
