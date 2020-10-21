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

package password.pwm;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

@Value
@Builder( toBuilder = true )
public class PwmEnvironment
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmEnvironment.class );

    @lombok.Builder.Default
    private PwmApplicationMode applicationMode = PwmApplicationMode.ERROR;

    private Configuration config;
    private File applicationPath;
    private boolean internalRuntimeInstance;
    private File configurationFile;
    private ContextManager contextManager;

    @Singular
    private Set<ApplicationFlag> flags;

    @Singular
    private Map<ApplicationParameter, String> parameters;

    public enum ApplicationParameter
    {
        AutoExportHttpsKeyStoreFile,
        AutoExportHttpsKeyStorePassword,
        AutoExportHttpsKeyStoreAlias,
        AutoWriteTomcatConfSourceFile,
        AutoWriteTomcatConfOutputFile,
        AppliancePort,
        ApplianceHostnameFile,
        ApplianceTokenFile,
        InstanceID,
        InitConsoleLogLevel,;

        public static ApplicationParameter forString( final String input )
        {
            return JavaHelper.readEnumFromString( ApplicationParameter.class, null, input );
        }
    }

    public enum ApplicationFlag
    {
        Appliance,
        Docker,
        ManageHttps,
        NoFileLock,
        CommandLineInstance,;

        public static ApplicationFlag forString( final String input )
        {
            return JavaHelper.readEnumFromString( ApplicationFlag.class, null, input );
        }
    }

    public enum EnvironmentParameter
    {
        applicationPath,
        applicationFlags,
        applicationParamFile,;

        public String conicalJavaOptionSystemName( )
        {
            return PwmConstants.PWM_APP_NAME.toLowerCase() + "." + this.toString();
        }

        public String conicalEnvironmentSystemName( )
        {
            return ( PwmConstants.PWM_APP_NAME.toLowerCase() + "_" + this.toString() ).toUpperCase();
        }

        public List<String> possibleNames( final String contextName )
        {
            final List<String> returnValues = new ArrayList<>();
            if ( contextName != null )
            {
                // java property format <app>.<context>.<paramName> like pwm.pwm.applicationFlag
                final String value = PwmConstants.PWM_APP_NAME.toLowerCase()
                        + "."
                        + contextName
                        + "."
                        + this.toString();
                returnValues.add( value );
                returnValues.add( value.toUpperCase() );
                returnValues.add( value.replace( ".", "_" ) );
                returnValues.add( value.toUpperCase().replace( ".", "_" ) );
            }
            {
                // java property format <app>.<paramName> like pwm.applicationFlag
                final String value = PwmConstants.PWM_APP_NAME.toLowerCase()
                        + "."
                        + this.toString();
                returnValues.add( value );
                returnValues.add( value.toUpperCase() );
                returnValues.add( value.replace( ".", "_" ) );
                returnValues.add( value.toUpperCase().replace( ".", "_" ) );
            }

            return Collections.unmodifiableList( returnValues );
        }
    }


    public void verifyIfApplicationPathIsSetProperly( )
            throws PwmUnrecoverableException
    {
        final File applicationPath = this.getApplicationPath();

        verifyApplicationPath( applicationPath );

        boolean applicationPathIsWebInfPath = false;
        if ( applicationPath.getAbsolutePath().endsWith( "/WEB-INF" ) )
        {
            final File webXmlFile = new File( applicationPath.getAbsolutePath() + File.separator + "web.xml" );
            if ( webXmlFile.exists() )
            {
                applicationPathIsWebInfPath = true;
            }
        }
        if ( applicationPathIsWebInfPath )
        {
            LOGGER.trace( () -> "applicationPath appears to be servlet /WEB-INF directory" );
        }
    }

    public PwmEnvironment makeRuntimeInstance(
            final Configuration configuration
    )
            throws PwmUnrecoverableException
    {
        return this.toBuilder()
                .applicationMode( PwmApplicationMode.READ_ONLY )
                .internalRuntimeInstance( true )
                .configurationFile( null )
                .config( configuration )
                .build();
    }


    public static void verifyApplicationPath( final File applicationPath ) throws PwmUnrecoverableException
    {

        if ( applicationPath == null )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                            "unable to determine valid applicationPath" )
            );
        }

        LOGGER.trace( () -> "examining applicationPath of " + applicationPath.getAbsolutePath() + "" );

        if ( !applicationPath.exists() )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                            "applicationPath " + applicationPath.getAbsolutePath() + " does not exist" )
            );
        }

        if ( !applicationPath.canRead() )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                            "unable to read from applicationPath " + applicationPath.getAbsolutePath() + "" )
            );
        }

        if ( !applicationPath.canWrite() )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                            "unable to write to applicationPath " + applicationPath.getAbsolutePath() + "" )
            );
        }

        final File infoFile = new File( applicationPath.getAbsolutePath() + File.separator + PwmConstants.APPLICATION_PATH_INFO_FILE );
        LOGGER.trace( () -> "checking " + infoFile.getAbsolutePath() + " status" );
        if ( infoFile.exists() )
        {
            final String errorMsg = "The file " + infoFile.getAbsolutePath() + " exists, and an applicationPath was not explicitly specified."
                    + "  This happens when an applicationPath was previously configured, but is not now being specified."
                    + "  An explicit applicationPath parameter must be specified, or the file can be removed if the applicationPath"
                    + " should be changed to the default /WEB-INF directory.";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg ) );
        }

    }

    public static class ParseHelper
    {
        public static Set<ApplicationFlag> readApplicationFlagsFromSystem( final String contextName )
        {
            final String rawValue = readValueFromSystem( EnvironmentParameter.applicationFlags, contextName );
            if ( rawValue != null )
            {
                return parseApplicationFlagValueParameter( rawValue );
            }
            return Collections.emptySet();
        }

        public static Map<ApplicationParameter, String> readApplicationParmsFromSystem( final String contextName )
        {
            final String rawValue = readValueFromSystem( EnvironmentParameter.applicationParamFile, contextName );
            if ( rawValue != null )
            {
                return readAppParametersFromPath( rawValue );
            }
            return Collections.emptyMap();
        }

        public static String readValueFromSystem( final PwmEnvironment.EnvironmentParameter parameter, final String contextName )
        {
            final List<String> namePossibilities = parameter.possibleNames( contextName );

            for ( final String propertyName : namePossibilities )
            {
                final String propValue = System.getProperty( propertyName );
                if ( propValue != null && !propValue.isEmpty() )
                {
                    return propValue;
                }
            }

            for ( final String propertyName : namePossibilities )
            {
                final String propValue = System.getenv( propertyName );
                if ( propValue != null && !propValue.isEmpty() )
                {
                    return propValue;
                }
            }

            return null;
        }

        public static Set<ApplicationFlag> parseApplicationFlagValueParameter( final String input )
        {
            if ( input == null )
            {
                return Collections.emptySet();
            }

            try
            {
                final List<String> jsonValues = JsonUtil.deserializeStringList( input );
                final Set<ApplicationFlag> returnFlags = JavaHelper.readEnumSetFromStringCollection( ApplicationFlag.class, jsonValues );
                return Collections.unmodifiableSet( returnFlags );
            }
            catch ( final Exception e )
            {
                //
            }

            final Set<ApplicationFlag> returnFlags = EnumSet.noneOf( ApplicationFlag.class );
            for ( final String value : input.split( "," ) )
            {
                final ApplicationFlag flag = ApplicationFlag.forString( value );
                if ( value != null )
                {
                    returnFlags.add( flag );
                }
                else
                {
                    LOGGER.warn( () -> "unknown " + EnvironmentParameter.applicationFlags.toString() + " value: " + input );
                }
            }
            return returnFlags;
        }

        public static Map<ApplicationParameter, String> readAppParametersFromPath( final String input )
        {
            if ( input == null )
            {
                return Collections.emptyMap();
            }

            final Properties propValues = new Properties();
            try ( FileInputStream fileInputStream = new FileInputStream( new File( input ) ) )
            {
                propValues.load( fileInputStream );
            }
            catch ( final Exception e )
            {
                LOGGER.warn( () -> "error reading properties file '" + input + "' specified by environment setting "
                        + EnvironmentParameter.applicationParamFile.toString() + ", error: " + e.getMessage() );
            }

            try
            {
                final Map<ApplicationParameter, String> returnParams = new EnumMap<>( ApplicationParameter.class );
                for ( final Object key : propValues.keySet() )
                {

                    final ApplicationParameter param = ApplicationParameter.forString( key.toString() );
                    if ( param != null )
                    {
                        returnParams.put( param, propValues.getProperty( key.toString() ) );
                    }
                    else
                    {
                        LOGGER.warn( () -> "unknown " + EnvironmentParameter.applicationParamFile.toString() + " value: " + input );
                    }
                }
                return Collections.unmodifiableMap( returnParams );
            }
            catch ( final Exception e )
            {
                LOGGER.warn( () -> "unable to parse jason value of " + EnvironmentParameter.applicationParamFile.toString() + ", error: " + e.getMessage() );
            }

            return Collections.emptyMap();
        }
    }

    public static PwmApplicationMode checkForTrial( final PwmApplicationMode mode )
    {
        if ( PwmConstants.TRIAL_MODE && mode == PwmApplicationMode.RUNNING )
        {
            LOGGER.info( () -> "application is in trial mode" );
            return PwmApplicationMode.CONFIGURATION;
        }

        return mode;
    }
}
