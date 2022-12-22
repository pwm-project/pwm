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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.Builder;
import lombok.Value;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.ServletContext;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

@Value
@Builder( toBuilder = true )
public class PwmEnvironment
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmEnvironment.class );

    private static final SessionLabel SESSION_LABEL = SessionLabel.SYSTEM_LABEL;

    @lombok.Builder.Default
    private PwmApplicationMode applicationMode = PwmApplicationMode.ERROR;

    private AppConfig config;
    private File applicationPath;
    private boolean internalRuntimeInstance;
    private File configurationFile;
    private ContextManager contextManager;

    private final Supplier<Map<EnvironmentProperty, String>> parameters = LazySupplier.create( this::readApplicationParams );

    private final LazySupplier<DeploymentPlatform> deploymentPlatformLazySupplier
            = LazySupplier.create( this::determineDeploymentPlatform );


    public enum DeploymentPlatform
    {
        War,
        Onejar,
        Docker,
        Appliance,;
    }


    public void verifyIfApplicationPathIsSetProperly()
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
            LOGGER.trace( SESSION_LABEL, () -> "applicationPath appears to be servlet /WEB-INF directory" );
        }
    }

    public Map<EnvironmentProperty, String> readProperties()
    {
        return parameters.get();
    }

    public Optional<String> readProperty( final EnvironmentProperty environmentParameter )
    {
        return Optional.ofNullable( parameters.get().get( environmentParameter ) );
    }

    public boolean readPropertyAsBoolean( final EnvironmentProperty environmentParameter )
    {
        return Boolean.parseBoolean( parameters.get().get( environmentParameter ) );
    }

    public PwmEnvironment makeRuntimeInstance(
            final AppConfig appConfig
    )
    {
        return this.toBuilder()
                .applicationMode( PwmApplicationMode.READ_ONLY )
                .internalRuntimeInstance( true )
                .configurationFile( null )
                .config( appConfig )
                .build();
    }


    public static void verifyApplicationPath( final File applicationPath )
            throws PwmUnrecoverableException
    {

        if ( applicationPath == null )
        {
            throw new PwmUnrecoverableException(
                    new ErrorInformation( PwmError.ERROR_STARTUP_ERROR,
                            "unable to determine valid applicationPath" )
            );
        }

        LOGGER.trace( SESSION_LABEL, () -> "examining applicationPath of " + applicationPath.getAbsolutePath() + "" );

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
        LOGGER.trace( SESSION_LABEL, () -> "checking " + infoFile.getAbsolutePath() + " status" );
        if ( infoFile.exists() )
        {
            final String errorMsg = "The file " + infoFile.getAbsolutePath() + " exists, and an applicationPath was not explicitly specified."
                    + "  This happens when an applicationPath was previously configured, but is not now being specified."
                    + "  An explicit applicationPath parameter must be specified, or the file can be removed if the applicationPath"
                    + " should be changed to the default /WEB-INF directory.";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_STARTUP_ERROR, errorMsg ) );
        }

    }

    public static PwmApplicationMode checkForTrial( final PwmApplicationMode mode )
    {
        if ( PwmConstants.TRIAL_MODE && mode == PwmApplicationMode.RUNNING )
        {
            LOGGER.info( SESSION_LABEL, () -> "application is in trial mode" );
            return PwmApplicationMode.CONFIGURATION;
        }

        return mode;
    }

    public DeploymentPlatform getDeploymentPlatform()
    {
        return deploymentPlatformLazySupplier.get();
    }

    @SuppressFBWarnings( "DMI_HARDCODED_ABSOLUTE_FILENAME" )
    private DeploymentPlatform determineDeploymentPlatform()
    {
        if ( Files.exists( Path.of( "/.dockerenv" ) ) )
        {
            return DeploymentPlatform.Docker;
        }

        if ( readPropertyAsBoolean( EnvironmentProperty.OnejarInstance ) )
        {
            return DeploymentPlatform.Onejar;
        }
        return DeploymentPlatform.War;
    }

    private Map<EnvironmentProperty, String> readApplicationParams()
    {
        final Path applicationPath = this.applicationPath == null ? null : this.applicationPath.toPath();
        final ServletContext effectiveContext = this.contextManager == null
                ? null
                : this.contextManager.getServletContext();

        return EnvironmentProperty.readApplicationParams( applicationPath, effectiveContext );
    }
}

