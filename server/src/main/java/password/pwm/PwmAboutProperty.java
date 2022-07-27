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

import lombok.Value;
import password.pwm.config.PwmSetting;
import password.pwm.i18n.Display;
import password.pwm.ldap.LdapDomainService;
import password.pwm.svc.db.DatabaseService;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.net.ssl.SSLContext;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

public enum PwmAboutProperty
{
    app_version( "App Version", pwmApplication -> PwmConstants.SERVLET_VERSION ),
    app_chaiApiVersion( "App Chai Version", pwmApplication -> PwmConstants.CHAI_API_VERSION ),
    app_currentTime( "App Current Time", pwmApplication -> format( Instant.now() ) ),
    app_startTime( "App Startup Time", pwmApplication -> format( pwmApplication.getStartupTime() ) ),
    app_installTime( "App Install Time", pwmApplication -> format( pwmApplication.getInstallTime() ) ),
    app_siteUrl( "App Site URL", pwmApplication -> pwmApplication.getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL ) ),
    app_instanceID( "App InstanceID", PwmApplication::getInstanceID ),
    app_trialMode( null, pwmApplication -> Boolean.toString( PwmConstants.TRIAL_MODE ) ),
    app_deployment_type( null, pwmApplication -> pwmApplication.getPwmEnvironment().getDeploymentPlatform().name() ),
    app_mode_manageHttps( null, pwmApplication -> Boolean.toString( pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.ManageHttps ) ) ),
    app_applicationPath( null, pwmApplication -> pwmApplication.getPwmEnvironment().getApplicationPath().getAbsolutePath() ),
    app_environmentFlags( null, pwmApplication -> StringUtil.collectionToString( pwmApplication.getPwmEnvironment().getFlags() ) ),
    app_wordlistSize( null, pwmApplication -> Long.toString( pwmApplication.getWordlistService().size() ) ),
    app_sharedHistorySize( null, pwmApplication -> Long.toString( pwmApplication.getSharedHistoryManager().size() ) ),
    app_sharedHistoryOldestTime( null, pwmApplication -> format( pwmApplication.getSharedHistoryManager().getOldestEntryTime() ) ),
    app_emailQueueSize( null, pwmApplication -> Integer.toString( pwmApplication.getEmailQueue().queueSize() ) ),
    app_emailQueueOldestTime( null, pwmApplication -> format( pwmApplication.getEmailQueue().eldestItem() ) ),
    app_smsQueueSize( null, pwmApplication -> Integer.toString( pwmApplication.getSmsQueue().queueSize() ) ),
    app_smsQueueOldestTime( null, pwmApplication -> format( pwmApplication.getSmsQueue().eldestItem() ) ),
    app_syslogQueueSize( null, pwmApplication -> Integer.toString( pwmApplication.getAuditService().syslogQueueSize() ) ),
    app_localDbLogSize( null, pwmApplication -> Integer.toString( pwmApplication.getLocalDBLogger().getStoredEventCount() ) ),
    app_localDbLogOldestTime( null, pwmApplication -> format( pwmApplication.getLocalDBLogger().getTailDate().orElse( null ) ) ),
    app_localDbStorageSize( null, pwmApplication -> StringUtil.formatDiskSize( FileSystemUtility.getFileDirectorySize( pwmApplication.getLocalDB().getFileLocation() ) ) ),
    app_localDbFreeSpace( null, pwmApplication -> StringUtil.formatDiskSize( FileSystemUtility.diskSpaceRemaining( pwmApplication.getLocalDB().getFileLocation() ) ) ),
    app_configurationRestartCounter( null, pwmApplication -> Integer.toString( pwmApplication.getPwmEnvironment().getContextManager().getRestartCount() ) ),
    app_secureBlockAlgorithm( null, pwmApplication -> pwmApplication.getSecureService().getDefaultBlockAlgorithm().getLabel() ),
    app_secureHashAlgorithm( null, pwmApplication -> pwmApplication.getSecureService().getDefaultHashAlgorithm().toString() ),
    app_ldapProfileCount( null, pwmApplication -> Integer.toString( LdapDomainService.totalLdapProfileCount( pwmApplication ) ) ),
    app_ldapConnectionCount( null, pwmApplication -> Long.toString( LdapDomainService.totalLdapConnectionCount( pwmApplication ) ) ),
    app_activeSessionCount( "App Active Session Count", pwmApplication -> Integer.toString( pwmApplication.getSessionTrackService().sessionCount() ) ),
    app_activeRequestCount( "App Active Request Count", pwmApplication -> Integer.toString( pwmApplication.getTotalActiveServletRequests() ) ),
    app_definedDomainCount( "App Defined Domain Count", pwmApplication -> Integer.toString( pwmApplication.domains().size() ) ),

    build_Time( "Build Time", pwmApplication -> PwmConstants.BUILD_TIME ),
    build_Number( "Build Number", pwmApplication -> PwmConstants.BUILD_NUMBER ),
    build_Revision( "Build Revision", pwmApplication -> PwmConstants.BUILD_REVISION ),
    build_JavaVendor( "Build Java Vendor", pwmApplication -> PwmConstants.BUILD_JAVA_VENDOR ),
    build_JavaVersion( "Build Java Version", pwmApplication -> PwmConstants.BUILD_JAVA_VERSION ),
    build_Version( "Build Version", pwmApplication -> PwmConstants.BUILD_VERSION ),

    java_memoryFree( "Java Memory Free", pwmApplication -> Long.toString( Runtime.getRuntime().freeMemory() ) ),
    java_memoryAllocated( "Java Memory Allocated", pwmApplication -> Long.toString( Runtime.getRuntime().totalMemory() ) ),
    java_memoryMax( "Java Memory Max", pwmApplication -> Long.toString( Runtime.getRuntime().maxMemory() ) ),
    java_processors( "Java Available Processors", pwmApplication -> Integer.toString( Runtime.getRuntime().availableProcessors() ) ),
    java_threadCount( "Java Thread Count", pwmApplication -> Integer.toString( Thread.activeCount() ) ),
    java_runtimeVersion( "Java Runtime Version", pwmApplication -> System.getProperty( "java.runtime.version" ) ),
    java_vmName( "Java VM Name", pwmApplication -> System.getProperty( "java.vm.name" ) ),
    java_vmVendor( "Java VM Vendor", pwmApplication -> System.getProperty( "java.vm.vendor" ) ),
    java_vmLocation( "Java VM Location", pwmApplication -> System.getProperty( "java.home" ) ),
    java_vmVersion( "Java VM Version", pwmApplication -> System.getProperty( "java.vm.version" ) ),
    java_vmCommandLine( "Java VM Command Line", pwmApplication -> StringUtil.collectionToString( ManagementFactory.getRuntimeMXBean().getInputArguments() ) ),
    java_osName( "Operating System Name", pwmApplication -> System.getProperty( "os.name" ) ),
    java_osVersion( "Operating System Version", pwmApplication -> System.getProperty( "os.version" ) ),
    java_osArch( "Operating System Architecture", pwmApplication -> System.getProperty( "os.arch" ) ),
    java_randomAlgorithm( "Random Algorithm", pwmApplication -> pwmApplication.getSecureService().pwmRandom().getAlgorithm() ),
    java_defaultCharset( "Default Character Set", pwmApplication -> Charset.defaultCharset().name() ),
    java_appServerInfo( "Java AppServer Info", pwmApplication -> pwmApplication.getPwmEnvironment().getContextManager().getServerInfo() ),
    java_sslVersions( "Java SSL Versions", pwmApplication -> readSslVersions() ),
    java_gcName( "Java GC Name", pwmApplication -> readGcName() ),

    database_driverName( null,
            pwmApplication -> pwmApplication.getDatabaseService().getConnectionDebugProperties().get( DatabaseService.DatabaseAboutProperty.driverName ) ),
    database_driverVersion( null,
            pwmApplication -> pwmApplication.getDatabaseService().getConnectionDebugProperties().get( DatabaseService.DatabaseAboutProperty.driverVersion ) ),
    database_databaseProductName( null,
            pwmApplication -> pwmApplication.getDatabaseService().getConnectionDebugProperties().get( DatabaseService.DatabaseAboutProperty.databaseProductName ) ),
    database_databaseProductVersion( null,
            pwmApplication -> pwmApplication.getDatabaseService().getConnectionDebugProperties().get( DatabaseService.DatabaseAboutProperty.databaseProductVersion ) ),;

    private final String label;
    private final transient Function<PwmApplication, String> value;

    PwmAboutProperty( final String label, final Function<PwmApplication, String> value )
    {
        this.label = label;
        this.value = value;
    }

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmAboutProperty.class );

    @Value
    private static class Pair<K, V>
    {
        private final K key;
        private final V value;
    }

    public static Map<PwmAboutProperty, String> makeInfoBean(
            final PwmApplication pwmApplication
    )
    {
        return Collections.unmodifiableMap( CollectionUtil.enumStream( PwmAboutProperty.class )
                .map( aboutProp -> new Pair<>( aboutProp, readAboutValue( pwmApplication, aboutProp ) ) )
                .filter( entry -> entry.getValue().isPresent() )
                .collect( CollectionUtil.collectorToEnumMap( PwmAboutProperty.class,
                        Pair::getKey,
                        entry -> entry.getValue().get() ) ) );

    }

    private static Optional<String> readAboutValue( final PwmApplication pwmApplication, final PwmAboutProperty pwmAboutProperty )
    {
        final Function<PwmApplication, String> valueProvider = pwmAboutProperty.value;
        if ( valueProvider != null )
        {
            try
            {
                final String value = valueProvider.apply( pwmApplication );
                return Optional.ofNullable( value );
            }
            catch ( final Exception t )
            {
                LOGGER.trace( () -> "error generating about value for '" + pwmAboutProperty.name() + "', error: " + t.getMessage() );
                return Optional.of( LocaleHelper.getLocalizedMessage( null, Display.Value_NotApplicable, pwmApplication.getConfig() ) );
            }
        }

        return Optional.empty();
    }

    private static String format( final Instant date )
    {
        if ( date != null )
        {
            return date.toString();
        }
        else
        {
            return LocaleHelper.getLocalizedMessage( PwmConstants.DEFAULT_LOCALE, Display.Value_NotApplicable, null );
        }

    }

    public String getLabel( )
    {
        return label == null ? this.name() : label;
    }

    public static Map<String, String> toStringMap( final Map<PwmAboutProperty, String> infoBeanMap )
    {
        final Map<String, String> outputProps = new TreeMap<>( );
        for ( final Map.Entry<PwmAboutProperty, String> entry : infoBeanMap.entrySet() )
        {
            final PwmAboutProperty aboutProperty = entry.getKey();
            final String value = entry.getValue();
            outputProps.put( aboutProperty.toString().replace( '_', '.' ), value );
        }
        return Collections.unmodifiableMap( outputProps );
    }

    private static String readSslVersions()
    {
        try
        {
            return String.join( " ", SSLContext.getDefault().getSupportedSSLParameters().getProtocols() );
        }
        catch ( final NoSuchAlgorithmException e )
        {
            return "";
        }
    }

    private static String readGcName()
    {

        for ( final GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans() )
        {
            return bean.getName();
        }
        return LocaleHelper.valueNotApplicable( PwmConstants.DEFAULT_LOCALE );
    }
}
