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

package password.pwm.util.macro;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmEnvironment;
import password.pwm.config.PwmSetting;
import password.pwm.http.ContextManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmDateFormat;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SystemMacros
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SystemMacros.class );

    static final List<Macro> SYSTEM_MACROS = Collections.unmodifiableList( Stream.of(
            new CurrentTimeMacro(),
            new Iso8601DateTimeMacro(),
            new InstanceIDMacro(),
            new DefaultEmailFromAddressMacro(),
            new SiteURLMacro(),
            new SiteHostMacro(),
            new RandomCharMacro(),
            new RandomNumberMacro(),
            new UUIDMacro(),
            new PwmContextPath()
    ).collect( Collectors.toList() ) );

    public abstract static class AbstractSystemMacros extends AbstractMacro
    {
        @Override
        public Scope getScope()
        {
            return Scope.System;
        }
    }


    public static class InstanceIDMacro extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@InstanceID@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request
        )
        {
            final PwmApplication pwmApplication = request.getPwmApplication();

            if ( pwmApplication == null )
            {
                LOGGER.error( request.getSessionLabel(),  () -> "could not replace value for '" + matchValue + "', pwmApplication is null" );
                return "";
            }

            return pwmApplication.getInstanceID();
        }
    }

    public static class CurrentTimeMacro extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@CurrentTime" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request

        )
                throws MacroParseException
        {
            final List<String> parameters = splitMacroParameters( matchValue, Collections.singletonList( "CurrentTime" ) );

            if ( parameters.size() > 2 )
            {
                throw new MacroParseException( "too many parameters" );
            }

            final PwmDateFormat pwmDateFormat = readDateFormatAndTimeZoneParams( parameters );

            try
            {
                return pwmDateFormat.format( Instant.now() );
            }
            catch ( final IllegalArgumentException e )
            {
                throw new MacroParseException( e.getMessage() );
            }
        }
    }

    public static class Iso8601DateTimeMacro extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@Iso8601" + PATTERN_OPTIONAL_PARAMETER_MATCH + "@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request

        )
                throws MacroParseException
        {
            final List<String> parameters = splitMacroParameters( matchValue, Collections.singletonList( "Iso8601" ) );

            if ( JavaHelper.isEmpty(  parameters ) || parameters.size() != 1 )
            {
                throw new MacroParseException( "exactly one parameter is required" );
            }

            final String param = parameters.get( 0 );

            if ( "DateTime".equalsIgnoreCase( param ) )
            {
                return JavaHelper.toIsoDate( Instant.now() );
            }
            else if ( "Date".equalsIgnoreCase( param ) )
            {
                return Instant.now().atOffset( ZoneOffset.UTC ).format( DateTimeFormatter.ofPattern( "uuuu-MM-dd" ) );
            }
            else if ( "Time".equalsIgnoreCase( param ) )
            {
                return Instant.now().atOffset( ZoneOffset.UTC ).format( DateTimeFormatter.ofPattern( "HH:mm:ss" ) );
            }

            throw new MacroParseException( "unknown parameter" );
        }
    }

    public static class SiteURLMacro extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@SiteURL@|@Site:URL@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request
        )
        {
            return request.getPwmApplication().getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL );
        }
    }

    public static class DefaultEmailFromAddressMacro extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@DefaultEmailFromAddress@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request
        )
        {
            return request.getPwmApplication().getConfig().readSettingAsString( PwmSetting.EMAIL_DEFAULT_FROM_ADDRESS );
        }
    }

    public static class SiteHostMacro extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@SiteHost@|@Site:Host@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request
        )
        {
            try
            {
                final String siteUrl = request.getPwmApplication().getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL );
                final URL url = new URL( siteUrl );
                return url.getHost();
            }
            catch ( final MalformedURLException e )
            {
                LOGGER.error( () -> "unable to parse configured/detected site URL: " + e.getMessage() );
            }
            return "";
        }
    }

    public static class RandomCharMacro extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@RandomChar(:[^@]*)?@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request
        )
                throws MacroParseException
        {
            if ( matchValue == null || matchValue.length() < 1 )
            {
                return "";
            }

            final List<String> parameters = splitMacroParameters( matchValue, Collections.singletonList( "RandomChar" ) );
            int length = 1;
            if ( parameters.size() > 0 && !parameters.get( 0 ).isEmpty() )
            {
                final int maxLengthPermitted = Integer.parseInt( request.getPwmApplication().getConfig().readAppProperty( AppProperty.MACRO_RANDOM_CHAR_MAX_LENGTH ) );
                try
                {
                    length = Integer.parseInt( parameters.get( 0 ) );
                    if ( length > maxLengthPermitted )
                    {
                        throw new MacroParseException( "maximum permitted length of RandomChar (" + maxLengthPermitted + ") exceeded" );
                    }
                    else if ( length <= 0 )
                    {
                        throw new MacroParseException( "length of RandomChar (" + maxLengthPermitted + ") must be greater than zero" );
                    }
                }
                catch ( final NumberFormatException e )
                {
                    throw new MacroParseException( "error parsing length parameter of RandomChar: " + e.getMessage() );
                }
            }

            if ( parameters.size() > 1 && !parameters.get( 1 ).isEmpty() )
            {
                final String chars = parameters.get( 1 );
                return PwmRandom.getInstance().alphaNumericString( chars, length );
            }
            else
            {
                return PwmRandom.getInstance().alphaNumericString( length );
            }
        }
    }

    public static class RandomNumberMacro extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@RandomNumber(:[^@]*)?@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request
        )
                throws MacroParseException
        {
            if ( matchValue == null || matchValue.length() < 1 )
            {
                return "";
            }

            final List<String> parameters = splitMacroParameters( matchValue, Collections.singletonList( "RandomNumber" ) );
            if ( parameters.size() != 2 )
            {
                throw new MacroParseException( "incorrect number of parameter of RandomNumber: "
                        + parameters.size() + ", should be 2" );
            }

            final int min;
            final int max;
            try
            {
                min = Integer.parseInt( parameters.get( 0 ) );
            }
            catch ( final NumberFormatException e )
            {
                throw new MacroParseException( "error parsing minimum value parameter of RandomNumber: " + e.getMessage() );
            }

            try
            {
                max = Integer.parseInt( parameters.get( 1 ) );
            }
            catch ( final NumberFormatException e )
            {
                throw new MacroParseException( "error parsing maximum value parameter of RandomNumber: " + e.getMessage() );
            }

            if ( min > max )
            {
                throw new MacroParseException( "minimum value is less than maximum value parameter of RandomNumber" );
            }

            final int range = max - min;
            return String.valueOf( PwmRandom.getInstance().nextInt( range ) + min );
        }
    }

    public static class UUIDMacro extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@UUID@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue(
                final String matchValue,
                final MacroRequest request
        )
        {
            return PwmRandom.getInstance().randomUUID().toString();
        }
    }

    public static class PwmContextPath extends AbstractSystemMacros
    {
        private static final Pattern PATTERN = Pattern.compile( "@PwmContextPath@" );

        @Override
        public Pattern getRegExPattern( )
        {
            return PATTERN;
        }

        @Override
        public String replaceValue( final String matchValue, final MacroRequest request )
                throws MacroParseException
        {
            String contextName = "[context]";
            final PwmApplication pwmApplication = request.getPwmApplication();
            if ( pwmApplication != null )
            {
                final PwmEnvironment pwmEnvironment = pwmApplication.getPwmEnvironment();
                if ( pwmEnvironment != null )
                {
                    final ContextManager contextManager = pwmEnvironment.getContextManager();
                    if ( contextManager != null && contextManager.getContextPath() != null )
                    {
                        contextName = contextManager.getContextPath();
                    }
                }
            }
            return contextName;
        }
    }
}
