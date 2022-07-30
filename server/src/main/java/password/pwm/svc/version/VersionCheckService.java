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

package password.pwm.svc.version;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import password.pwm.AppAttribute;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.VersionNumber;
import password.pwm.bean.pub.PublishVersionBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.http.HttpContentType;
import password.pwm.http.HttpHeader;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.httpclient.PwmHttpClient;
import password.pwm.svc.httpclient.PwmHttpClientRequest;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.svc.telemetry.TelemetryService;
import password.pwm.util.PwmScheduler;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class VersionCheckService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( VersionCheckService.class );

    private PwmApplication pwmApplication;
    private VersionCheckSettings settings;
    private ScheduledExecutorService executorService;

    private VersionNumber runningVersion;
    private CacheHolder cacheHolder;
    private Instant nextScheduledCheck;

    private enum DebugKey
    {
        runningVersion,
        currentVersion,
        outdatedVersionFlag,
        lastCheckTime,
        nextCheckTime,
        lastError
    }

    public void init( final PwmApplication pwmApplication )
            throws PwmException
    {
        this.pwmApplication = Objects.requireNonNull( pwmApplication );
        this.settings = VersionCheckSettings.fromConfig( pwmApplication.getConfig() );
        initRunningVersion();

        if ( enabled() )
        {
            cacheHolder = new CacheHolder( pwmApplication );

            setStatus( PwmService.STATUS.OPEN );

            executorService = PwmScheduler.makeSingleThreadExecutorService( pwmApplication, TelemetryService.class );

            scheduleNextCheck();

            return;
        }

        setStatus( STATUS.CLOSED );
    }

    @Override
    public void close()
    {
        if ( executorService != null )
        {
            executorService.shutdownNow();
        }
    }

    private Map<DebugKey, String> debugMap()
    {
        if ( status() != PwmService.STATUS.OPEN )
        {
            return Collections.emptyMap();
        }

        final String notApplicable = LocaleHelper.valueNotApplicable( PwmConstants.DEFAULT_LOCALE );
        final VersionCheckInfoCache localCache = cacheHolder.getVersionCheckInfoCache();

        final Map<DebugKey, String> debugKeyMap = new EnumMap<>( DebugKey.class );
        debugKeyMap.put( DebugKey.runningVersion, runningVersion == null ? notApplicable : runningVersion.prettyVersionString() );
        debugKeyMap.put( DebugKey.currentVersion, localCache.getCurrentVersion() == null ? notApplicable : localCache.getCurrentVersion().prettyVersionString() );
        debugKeyMap.put( DebugKey.outdatedVersionFlag, LocaleHelper.valueBoolean( PwmConstants.DEFAULT_LOCALE, isOutdated() ) );
        debugKeyMap.put( DebugKey.lastError, localCache.getLastError() == null ? notApplicable : localCache.getLastError().toDebugStr() );
        debugKeyMap.put( DebugKey.lastCheckTime, localCache.getLastCheckTimestamp() == null ? notApplicable : JavaHelper.toIsoDate( localCache.getLastCheckTimestamp() ) );
        debugKeyMap.put( DebugKey.nextCheckTime, nextScheduledCheck == null
                ? notApplicable
                : JavaHelper.toIsoDate( nextScheduledCheck ) + " (" + TimeDuration.compactFromCurrent( nextScheduledCheck ) + ")" );

        return Collections.unmodifiableMap( debugKeyMap );
    }



    public PwmService.ServiceInfoBean serviceInfo()
    {
        return PwmService.ServiceInfoBean.builder()
                .debugProperties( JavaHelper.enumMapToStringMap( debugMap() ) )
                .build();
    }

    private void scheduleNextCheck()
    {
        if ( status() != PwmService.STATUS.OPEN )
        {
            return;
        }

        this.nextScheduledCheck = Instant.now().plus( 1, ChronoUnit.MINUTES );
        executorService.schedule( new PeriodicCheck(), 1, TimeUnit.MINUTES );

        /*
        final VersionCheckInfoCache localCache = cacheHolder.getVersionCheckInfoCache();

        final TimeDuration idealDurationUntilNextCheck = localCache.getLastError() != null && localCache.getCurrentVersion() == null
                ? settings.getCheckIntervalError()
                : settings.getCheckInterval();

        if ( localCache.getLastCheckTimestamp() == null )
        {
            this.nextScheduledCheck = Instant.now().plus( 10, ChronoUnit.SECONDS );
        }
        else
        {
            final Instant nextIdealTimestamp = localCache.getLastCheckTimestamp().plus( idealDurationUntilNextCheck.asDuration() );
            this.nextScheduledCheck = nextIdealTimestamp.isBefore( Instant.now() )
                    ? Instant.now().plus( 10, ChronoUnit.SECONDS )
                    : nextIdealTimestamp;
        }

        final TimeDuration delayUntilNextExecution = TimeDuration.fromCurrent( this.nextScheduledCheck );

        executorService.schedule( new PeriodicCheck(), delayUntilNextExecution.asMillis(), TimeUnit.MILLISECONDS );

        LOGGER.trace( SessionLabel.VERSIONCHECK_SESSION_LABEL, () -> "scheduled next check execution at " + JavaHelper.toIsoDate( nextScheduledCheck )
                + " in " + delayUntilNextExecution.asCompactString() );

         */
    }

    private class PeriodicCheck implements Runnable
    {
        @Override
        public void run()
        {
            if ( status() != PwmService.STATUS.OPEN )
            {
                return;
            }

            try
            {
                processReturnedVersionBean( executeFetch() );
            }
            catch ( final PwmUnrecoverableException e )
            {
                cacheHolder.setVersionCheckInfoCache( VersionCheckInfoCache.builder()
                        .lastError( e.getErrorInformation() )
                        .lastCheckTimestamp( Instant.now() )
                        .build() );
            }

            scheduleNextCheck();
        }
    }

    private void processReturnedVersionBean( final PublishVersionBean publishVersionBean )
    {
        final Instant startTime = Instant.now();

        final VersionNumber currentVersion = publishVersionBean.getVersions().get( PublishVersionBean.VersionKey.current );

        cacheHolder.setVersionCheckInfoCache( VersionCheckInfoCache.builder()
                .currentVersion( currentVersion )
                .lastCheckTimestamp( Instant.now() )
                .build() );

        LOGGER.trace( SessionLabel.VERSIONCHECK_SESSION_LABEL, () -> "successfully fetched current version information from cloud service: "
                + currentVersion, () -> TimeDuration.fromCurrent( startTime ) );
    }

    private PublishVersionBean executeFetch()
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        try
        {
            final PwmHttpClient pwmHttpClient = pwmApplication.getHttpClientService().getPwmHttpClient();
            final PwmHttpClientRequest request = PwmHttpClientRequest.builder()
                    .url( settings.getUrl() )
                    .header( HttpHeader.ContentType.getHttpName(), HttpContentType.json.getHeaderValueWithEncoding() )
                    .header( HttpHeader.Accept.getHttpName(), HttpContentType.json.getHeaderValueWithEncoding() )
                    .body( JsonUtil.serialize( makeRequestBody() ) )
                    .build();

            LOGGER.trace( SessionLabel.VERSIONCHECK_SESSION_LABEL, () -> "sending cloud version request to: " + settings.getUrl() );
            final PwmHttpClientResponse response = pwmHttpClient.makeRequest( request, SessionLabel.VERSIONCHECK_SESSION_LABEL );

            if ( response.getStatusCode() == 200 )
            {
                final Type restResultBeanType = newParameterizedType( RestObjectBean.class, PublishVersionBean.class );

                final String body = response.getBody();
                final RestObjectBean<PublishVersionBean> restResultBean = JsonUtil.deserialize( body, restResultBeanType );
                return restResultBean.getData();
            }
            else
            {
                LOGGER.debug( SessionLabel.VERSIONCHECK_SESSION_LABEL, () -> "error reading cloud current version information: " + response );
                final String msg = "error reading cloud current version information: " + response.getStatusCode()
                        + " " + ( response.getStatusPhrase() == null ? "" : response.getStatusPhrase() );
                throw PwmUnrecoverableException.newException( PwmError.ERROR_UNREACHABLE_CLOUD_SERVICE, msg );
            }
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation;

            if ( e instanceof PwmException )
            {
                errorInformation = ( ( PwmUnrecoverableException ) e ).getErrorInformation();
            }
            else
            {
                final String errorMsg = "error reading current version from cloud service: " + e.getMessage();
                errorInformation = new ErrorInformation( PwmError.ERROR_UNREACHABLE_CLOUD_SERVICE, errorMsg );
            }

            LOGGER.debug( SessionLabel.VERSIONCHECK_SESSION_LABEL, () -> "error fetching current version from cloud: "
                    + e.getMessage(), () -> TimeDuration.fromCurrent( startTime ) );

            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    private PublishVersionBean makeRequestBody()
    {
        VersionNumber versionNumber = VersionNumber.ZERO;
        try
        {
            versionNumber = VersionNumber.parse( PwmConstants.BUILD_VERSION );
        }
        catch ( final Exception e )
        {
            LOGGER.trace( SessionLabel.VERSIONCHECK_SESSION_LABEL, () -> "error reading local version number " + e.getMessage() );
        }

        return new PublishVersionBean( Collections.singletonMap( PublishVersionBean.VersionKey.current, versionNumber ) );
    }

    @Override
    protected List<HealthRecord> serviceHealthCheck()
    {
        if ( status() != PwmService.STATUS.OPEN )
        {
            return Collections.emptyList();
        }

        final VersionCheckInfoCache localCache = cacheHolder.getVersionCheckInfoCache();

        if ( isOutdated() )
        {
            return Collections.singletonList( HealthRecord.forMessage(
                    HealthMessage.Version_OutOfDate,
                    PwmConstants.PWM_APP_NAME,
                    localCache.getCurrentVersion().prettyVersionString() ) );
        }

        if ( localCache.getLastError() != null )
        {
            return Collections.singletonList( HealthRecord.forMessage(
                    HealthMessage.Version_Unreachable,
                    localCache.getLastError().toDebugStr() ) );
        }

        return Collections.emptyList();
    }

    private boolean isOutdated()
    {
        if ( status() != PwmService.STATUS.OPEN )
        {
            return false;
        }

        final VersionCheckInfoCache localCache = cacheHolder.getVersionCheckInfoCache();
        if ( runningVersion == null || localCache.getCurrentVersion() == null )
        {
            return false;
        }

        final int comparisonInt = runningVersion.compareTo( localCache.getCurrentVersion() );
        return comparisonInt < 0;
    }

    @Value
    @Builder
    private static class VersionCheckInfoCache implements Serializable
    {
        private final Instant lastCheckTimestamp;
        private final ErrorInformation lastError;

        @SuppressFBWarnings( "SE_BAD_FIELD" )
        private final VersionNumber currentVersion;
    }

    @Value
    @Builder
    private static class VersionCheckSettings
    {
        private static final int DEFAULT_INTERVAL_SECONDS = 3801;

        private final String url;
        private final TimeDuration checkInterval;
        private final TimeDuration checkIntervalError;

        static VersionCheckSettings fromConfig( final Configuration appConfig )
        {
            final int checkSeconds = JavaHelper.silentParseInt( appConfig.readAppProperty( AppProperty.VERSION_CHECK_CHECK_INTERVAL_SECONDS ), DEFAULT_INTERVAL_SECONDS );
            final int checkSecondsError = JavaHelper.silentParseInt(
                    appConfig.readAppProperty( AppProperty.VERSION_CHECK_CHECK_INTERVAL_ERROR_SECONDS ), DEFAULT_INTERVAL_SECONDS );

            return  VersionCheckSettings.builder()
                    .url( appConfig.readAppProperty( AppProperty.VERSION_CHECK_URL ) )
                    .checkInterval( TimeDuration.of( checkSeconds, TimeDuration.Unit.SECONDS ) )
                    .checkIntervalError( TimeDuration.of( checkSecondsError, TimeDuration.Unit.SECONDS ) )
                    .build();
        }
    }

    private void initRunningVersion()
    {
        try
        {
            this.runningVersion = VersionNumber.parse( PwmConstants.BUILD_VERSION );
        }
        catch ( final Exception e )
        {
            LOGGER.error( SessionLabel.VERSIONCHECK_SESSION_LABEL, () -> "error parsing internal running version number: " + e.getMessage() );
        }
    }

    private boolean enabled()
    {
        return pwmApplication.getLocalDB() != null
                && runningVersion != null
                && pwmApplication.getLocalDB().status() == LocalDB.Status.OPEN
                && !pwmApplication.getPwmEnvironment().isInternalRuntimeInstance()
                && pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.VERSION_CHECK_ENABLE );
    }

    private static class CacheHolder
    {
        private final PwmApplication pwmApplication;
        private VersionCheckInfoCache versionCheckInfoCache;

        CacheHolder( final PwmApplication pwmApplication )
        {
            this.pwmApplication = pwmApplication;
            this.versionCheckInfoCache = pwmApplication.readAppAttribute( AppAttribute.VERSION_CHECK_CACHE, VersionCheckInfoCache.class )
                    .orElse( VersionCheckInfoCache.builder().build() );
        }

        public VersionCheckInfoCache getVersionCheckInfoCache()
        {
            return versionCheckInfoCache == null ? VersionCheckInfoCache.builder().build() : versionCheckInfoCache;
        }

        public void setVersionCheckInfoCache( final VersionCheckInfoCache versionCheckInfoCache )
        {
            this.versionCheckInfoCache = versionCheckInfoCache;
            pwmApplication.writeAppAttribute( AppAttribute.VERSION_CHECK_CACHE, versionCheckInfoCache );
        }
    }

    private static Type newParameterizedType( final Type rawType, final Type... parameterizedTypes )
    {
        return new ParameterizedType()
        {
            @Override
            public Type[] getActualTypeArguments()
            {
                return parameterizedTypes;
            }

            @Override
            public Type getRawType()
            {
                return rawType;
            }

            @Override
            public Type getOwnerType()
            {
                return null;
            }
        };
    }

    @Value
    @Builder( toBuilder =  true, access = AccessLevel.PACKAGE )
    private static class RestObjectBean<T> implements Serializable
    {
        private static final long serialVersionUID = 1L;

        private final T data;
        private final boolean error;
        private final int errorCode;
        private final String errorMessage;
        private final String errorDetail;
        private final String successMessage;

        @SuppressFBWarnings( "SE_TRANSIENT_FIELD_NOT_RESTORED" )
        private final transient Class<T> classOfT;

        public static <T> RestObjectBean<T> withData( final T data, final Class<T> classOfT )
        {
            return RestObjectBean.<T>builder()
                    .data( data )
                    .classOfT( classOfT )
                    .build();
        }
    }
}
