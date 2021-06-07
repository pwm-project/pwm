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
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.DebugOutputBuilder;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DebugItemGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( DebugItemGenerator.class );

    private static final List<Class<? extends AppItemGenerator>> APP_ITEM_GENERATORS = List.of(
            ConfigurationFileItemGenerator.class,
            ConfigurationDebugJsonItemGenerator.class,
            ConfigurationDebugTextItemGenerator.class,
            AboutItemGenerator.class,
            SystemEnvironmentItemGenerator.class,
            AppPropertiesItemGenerator.class,
            ServicesDebugItemGenerator.class,
            HealthDebugItemGenerator.class,
            ThreadDumpDebugItemGenerator.class,
            FileInfoDebugItemGenerator.class,
            IntruderDataGenerator.class,
            LogDebugItemGenerator.class,
            LogJsonItemGenerator.class,
            LocalDBDebugGenerator.class,
            SessionDataGenerator.class,
            ClusterInfoDebugGenerator.class,
            CacheServiceDebugItemGenerator.class,
            RootFileSystemDebugItemGenerator.class,
            StatisticsDataDebugItemGenerator.class,
            StatisticsEpsDataDebugItemGenerator.class,
            BuildInformationDebugItemGenerator.class );

    private static final List<Class<? extends DomainItemGenerator>> DOMAIN_ITEM_GENERATORS = List.of(
            LDAPPermissionItemGenerator.class,
            LdapDebugItemGenerator.class,
            LdapRecentUserDebugGenerator.class,
            DashboardDataDebugItemGenerator.class,
            LdapConnectionsDebugItemGenerator.class );

    private final PwmApplication pwmApplication;
    private final AppConfig obfuscatedAppConfig;
    private final SessionLabel sessionLabel;

    private static final Locale LOCALE = PwmConstants.DEFAULT_LOCALE;

    public DebugItemGenerator( final PwmApplication pwmApplication, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;
        this.sessionLabel = sessionLabel;

        final StoredConfiguration obfuscatedStoredConfig = StoredConfigurationUtil.copyConfigAndBlankAllPasswords( pwmApplication.getConfig().getStoredConfiguration() );
        this.obfuscatedAppConfig = new AppConfig( obfuscatedStoredConfig );
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
        final DebugOutputBuilder debugGeneratorLogFile = new DebugOutputBuilder();
        debugGeneratorLogFile.appendLine( "beginning debug output" );

        {
            final AppDebugItemInput debugItemInput = new AppDebugItemInput( pwmApplication, sessionLabel, this.obfuscatedAppConfig, LOCALE );
            final String pathPrefix = getFilenameBase() + "/";
            for ( final Class<? extends AppItemGenerator> serviceClass : APP_ITEM_GENERATORS )
            {
                executeAppDebugItem( debugItemInput, serviceClass, debugGeneratorLogFile, zipOutput, pathPrefix );
            }
        }

        {
            for ( final PwmDomain pwmDomain : pwmApplication.domains().values() )
            {
                final DomainConfig obfuscatedDomainConfig = this.obfuscatedAppConfig.getDomainConfigs().get( pwmDomain.getDomainID() );
                final DomainDebugItemInput debugItemInput = new DomainDebugItemInput( pwmDomain, sessionLabel, obfuscatedDomainConfig, LOCALE );
                final String pathPrefix = getFilenameBase() + "/" + pwmDomain.getDomainID() + "/";
                for ( final Class<? extends DomainItemGenerator> serviceClass : DOMAIN_ITEM_GENERATORS )
                {
                    executeDomainDebugItem( debugItemInput, serviceClass, debugGeneratorLogFile, zipOutput, pathPrefix );
                }
            }
        }

        {
            final String msg = "completed";
            debugGeneratorLogFile.appendLine( msg );
            LOGGER.trace( sessionLabel, () -> msg, () ->  TimeDuration.fromCurrent( startTime ) );
        }

        try
        {
            zipOutput.putNextEntry( new ZipEntry( getFilenameBase() + "/" + debugFileName ) );
            zipOutput.write( debugGeneratorLogFile.toString().getBytes( PwmConstants.DEFAULT_CHARSET ) );
            zipOutput.closeEntry();
        }
        catch ( final Exception e )
        {
            LOGGER.error( () -> "error generating " + debugFileName + ": " + e.getMessage() );
        }

        zipOutput.flush();
    }

    private void executeAppDebugItem(
            final AppDebugItemInput debugItemInput,
            final Class<? extends AppItemGenerator> serviceClass,
            final DebugOutputBuilder debugGeneratorLogFile,
            final ZipOutputStream zipOutput,
            final String pathPrefix
    )
    {
        try
        {
            final Instant itemStartTime = Instant.now();
            LOGGER.trace( sessionLabel, () -> "beginning output of item " + serviceClass.getSimpleName() );
            final AppItemGenerator newAppItemGeneratorItem = serviceClass.getDeclaredConstructor().newInstance();
            zipOutput.putNextEntry( new ZipEntry( pathPrefix + newAppItemGeneratorItem.getFilename() ) );
            newAppItemGeneratorItem.outputItem( debugItemInput, zipOutput );
            zipOutput.closeEntry();
            zipOutput.flush();
            final String finishMsg = "completed output of " + newAppItemGeneratorItem.getFilename()
                    + " in " + TimeDuration.fromCurrent( itemStartTime ).asCompactString();
            LOGGER.trace( sessionLabel, () -> finishMsg );
            debugGeneratorLogFile.appendLine( finishMsg );
        }
        catch ( final Throwable e )
        {
            final String errorMsg = "unexpected error executing debug item output class '" + serviceClass.getName() + "', error: " + e.toString();
            LOGGER.error( sessionLabel, () -> errorMsg, e );
            debugGeneratorLogFile.appendLine( errorMsg );
            final Writer stackTraceOutput = new StringWriter();
            e.printStackTrace( new PrintWriter( stackTraceOutput ) );
            debugGeneratorLogFile.appendLine( stackTraceOutput.toString() );
        }
    }


    void executeDomainDebugItem(
            final DomainDebugItemInput debugItemInput,
            final Class<? extends DomainItemGenerator> serviceClass,
            final DebugOutputBuilder debugGeneratorLogFile,
            final ZipOutputStream zipOutput,
            final String pathPrefix
    )
    {
        try
        {
            final Instant itemStartTime = Instant.now();
            LOGGER.trace( sessionLabel, () -> "beginning output of item " + serviceClass.getSimpleName() );
            final DomainItemGenerator newAppItemGeneratorItem = serviceClass.getDeclaredConstructor().newInstance();
            zipOutput.putNextEntry( new ZipEntry( pathPrefix + newAppItemGeneratorItem.getFilename() ) );
            newAppItemGeneratorItem.outputItem( debugItemInput, zipOutput );
            zipOutput.closeEntry();
            zipOutput.flush();
            final String finishMsg = "completed output of " + newAppItemGeneratorItem.getFilename()
                    + " in " + TimeDuration.fromCurrent( itemStartTime ).asCompactString();
            LOGGER.trace( sessionLabel, () -> finishMsg );
            debugGeneratorLogFile.appendLine( finishMsg );
        }
        catch ( final Throwable e )
        {
            final String errorMsg = "unexpected error executing debug item output class '" + serviceClass.getName() + "', error: " + e.toString();
            LOGGER.error( sessionLabel, () -> errorMsg, e );
            debugGeneratorLogFile.appendLine( errorMsg );
            final Writer stackTraceOutput = new StringWriter();
            e.printStackTrace( new PrintWriter( stackTraceOutput ) );
            debugGeneratorLogFile.appendLine( stackTraceOutput.toString() );
        }

    }


}
