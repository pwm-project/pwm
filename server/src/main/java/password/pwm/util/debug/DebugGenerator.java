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

package password.pwm.util.debug;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.DomainConfig;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DebugGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DebugGenerator.class );

    private final PwmApplication pwmApplication;
    private final AppConfig obfuscatedAppConfig;
    private final SessionLabel sessionLabel;

    private static final Locale LOCALE = PwmConstants.DEFAULT_LOCALE;

    public DebugGenerator( final PwmApplication pwmApplication, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;

        final StoredConfiguration obfuscatedStoredConfig = StoredConfigurationUtil.copyConfigAndBlankAllPasswords( pwmApplication.getConfig().getStoredConfiguration() );
        this.obfuscatedAppConfig = AppConfig.forStoredConfig( obfuscatedStoredConfig );
    }

    private String getFilenameBase()
    {
        return PwmConstants.PWM_APP_NAME + "-Support";
    }

    public String getFilename()
    {
        return getFilenameBase() + ".zip";
    }

    public void outputZipDebugFile( final ZipOutputStream zipOutput )
            throws IOException
    {
        final String debugFileName = "zipDebugGeneration.csv";
        final Instant startTime = Instant.now();
        final DebugGeneratorLogger debugLogger = new DebugGeneratorLogger( sessionLabel );
        debugLogger.appendLine( "beginning debug output" );

        {
            final AppDebugItemRequest debugItemInput = new AppDebugItemRequest( pwmApplication, sessionLabel, this.obfuscatedAppConfig, LOCALE, debugLogger );
            final String pathPrefix = getFilenameBase() + "/";
            final List<AppItemGenerator> appItemGenerators = JavaHelper.instancesOfSealedInterface( AppItemGenerator.class );
            for ( final AppItemGenerator serviceClass : appItemGenerators )
            {
                executeAppDebugItem( debugItemInput, serviceClass, debugLogger, zipOutput, pathPrefix );
            }
        }

        {
            for ( final PwmDomain pwmDomain : pwmApplication.domains().values() )
            {
                final DomainConfig obfuscatedDomainConfig = this.obfuscatedAppConfig.getDomainConfigs().get( pwmDomain.getDomainID() );
                final DomainDebugItemRequest debugItemInput = new DomainDebugItemRequest( pwmDomain, sessionLabel, obfuscatedDomainConfig, LOCALE, debugLogger );
                final String pathPrefix = getFilenameBase() + "/" + pwmDomain.getDomainID() + "/";
                final List<DomainItemGenerator> domainItemGenerator = JavaHelper.instancesOfSealedInterface( DomainItemGenerator.class );
                for ( final DomainItemGenerator serviceClass : domainItemGenerator )
                {
                    executeDomainDebugItem( debugItemInput, serviceClass, debugLogger, zipOutput, pathPrefix );
                }
            }
        }

        {
            final String msg = "completed";
            debugLogger.appendLine( msg );
            LOGGER.trace( sessionLabel, () -> msg, TimeDuration.fromCurrent( startTime ) );
        }

        try
        {
            zipOutput.putNextEntry( new ZipEntry( getFilenameBase() + "/" + debugFileName ) );
            zipOutput.write( debugLogger.toString().getBytes( PwmConstants.DEFAULT_CHARSET ) );
            zipOutput.closeEntry();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error generating " + debugFileName + ": " + e.getMessage() );
        }

        zipOutput.flush();
    }

    private void executeAppDebugItem(
            final AppDebugItemRequest debugItemInput,
            final AppItemGenerator serviceClass,
            final DebugGeneratorLogger debugGeneratorLogFile,
            final ZipOutputStream zipOutput,
            final String pathPrefix
    )
    {
        try
        {
            final Instant itemStartTime = Instant.now();
            LOGGER.trace( sessionLabel, () -> "beginning output of item " + serviceClass.getClass().getSimpleName() );
            zipOutput.putNextEntry( new ZipEntry( pathPrefix + serviceClass.getFilename() ) );
            serviceClass.outputItem( debugItemInput, zipOutput );
            zipOutput.closeEntry();
            zipOutput.flush();
            final Supplier<String> finishMsg = () -> "completed output of " + serviceClass.getFilename();
            LOGGER.trace( sessionLabel, finishMsg, TimeDuration.fromCurrent( itemStartTime ) );
            debugGeneratorLogFile.appendLine( finishMsg.get() );
        }
        catch ( final Throwable e )
        {
            final Supplier<String> errorMsg = () -> "unexpected error executing debug item output class '" + serviceClass.getClass().getName() + "', error: " + e;
            LOGGER.error( sessionLabel, errorMsg, e );
            debugGeneratorLogFile.appendLine( errorMsg.get() );
            debugGeneratorLogFile.appendLine( JavaHelper.stackTraceToString( e ) );
        }
    }


    void executeDomainDebugItem(
            final DomainDebugItemRequest debugItemInput,
            final DomainItemGenerator serviceClass,
            final DebugGeneratorLogger debugGeneratorLogFile,
            final ZipOutputStream zipOutput,
            final String pathPrefix
    )
    {
        try
        {
            final Instant itemStartTime = Instant.now();
            LOGGER.trace( sessionLabel, () -> "beginning output of item " + serviceClass.getClass().getSimpleName() );
            zipOutput.putNextEntry( new ZipEntry( pathPrefix + serviceClass.getFilename() ) );
            serviceClass.outputItem( debugItemInput, zipOutput );
            zipOutput.closeEntry();
            zipOutput.flush();
            final Supplier<String> finishMsg = () -> "completed output of " + serviceClass.getFilename();
            LOGGER.trace( sessionLabel, finishMsg, TimeDuration.fromCurrent( itemStartTime ) );
            debugGeneratorLogFile.appendLine( finishMsg.get() );
        }
        catch ( final Throwable e )
        {
            final Supplier<String> errorMsg = () -> "unexpected error executing debug item output class '" + serviceClass.getClass().getName() + "', error: " + e;
            LOGGER.error( sessionLabel, errorMsg, e );
            debugGeneratorLogFile.appendLine( errorMsg.get() );
            debugGeneratorLogFile.appendLine( JavaHelper.stackTraceToString( e ) );
        }
    }

    static void writeString( final OutputStream outputStream, final String value )
    {
        try
        {
            outputStream.write( value.getBytes( StandardCharsets.UTF_8 ) );
        }
        catch ( final IOException e )
        {
            throw new PwmInternalException( "i/o error writing to zipOutputStream: " + e.getMessage() );
        }
    }
}
