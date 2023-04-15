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
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.TelemetryPublishBean;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.PwmLdapVendor;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsBundle;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.secure.PwmRandom;

import java.io.IOException;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class TelemetryService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( TelemetryService.class );

    private Settings settings;

    private Instant lastPublishTime;
    private ErrorInformation lastError;
    private TelemetrySender sender;

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        if ( pwmApplication.getApplicationMode() != PwmApplicationMode.RUNNING )
        {
            LOGGER.trace( getSessionLabel(), () -> "will remain closed, app is not running" );
            return STATUS.CLOSED;
        }

        if ( !pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.PUBLISH_STATS_ENABLE ) )
        {
            LOGGER.trace( getSessionLabel(), () -> "will remain closed, publish stats not enabled" );
            return STATUS.CLOSED;
        }

        if ( pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            LOGGER.trace( getSessionLabel(), () -> "will remain closed, localdb not enabled" );
            return STATUS.CLOSED;
        }

        if ( pwmApplication.getStatisticsService().status() != STATUS.OPEN )
        {
            LOGGER.trace( getSessionLabel(), () -> "will remain closed, statistics manager is not enabled" );
            return STATUS.CLOSED;
        }

        settings = Settings.fromConfig( pwmApplication.getConfig() );
        try
        {
            initSender();
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.trace( getSessionLabel(), () -> "will remain closed, unable to init sender: " + e.getMessage() );
            return STATUS.CLOSED;
        }

        lastPublishTime = pwmApplication.readAppAttribute( AppAttribute.TELEMETRY_LAST_PUBLISH_TIMESTAMP, Instant.class )
                .orElseGet( pwmApplication::getInstallTime );
        LOGGER.trace( getSessionLabel(), () -> "last publish time was " + StringUtil.toIsoDate( lastPublishTime ) );

        scheduleNextJob();

        return STATUS.OPEN;
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
            telemetrySender = ( TelemetrySender ) theClass.getDeclaredConstructor().newInstance();
        }
        catch ( final Exception e )
        {
            final String msg = "unable to load implementation class: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, msg ) );
        }

        try
        {
            final String macrodSettings = MacroRequest.forNonUserSpecific( getPwmApplication(), null ).expandMacros( settings.getSenderSettings() );
            telemetrySender.init( getPwmApplication(), getSessionLabel(), macrodSettings );
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
        final String authValue = getPwmApplication().getStatisticsService().getCumulativeBundle().getStatistic( Statistic.AUTHENTICATIONS );
        if ( StringUtil.isEmpty( authValue ) || Integer.parseInt( authValue ) < settings.getMinimumAuthentications() )
        {
            LOGGER.trace( getSessionLabel(), () -> "skipping telemetry send, authentication count is too low" );
        }
        else
        {
            try
            {
                final TelemetryPublishBean telemetryPublishBean = generatePublishableBean();
                sender.publish( telemetryPublishBean );
                LOGGER.trace( getSessionLabel(), () -> "sent telemetry data: " + JsonFactory.get().serialize( telemetryPublishBean ) );
            }
            catch ( final PwmException e )
            {
                lastError = e.getErrorInformation();
                LOGGER.error( getSessionLabel(), () -> "error sending telemetry data: " + e.getMessage() );
            }
        }

        lastPublishTime = Instant.now();
        getPwmApplication().writeAppAttribute( AppAttribute.TELEMETRY_LAST_PUBLISH_TIMESTAMP, lastPublishTime );
        scheduleNextJob();
    }

    private void scheduleNextJob( )
    {
        final TimeDuration durationUntilNextPublish = durationUntilNextPublish();
        scheduleJob( new PublishJob(), durationUntilNextPublish );
        LOGGER.trace( getSessionLabel(), () -> "next publish time: " + durationUntilNextPublish().asCompactString() );
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
                LOGGER.error( getSessionLabel(), e.getErrorInformation() );
            }
            catch ( final Exception e )
            {
                LOGGER.error( getSessionLabel(), () -> "unexpected error during telemetry publish job: " + e.getMessage() );
            }
        }
    }

    @Override
    public void shutdownImpl( )
    {

    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        if ( status() != STATUS.OPEN )
        {
            return null;
        }

        final Map<String, String> debugMap = new LinkedHashMap<>();
        debugMap.put( "lastPublishTime", StringUtil.toIsoDate( lastPublishTime ) );
        if ( lastError != null )
        {
            debugMap.put( "lastError", lastError.toDebugStr() );
        }
        return ServiceInfoBean.builder().debugProperties( debugMap ).build();
    }


    public TelemetryPublishBean generatePublishableBean( )
            throws PwmUnrecoverableException
    {
        final StatisticsBundle bundle = getPwmApplication().getStatisticsService().getCumulativeBundle();
        final AppConfig config = getPwmApplication().getConfig();
        final Map<PwmAboutProperty, String> aboutPropertyStringMap = PwmAboutProperty.makeInfoBean( getPwmApplication() );

        final Map<String, String> statData = Arrays.stream( Statistic.values() ).collect( Collectors.toUnmodifiableMap(
                Statistic::getKey,
                bundle::getStatistic ) );

        final List<String> configuredSettings = CollectionUtil.iteratorToStream( config.getStoredConfiguration().keys() )
                .filter( key -> key.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                .map( key -> key.toPwmSetting().getKey() )
                .distinct()
                .sorted()
                .collect( Collectors.toUnmodifiableList() );

        final Optional<String> ldapVendorName = determineLdapVendorName();

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

        final PwmApplication pwmApplication = getPwmApplication();

        return TelemetryPublishBean.builder()
                .timestamp( Instant.now() )
                .id( makeId( pwmApplication ) )
                .instanceHash( pwmApplication.getSecureService().hash( pwmApplication.getInstanceID() ) )
                .installTime( pwmApplication.getInstallTime() )
                .siteDescription( config.readSettingAsString( PwmSetting.PUBLISH_STATS_SITE_DESCRIPTION ) )
                .versionBuild( PwmConstants.BUILD_NUMBER )
                .versionVersion( PwmConstants.BUILD_VERSION )
                .ldapVendorName( ldapVendorName.orElse( null ) )
                .statistics( statData )
                .configuredSettings( configuredSettings )
                .about( aboutStrings )
                .build();
    }

    private Optional<String> determineLdapVendorName()
    {
        for ( final PwmDomain pwmDomain : getPwmApplication().domains().values() )
        {
            for ( final LdapProfile ldapProfile : pwmDomain.getConfig().getLdapProfiles().values() )
            {
                try
                {
                    final DirectoryVendor directoryVendor = ldapProfile.getProxyChaiProvider( getSessionLabel(), pwmDomain ).getDirectoryVendor();
                    final PwmLdapVendor pwmLdapVendor = PwmLdapVendor.fromChaiVendor( directoryVendor );
                    if ( pwmLdapVendor != null )
                    {
                        return Optional.of( pwmLdapVendor.name() );
                    }
                }
                catch ( final Exception e )
                {
                    LOGGER.trace( getSessionLabel(), () -> "unable to read ldap vendor type for stats publication: " + e.getMessage() );
                }
            }
        }

        return Optional.empty();
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

        static Settings fromConfig( final AppConfig config )
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
