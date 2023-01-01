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

package password.pwm;

import password.pwm.http.ContextManager;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletContext;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

public enum EnvironmentProperty
{
    applicationPath,
    AutoExportHttpsKeyStoreFile,
    AutoExportHttpsKeyStorePassword,
    AutoExportHttpsKeyStoreAlias,
    AutoWriteTomcatConfSourceFile,
    AutoWriteTomcatConfOutputFile,
    AppliancePort,
    ApplianceHostnameFile,
    ApplianceTokenFile,
    InstanceID,
    InitConsoleLogLevel,
    ManageHttps,
    NoFileLock,
    CommandLineInstance,
    OnejarInstance,;

    private static final PwmLogger LOGGER = PwmLogger.forClass( EnvironmentProperty.class );

    public String conicalJavaOptionSystemName( final String contextName )
    {
        return effectiveContextName( contextName ) + "." + this.name();
    }

    public String conicalEnvironmentSystemName( final String contextName )
    {
        return ( effectiveContextName( contextName ) + "_" + this.name() ).toUpperCase();
    }

    public List<String> possibleNames( final String contextName )
    {
        return List.of(
                conicalEnvironmentSystemName( contextName ),
                conicalEnvironmentSystemName( contextName ).toLowerCase(),
                conicalEnvironmentSystemName( contextName ).toUpperCase(),
                conicalJavaOptionSystemName( contextName ),
                conicalJavaOptionSystemName( contextName ).toLowerCase(),
                conicalJavaOptionSystemName( contextName ).toUpperCase() );
    }

    private static String effectiveContextName( final String contextName )
    {
        return StringUtil.isEmpty( contextName ) ? PwmConstants.PWM_APP_NAME.toLowerCase() : contextName;
    }

    public static Optional<Path> readApplicationPath(
            final ServletContext servletContext
    )
    {
        final String effectiveContext = servletContext == null
                ? PwmConstants.PWM_APP_NAME.toLowerCase()
                : servletContext.getContextPath().replace( "/", "" );
        final Map<EnvironmentProperty, String> tempMap = new EnumMap<>( EnvironmentProperty.class );
        tempMap.putAll( ParameterReader.readEnvironmentFromSystemEnv( effectiveContext ) );
        tempMap.putAll( ParameterReader.readEnvironmentFromSystemProperties( effectiveContext ) );
        tempMap.putAll( ParameterReader.readEnvironmentFromContext( servletContext ) );

        final String value = tempMap.get( EnvironmentProperty.applicationPath );
        if ( !StringUtil.isTrimEmpty( value ) )
        {
            return Optional.of( Path.of( value ) );
        }

        return Optional.empty();
    }

    public static Map<EnvironmentProperty, String> readApplicationParams(
            final Path applicationPath,
            final ServletContext servletContext
    )
    {
        final String effectiveContextName = servletContext == null
                ? PwmConstants.PWM_APP_NAME.toLowerCase()
                : servletContext.getContextPath().replace( "/", "" );

        final Map<EnvironmentProperty, String> resultMap = new EnumMap<>( EnvironmentProperty.class );
        resultMap.putAll( ParameterReader.readEnvironmentFromContext( servletContext ) );
        resultMap.putAll( ParameterReader.readEnvironmentFromSystemEnv( effectiveContextName ) );
        resultMap.putAll( ParameterReader.readEnvironmentFromSystemProperties( effectiveContextName ) );
        resultMap.putAll( ParameterReader.readEnvironmentFromEnvPropFile( applicationPath ) );
        return Collections.unmodifiableMap( resultMap );
    }

    private static class ParameterReader
    {
        private static Map<EnvironmentProperty, String> readEnvironmentFromContext( final ServletContext servletContext )
        {
            if ( servletContext == null )
            {
                return Collections.emptyMap();
            }

            final Map<String, String> stringMap = Collections.list( servletContext.getInitParameterNames() )
                    .stream()
                    .collect( Collectors.toMap(
                            Function.identity(),
                            servletContext::getInitParameter ) );

            return readEnvironmentFromStringMap( stringMap, servletContext.getContextPath() );
        }

        private static Map<EnvironmentProperty, String> readEnvironmentFromSystemEnv( final String context )
        {
            if ( context == null )
            {
                return Collections.emptyMap();
            }

            return readEnvironmentFromStringMap( System.getenv(), context );
        }

        private static Map<EnvironmentProperty, String> readEnvironmentFromEnvPropFile( final Path appPath )
        {
            if ( appPath == null )
            {
                return Collections.emptyMap();
            }

            final Path propFilePath = appPath.resolve( PwmConstants.DEFAULT_ENVIRONMENT_PROPERTIES_FILENAME );

            if ( !Files.exists( propFilePath ) )
            {
                return Collections.emptyMap();
            }

            try
            {
                try ( InputStream inputStream = Files.newInputStream( propFilePath ) )
                {
                    final Properties properties = new Properties();
                    properties.load( inputStream );
                    final Map<String, String> stringMap = properties.entrySet().stream()
                            .filter( entry -> entry.getKey() != null && entry.getValue() != null )
                            .collect( Collectors.toMap(
                                    entry -> entry.getKey().toString(),
                                    entry -> entry.getValue().toString() ) );


                    return readEnvironmentFromStringMap( stringMap, null );
                }
            }
            catch ( final IOException e )
            {
                LOGGER.warn( () -> "unexpected error while reading " + PwmConstants.DEFAULT_ENVIRONMENT_PROPERTIES_FILENAME + ", error: " + e.getMessage(), e );
                return Collections.emptyMap();
            }

        }

        private static Map<EnvironmentProperty, String> readEnvironmentFromSystemProperties( final String context )
        {
            final Map<String, String> stringMap = System.getProperties().entrySet()
                    .stream()
                    .collect( Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            entry -> entry.getValue().toString() ) );

            return readEnvironmentFromStringMap( stringMap, context );
        }

        private static Map<EnvironmentProperty, String> readEnvironmentFromStringMap(
                final Map<String, String> input,
                final String context
        )
        {

            final String normalizedContext = context == null ? null : context.replace( "/", "" );

            final Map<EnvironmentProperty, String> returnObj = new EnumMap<>( EnvironmentProperty.class );

            for ( final EnvironmentProperty environmentParameter : EnvironmentProperty.values() )
            {
                if ( context == null )
                {
                    final String value = input.get( environmentParameter.name() );
                    if ( !StringUtil.isTrimEmpty( value ) && !ContextManager.UNSPECIFIED_VALUE.equalsIgnoreCase( value ) )
                    {
                        returnObj.put( environmentParameter, value );
                    }
                }
                else
                {
                    possibleNameLoop:
                    for ( final String possibleName : environmentParameter.possibleNames( normalizedContext ) )
                    {
                        final String value = input.get( possibleName );
                        if ( !StringUtil.isTrimEmpty( value ) && !ContextManager.UNSPECIFIED_VALUE.equalsIgnoreCase( value ) )
                        {
                            returnObj.put( environmentParameter, value );
                            break possibleNameLoop;
                        }
                    }
                }
            }

            return Collections.unmodifiableMap( returnObj );
        }
    }
}
