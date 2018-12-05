/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm;

import password.pwm.config.PwmSetting;
import password.pwm.i18n.Display;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.db.DatabaseService;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.lang.management.ManagementFactory;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public enum PwmAboutProperty
{

    app_version( null, pwmApplication -> PwmConstants.SERVLET_VERSION ),
    app_chaiApiVersion( null, pwmApplication -> PwmConstants.CHAI_API_VERSION ),
    app_currentTime( null, pwmApplication -> format( Instant.now() ) ),
    app_startTime( null, pwmApplication -> format( pwmApplication.getStartupTime() ) ),
    app_installTime( null, pwmApplication -> format( pwmApplication.getInstallTime() ) ),
    app_siteUrl( null, pwmApplication -> pwmApplication.getConfig().readSettingAsString( PwmSetting.PWM_SITE_URL ) ),
    app_instanceID( null, PwmApplication::getInstanceID ),
    app_trialMode( null, pwmApplication -> Boolean.toString( PwmConstants.TRIAL_MODE ) ),
    app_mode_appliance( null, pwmApplication -> Boolean.toString( pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.Appliance ) ) ),
    app_mode_docker( null, pwmApplication -> Boolean.toString( pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.Docker ) ) ),
    app_mode_manageHttps( null, pwmApplication -> Boolean.toString( pwmApplication.getPwmEnvironment().getFlags().contains( PwmEnvironment.ApplicationFlag.ManageHttps ) ) ),
    app_applicationPath( null, pwmApplication -> pwmApplication.getPwmEnvironment().getApplicationPath().getAbsolutePath() ),
    app_environmentFlags( null, pwmApplication -> StringUtil.collectionToString( pwmApplication.getPwmEnvironment().getFlags() ) ),
    app_wordlistSize( null, pwmApplication -> Long.toString( pwmApplication.getWordlistManager().size() ) ),
    app_seedlistSize( null, pwmApplication -> Long.toString( pwmApplication.getSeedlistManager().size() ) ),
    app_sharedHistorySize( null, pwmApplication -> Long.toString( pwmApplication.getSharedHistoryManager().size() ) ),
    app_sharedHistoryOldestTime( null, pwmApplication -> format( pwmApplication.getSharedHistoryManager().getOldestEntryTime() ) ),
    app_emailQueueSize( null, pwmApplication -> Integer.toString( pwmApplication.getEmailQueue().queueSize() ) ),
    app_emailQueueOldestTime( null, pwmApplication -> format( Date.from( pwmApplication.getEmailQueue().eldestItem() ) ) ),
    app_smsQueueSize( null, pwmApplication -> Integer.toString( pwmApplication.getSmsQueue().queueSize() ) ),
    app_smsQueueOldestTime( null, pwmApplication -> format( Date.from( pwmApplication.getSmsQueue().eldestItem() ) ) ),
    app_syslogQueueSize( null, pwmApplication -> Integer.toString( pwmApplication.getAuditManager().syslogQueueSize() ) ),
    app_localDbLogSize( null, pwmApplication -> Integer.toString( pwmApplication.getLocalDBLogger().getStoredEventCount() ) ),
    app_localDbLogOldestTime( null, pwmApplication -> format( pwmApplication.getLocalDBLogger().getTailDate() ) ),
    app_localDbStorageSize( null, pwmApplication -> StringUtil.formatDiskSize( FileSystemUtility.getFileDirectorySize( pwmApplication.getLocalDB().getFileLocation() ) ) ),
    app_localDbFreeSpace( null, pwmApplication -> StringUtil.formatDiskSize( FileSystemUtility.diskSpaceRemaining( pwmApplication.getLocalDB().getFileLocation() ) ) ),
    app_configurationRestartCounter( null, pwmApplication -> Integer.toString( pwmApplication.getPwmEnvironment().getContextManager().getRestartCount() ) ),
    app_secureBlockAlgorithm( null, pwmApplication -> pwmApplication.getSecureService().getDefaultBlockAlgorithm().getLabel() ),
    app_secureHashAlgorithm( null, pwmApplication -> pwmApplication.getSecureService().getDefaultHashAlgorithm().toString() ),
    app_ldapProfileCount( null, pwmApplication -> Integer.toString( pwmApplication.getConfig().getLdapProfiles().size() ) ),

    build_Time( "Build Time", pwmApplication -> PwmConstants.BUILD_TIME ),
    build_Number( "Build Number", pwmApplication -> PwmConstants.BUILD_NUMBER ),
    build_Revision( "Build Revision", pwmApplication -> PwmConstants.BUILD_REVISION ),
    build_JavaVendor( "Build Java Vendor", pwmApplication -> PwmConstants.BUILD_JAVA_VENDOR ),
    build_JavaVersion( "Build Java Version", pwmApplication -> PwmConstants.BUILD_JAVA_VERSION ),
    build_Version( "Build Version", pwmApplication -> PwmConstants.BUILD_VERSION ),

    java_memoryFree( "Java Memory Free", pwmApplication -> Long.toString( Runtime.getRuntime().freeMemory() ) ),
    java_memoryAllocated( "Java Memory Allocated", pwmApplication -> Long.toString( Runtime.getRuntime().totalMemory() ) ),
    java_memoryMax( "Java Memory Max", pwmApplication -> Long.toString( Runtime.getRuntime().maxMemory() ) ),
    java_threadCount( "Java Thread Count", pwmApplication -> Integer.toString( Thread.activeCount() ) ),
    java_runtimeVersion( "Java Runtime Version", pwmApplication -> System.getProperty( "java.runtime.version" ) ),
    java_vmName( "Java VM Name", pwmApplication -> System.getProperty( "java.vm.name" ) ),
    java_vmVendor( "Java VM Vendor", pwmApplication -> System.getProperty( "java.vm.vendor" ) ),
    java_vmLocation( "Java VM Location", pwmApplication -> System.getProperty( "java.home" ) ),
    java_vmVersion( "Java VM Version", pwmApplication -> System.getProperty( "java.vm.version" ) ),
    java_vmCommandLine( "Java VM Command Line", pwmApplication -> StringUtil.collectionToString( ManagementFactory.getRuntimeMXBean().getInputArguments() ) ),
    java_osName( "Java OS Name", pwmApplication -> System.getProperty( "os.name" ) ),
    java_osVersion( "Java OS Version", pwmApplication -> System.getProperty( "os.version" ) ),
    java_osArch( "Java OS Architecture", pwmApplication -> System.getProperty( "os.arch" ) ),
    java_randomAlgorithm( null, pwmApplication -> pwmApplication.getSecureService().pwmRandom().getAlgorithm() ),
    java_defaultCharset( null, pwmApplication -> Charset.defaultCharset().name() ),
    java_appServerInfo( "Java AppServer Info", pwmApplication -> pwmApplication.getPwmEnvironment().getContextManager().getServerInfo() ),

    database_driverName( null,
            pwmApplication -> pwmApplication.getDatabaseService().getConnectionDebugProperties().get( DatabaseService.DatabaseAboutProperty.driverName ) ),
    database_driverVersion( null,
            pwmApplication -> pwmApplication.getDatabaseService().getConnectionDebugProperties().get( DatabaseService.DatabaseAboutProperty.driverVersion ) ),
    database_databaseProductName( null,
            pwmApplication -> pwmApplication.getDatabaseService().getConnectionDebugProperties().get( DatabaseService.DatabaseAboutProperty.databaseProductName ) ),
    database_databaseProductVersion( null,
            pwmApplication -> pwmApplication.getDatabaseService().getConnectionDebugProperties().get( DatabaseService.DatabaseAboutProperty.databaseProductVersion ) ),;

    private final String label;
    private final ValueProvider valueProvider;

    PwmAboutProperty( final String label, final ValueProvider valueProvider )
    {
        this.label = label;
        this.valueProvider = valueProvider;
    }

    private interface ValueProvider
    {
        String value( PwmApplication pwmApplication );
    }

    private static final PwmLogger LOGGER = PwmLogger.forClass( PwmAboutProperty.class );

    public static Map<PwmAboutProperty, String> makeInfoBean(
            final PwmApplication pwmApplication
    )
    {
        final Map<String, String> aboutMap = new TreeMap<>();

        for ( final PwmAboutProperty pwmAboutProperty : PwmAboutProperty.values() )
        {
            final ValueProvider valueProvider = pwmAboutProperty.valueProvider;
            if ( valueProvider != null )
            {
                try
                {
                    final String value = valueProvider.value( pwmApplication );
                    aboutMap.put( pwmAboutProperty.name(), value == null ? "" : value );
                }
                catch ( Throwable t )
                {
                    aboutMap.put( pwmAboutProperty.name(), LocaleHelper.getLocalizedMessage( null, Display.Value_NotApplicable, null ) );
                    LOGGER.trace( () -> "error generating about value for '" + pwmAboutProperty.name() + "', error: " + t.getMessage() );
                }
            }
        }

        final Map<PwmAboutProperty, String> returnMap = new LinkedHashMap<>();
        for ( final Map.Entry<String, String> entry : aboutMap.entrySet() )
        {
            returnMap.put( PwmAboutProperty.valueOf( entry.getKey() ), entry.getValue() );
        }

        return Collections.unmodifiableMap( returnMap );
    }

    private static String format( final Date date )
    {
        return format( date == null ? null : date.toInstant() );
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
}
