/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.svc.telemetry;

import com.novell.ldapchai.provider.DirectoryVendor;
import lombok.Builder;
import lombok.Getter;
import password.pwm.AppAttribute;
import password.pwm.AppProperty;
import password.pwm.PwmAboutProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TelemetryPublishBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.PwmLdapVendor;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsBundle;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PwmScheduler;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.PwmRandom;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

public class TelemetryService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( TelemetryService.class );

    private ExecutorService executorService;
    private PwmApplication pwmApplication;
    private Settings settings;

    private Instant lastPublishTime;
    private ErrorInformation lastError;
    private TelemetrySender sender;

    private STATUS status = STATUS.NEW;


    @Override
    public STATUS status( )
    {
        return status;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;

        if ( pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING )
        {
            LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "will remain closed, app is not running" );
            status = STATUS.CLOSED;
            return;
        }

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PUBLISH_STATS_ENABLE ) )
        {
            LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "will remain closed, publish stats not enabled" );
            status = STATUS.CLOSED;
            return;
        }

        if ( pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "will remain closed, localdb not enabled" );
            status = STATUS.CLOSED;
            return;
        }

        if ( pwmApplication.getStatisticsManager().status() != STATUS.OPEN )
        {
            LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "will remain closed, statistics manager is not enabled" );
            status = STATUS.CLOSED;
            return;
        }

        settings = Settings.fromConfig( pwmApplication.getConfig() );
        try
        {
            initSender();
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "will remain closed, unable to init sender: " + e.getMessage() );
            status = STATUS.CLOSED;
            return;
        }

        {
            final Instant storedLastPublishTimestamp = pwmApplication.readAppAttribute( AppAttribute.TELEMETRY_LAST_PUBLISH_TIMESTAMP, Instant.class );
            lastPublishTime = storedLastPublishTimestamp != null
                    ? storedLastPublishTimestamp
                    : pwmApplication.getInstallTime();
            LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "last publish time was " + JavaHelper.toIsoDate( lastPublishTime ) );
        }

        executorService = PwmScheduler.makeBackgroundExecutor( pwmApplication, TelemetryService.class );

        scheduleNextJob();

        status = STATUS.OPEN;
    }

    private void initSender( ) throws PwmUnrecoverableException
    {
        if ( StringUtil.isEmpty( settings.getSenderImplementation() ) )
        {
            final String msg = "telemetry sender implementation not specified";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TELEMETRY_SEND_ERROR, msg ) );
        }

        final TelemetrySender telemetrySender;
        try
        {
            final String senderClass = settings.getSenderImplementation();
            final Class theClass = Class.forName( senderClass );
            telemetrySender = ( TelemetrySender ) theClass.newInstance();
        }
        catch ( final Exception e )
        {
            final String msg = "unable to load implementation class: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ) );
        }

        try
        {
            final String macrodSettings = MacroMachine.forNonUserSpecific( pwmApplication, null ).expandMacros( settings.getSenderSettings() );
            telemetrySender.init( pwmApplication, macrodSettings );
        }
        catch ( final Exception e )
        {
            final String msg = "unable to init implementation class: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ) );
        }
        sender = telemetrySender;
    }

    private void executePublishJob( ) throws PwmUnrecoverableException, IOException, URISyntaxException
    {
        final String authValue = pwmApplication.getStatisticsManager().getStatBundleForKey( StatisticsManager.KEY_CUMULATIVE ).getStatistic( Statistic.AUTHENTICATIONS );
        if ( StringUtil.isEmpty( authValue ) || Integer.parseInt( authValue ) < settings.getMinimumAuthentications() )
        {
            LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "skipping telemetry send, authentication count is too low" );
        }
        else
        {
            try
            {
                final TelemetryPublishBean telemetryPublishBean = generatePublishableBean();
                sender.publish( telemetryPublishBean );
                LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "sent telemetry data: " + JsonUtil.serialize( telemetryPublishBean ) );
            }
            catch ( final PwmException e )
            {
                lastError = e.getErrorInformation();
                LOGGER.error( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "error sending telemetry data: " + e.getMessage() );
            }
        }

        lastPublishTime = Instant.now();
        pwmApplication.writeAppAttribute( AppAttribute.TELEMETRY_LAST_PUBLISH_TIMESTAMP, lastPublishTime );
        scheduleNextJob();
    }

    private void scheduleNextJob( )
    {
        final TimeDuration durationUntilNextPublish = durationUntilNextPublish();
        pwmApplication.getPwmScheduler().scheduleJob( new PublishJob(), executorService, durationUntilNextPublish );
        LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "next publish time: " + durationUntilNextPublish().asCompactString() );
    }

    private class PublishJob implements Runnable
    {
        @Override
        public void run( )
        {
            try
            {
                executePublishJob();
            }
            catch ( final PwmException e )
            {
                LOGGER.error( e.getErrorInformation() );
            }
            catch ( final Exception e )
            {
                LOGGER.error( () -> "unexpected error during telemetry publish job: " + e.getMessage() );
            }
        }
    }

    @Override
    public void close( )
    {

    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> debugMap = new LinkedHashMap<>();
        debugMap.put( "lastPublishTime", JavaHelper.toIsoDate( lastPublishTime ) );
        if ( lastError != null )
        {
            debugMap.put( "lastError", lastError.toDebugStr() );
        }
        return new ServiceInfoBean( null, Collections.unmodifiableMap( debugMap ) );
    }


    public TelemetryPublishBean generatePublishableBean( )
            throws URISyntaxException, IOException, PwmUnrecoverableException
    {
        final StatisticsBundle bundle = pwmApplication.getStatisticsManager().getStatBundleForKey( StatisticsManager.KEY_CUMULATIVE );
        final Configuration config = pwmApplication.getConfig();
        final Map<PwmAboutProperty, String> aboutPropertyStringMap = PwmAboutProperty.makeInfoBean( pwmApplication );

        final Map<String, String> statData = new TreeMap<>();
        for ( final Statistic loopStat : Statistic.values() )
        {
            statData.put( loopStat.getKey(), bundle.getStatistic( loopStat ) );
        }

        final List<String> configuredSettings = new ArrayList<>();
        for ( final PwmSetting pwmSetting : config.nonDefaultSettings() )
        {
            if ( !pwmSetting.getCategory().hasProfiles() && !config.isDefaultValue( pwmSetting ) )
            {
                configuredSettings.add( pwmSetting.getKey() );
            }
        }

        String ldapVendorName = null;
        for ( final LdapProfile ldapProfile : config.getLdapProfiles().values() )
        {
            if ( ldapVendorName == null )
            {
                try
                {
                    final DirectoryVendor directoryVendor = ldapProfile.getProxyChaiProvider( pwmApplication ).getDirectoryVendor();
                    final PwmLdapVendor pwmLdapVendor = PwmLdapVendor.fromChaiVendor( directoryVendor );
                    if ( pwmLdapVendor != null )
                    {
                        ldapVendorName = pwmLdapVendor.name();
                    }
                }
                catch ( final Exception e )
                {
                    LOGGER.trace( SessionLabel.TELEMETRY_SESSION_LABEL, () -> "unable to read ldap vendor type for stats publication: " + e.getMessage() );
                }
            }
        }

        final Map<String, String> aboutStrings = new TreeMap<>();
        {
            for ( final Map.Entry<PwmAboutProperty, String> entry : aboutPropertyStringMap.entrySet() )
            {
                final PwmAboutProperty pwmAboutProperty = entry.getKey();
                aboutStrings.put( pwmAboutProperty.name(), entry.getValue() );
            }
            aboutStrings.remove( PwmAboutProperty.app_instanceID.name() );
            aboutStrings.remove( PwmAboutProperty.app_siteUrl.name() );
        }

        final TelemetryPublishBean.TelemetryPublishBeanBuilder builder = TelemetryPublishBean.builder();
        builder.timestamp( Instant.now() );
        builder.id( makeId( pwmApplication ) );
        builder.instanceHash( pwmApplication.getSecureService().hash( pwmApplication.getInstanceID() ) );
        builder.installTime( pwmApplication.getInstallTime() );
        builder.siteDescription( config.readSettingAsString( PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION ) );
        builder.versionBuild( PwmConstants.BUILD_NUMBER );
        builder.versionVersion( PwmConstants.BUILD_VERSION );
        builder.ldapVendorName( ldapVendorName );
        builder.statistics( Collections.unmodifiableMap( statData ) );
        builder.configuredSettings( Collections.unmodifiableList( configuredSettings ) );
        builder.about( aboutStrings );
        return builder.build();
    }

    private static String makeId( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        final String separator = "-";
        final String datetimePattern = "yyyyMMdd-HHmmss'Z'";
        final String timestamp = DateTimeFormatter.ofPattern( datetimePattern ).format( ZonedDateTime.now( ZoneId.of( "Zulu" ) ) );
        return PwmConstants.PWM_APP_NAME.toLowerCase()
                + separator + instanceHash( pwmApplication )
                + separator + timestamp;

    }

    private static String instanceHash( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        final int maxHashLength = 64;
        final String instanceID = pwmApplication.getInstanceID();
        final String hash = pwmApplication.getSecureService().hash( instanceID );
        return hash.length() > 64
                ? hash.substring( 0, maxHashLength )
                : hash;
    }

    @Getter
    @Builder
    private static class Settings
    {
        private TimeDuration publishFrequency;
        private int minimumAuthentications;
        private String senderImplementation;
        private String senderSettings;

        static Settings fromConfig( final Configuration config )
        {
            return Settings.builder()
                    .minimumAuthentications( Integer.parseInt( config.readAppProperty( AppProperty.TELEMETRY_MIN_AUTHENTICATIONS ) ) )
                    .publishFrequency( TimeDuration.of( Integer.parseInt( config.readAppProperty( AppProperty.TELEMETRY_SEND_FREQUENCY_SECONDS ) ), TimeDuration.Unit.SECONDS ) )
                    .senderImplementation( config.readAppProperty( AppProperty.TELEMETRY_SENDER_IMPLEMENTATION ) )
                    .senderSettings( config.readAppProperty( AppProperty.TELEMETRY_SENDER_SETTINGS ) )
                    .build();
        }
    }

    private TimeDuration durationUntilNextPublish( )
    {

        final Instant nextPublishTime = settings.getPublishFrequency().incrementFromInstant( lastPublishTime );
        final Instant minuteFromNow = TimeDuration.MINUTE.incrementFromInstant( Instant.now() );
        return nextPublishTime.isBefore( minuteFromNow )
                ? TimeDuration.fromCurrent( minuteFromNow )
                : TimeDuration.fromCurrent( nextPublishTime.toEpochMilli() + ( PwmRandom.getInstance().nextInt( 600 ) - 300 ) );
    }

}
