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

package password.pwm.svc.intruder;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IntruderStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.db.DatabaseDataStore;
import password.pwm.svc.db.DatabaseTable;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.event.SystemAuditRecord;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.DataStore;
import password.pwm.util.DataStoreFactory;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBDataStore;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.secure.PwmRandom;

import java.net.InetAddress;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class IntruderDomainService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( IntruderDomainService.class );

    private PwmDomain pwmDomain;

    private final Map<IntruderRecordType, IntruderRecordManager> recordManagers = new EnumMap<>( IntruderRecordType.class );
    private IntruderSettings intruderSettings;
    private ServiceInfoBean serviceInfo = ServiceInfoBean.builder().build();

    public IntruderDomainService( )
    {
        EnumSet.allOf( IntruderRecordType.class ).forEach( recordType -> recordManagers.put( recordType, new StubRecordManager() ) );
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmDomain = pwmApplication.domains().get( domainID );
        this.intruderSettings = IntruderSettings.fromConfiguration( pwmDomain.getConfig() );

        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "unable to start IntruderManager, LocalDB unavailable" );
            LOGGER.error( errorInformation::toDebugStr );
            setStartupError( errorInformation );
            return STATUS.CLOSED;
        }
        if ( !this.pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.INTRUDER_ENABLE ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "intruder module not enabled" );
            LOGGER.debug( errorInformation::toDebugStr );
            return STATUS.CLOSED;
        }

        try
        {
            final DataStore dataStore = initDataStore( pwmApplication, getSessionLabel(), intruderSettings.getIntruderStorageMethod() );
            serviceInfo = ServiceInfoBean.builder().storageMethod( dataStore.getDataStorageMethod() ).build();

            initializeRecordManagers();
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "unexpected error starting intruder manager: " + e.getMessage() );
            LOGGER.error( errorInformation::toDebugStr );
            setStartupError( errorInformation );
            close();
            return STATUS.CLOSED;
        }

        return STATUS.OPEN;

    }

    static DataStore initDataStore(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final IntruderStorageMethod intruderStorageMethod
    )
            throws PwmUnrecoverableException
    {
        final DataStore dataStore;
        final String debugMsg;
        switch ( intruderStorageMethod )
        {
            case AUTO:
                dataStore = DataStoreFactory.autoDbOrLocalDBstore( pwmApplication, DatabaseTable.INTRUDER, LocalDB.DB.INTRUDER );
                if ( dataStore instanceof DatabaseDataStore )
                {
                    debugMsg = "starting using auto-configured data store, Remote Database selected";
                }
                else
                {
                    debugMsg = "starting using auto-configured data store, LocalDB selected";
                }
                break;

            case DATABASE:
                dataStore = new DatabaseDataStore( pwmApplication.getDatabaseService(), DatabaseTable.INTRUDER );
                debugMsg = "starting using Remote Database data store";
                break;

            case LOCALDB:
                dataStore = new LocalDBDataStore( pwmApplication.getLocalDB(), LocalDB.DB.INTRUDER );
                debugMsg = "starting using LocalDB data store";
                break;

            default:
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "unknown storageMethod selected: " + intruderStorageMethod ) );
        }
        LOGGER.debug( sessionLabel, () -> debugMsg );

        return dataStore;
    }

    private void initializeRecordManagers() throws PwmUnrecoverableException
    {
        this.recordManagers.clear();
        final IntruderRecordStore recordStore = pwmDomain.getPwmApplication().getIntruderSystemService().getRecordStore();

        for ( final IntruderRecordType type : IntruderRecordType.values() )
        {
            final IntruderSettings.TypeSettings typeSettings = intruderSettings.getTargetSettings().get( type );
            if ( typeSettings.isConfigured() )
            {
                LOGGER.debug( getSessionLabel(), () -> "starting record manager for type '" + type + "' with settings: " + typeSettings.toString() );
                recordManagers.put( type, new IntruderRecordManagerImpl( pwmDomain, type, recordStore, intruderSettings ) );
            }
            else
            {
                LOGGER.debug( getSessionLabel(), () -> "skipping record manager for type '" + type + "' (not configured)" );
                recordManagers.put( type, new StubRecordManager() );
            }
        }
    }


    @Override
    public void close( )
    {
        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    public void check( final IntruderRecordType recordType, final String subject )
            throws PwmUnrecoverableException
    {
        if ( recordType == null )
        {
            throw new IllegalArgumentException( "recordType is required" );
        }

        if ( subject == null || subject.length() < 1 )
        {
            return;
        }

        final IntruderRecordManager manager = recordManagers.get( recordType );
        final boolean locked = manager.checkSubject( subject );

        if ( locked )
        {
            switch ( recordType )
            {
                case ADDRESS:
                    throw new PwmUnrecoverableException( PwmError.ERROR_INTRUDER_ADDRESS );

                case ATTRIBUTE:
                    throw new PwmUnrecoverableException( PwmError.ERROR_INTRUDER_ATTR_SEARCH );

                case TOKEN_DEST:
                    throw new PwmUnrecoverableException( PwmError.ERROR_INTRUDER_TOKEN_DEST );

                case USER_ID:
                case USERNAME:
                    throw new PwmUnrecoverableException( PwmError.ERROR_INTRUDER_USER );

                default:
                    MiscUtil.unhandledSwitchStatement( recordType );
            }
        }
    }

    public void clear( final IntruderRecordType recordType, final String subject )
            throws PwmUnrecoverableException
    {
        if ( recordType == null )
        {
            throw new IllegalArgumentException( "recordType is required" );
        }

        if ( subject == null || subject.length() < 1 )
        {
            return;
        }

        final IntruderRecordManager manager = recordManagers.get( recordType );
        manager.clearSubject( subject );
    }

    public void mark( final IntruderRecordType recordType, final String subject, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        Objects.requireNonNull( recordType );

        if ( StringUtil.isEmpty( subject ) )
        {
            return;
        }

        if ( recordType == IntruderRecordType.ADDRESS )
        {
            try
            {
                final InetAddress inetAddress = InetAddress.getByName( subject );
                if ( inetAddress.isAnyLocalAddress() || inetAddress.isLoopbackAddress() || inetAddress.isLinkLocalAddress() )
                {
                    LOGGER.debug( sessionLabel, () -> "disregarding local address intruder attempt from: " + subject );
                    return;
                }
            }
            catch ( final Exception e )
            {
                LOGGER.error( sessionLabel, () -> "error examining address: " + subject );
            }
        }

        final IntruderRecordManager manager = recordManagers.get( recordType );
        manager.markSubject( subject );

        if ( recordType == IntruderRecordType.USER_ID )
        {
            final UserIdentity userIdentity = UserIdentity.fromDelimitedKey( sessionLabel, subject );
            final UserAuditRecord auditRecord = AuditRecordFactory.make( sessionLabel, pwmDomain ).createUserAuditRecord(
                    AuditEvent.INTRUDER_USER_ATTEMPT,
                    userIdentity,
                    sessionLabel
            );
            AuditServiceClient.submit( pwmDomain.getPwmApplication(), sessionLabel, auditRecord );
        }
        else
        {
            // send intruder attempt audit event
            final Map<String, Object> messageObj = new LinkedHashMap<>();
            messageObj.put( "type", recordType );
            messageObj.put( "subject", subject );
            final String message = JsonFactory.get().serializeMap( messageObj );
            AuditServiceClient.submitSystemEvent( pwmDomain.getPwmApplication(), sessionLabel, AuditEvent.INTRUDER_ATTEMPT, message );

            final SystemAuditRecord auditRecord = AuditRecordFactory.make( sessionLabel, pwmDomain ).createSystemAuditRecord( AuditEvent.INTRUDER_ATTEMPT, message );
            AuditServiceClient.submit( pwmDomain.getPwmApplication(), sessionLabel, auditRecord );
        }

        try
        {
            check( recordType, subject );
        }
        catch ( final PwmUnrecoverableException e )
        {
            if ( !manager.isAlerted( subject ) )
            {
                if ( recordType == IntruderRecordType.USER_ID )
                {
                    final UserIdentity userIdentity = UserIdentity.fromDelimitedKey( sessionLabel, subject );
                    final UserAuditRecord auditRecord = AuditRecordFactory.make( sessionLabel, pwmDomain ).createUserAuditRecord(
                            AuditEvent.INTRUDER_USER_LOCK,
                            userIdentity,
                            sessionLabel
                    );
                    AuditServiceClient.submit( pwmDomain.getPwmApplication(), sessionLabel, auditRecord );
                    manager.readIntruderRecord( subject ).ifPresent( record -> sendAlert( record, sessionLabel ) );
                }
                else
                {
                    // send intruder attempt lock event
                    final Map<String, Object> messageObj = new LinkedHashMap<>();
                    messageObj.put( "type", recordType );
                    messageObj.put( "subject", subject );
                    final String message = JsonFactory.get().serializeMap( messageObj );
                    AuditServiceClient.submitSystemEvent( pwmDomain.getPwmApplication(), sessionLabel, AuditEvent.INTRUDER_LOCK, message );
                }

                manager.markAlerted( subject );

                StatisticsClient.incrementStat( pwmDomain.getPwmApplication(), Statistic.INTRUDER_ATTEMPTS );
                StatisticsClient.updateEps( pwmDomain.getPwmApplication(), EpsStatistic.INTRUDER_ATTEMPTS );
                StatisticsClient.incrementStat( pwmDomain.getPwmApplication(), recordType.getLockStatistic() );
            }
            throw e;
        }

        manager.readIntruderRecord( subject ).ifPresent( record -> delayPenalty( record, sessionLabel ) );
    }

    private void delayPenalty( final IntruderRecord intruderRecord, final SessionLabel sessionLabel )
    {
        int points = 0;
        if ( intruderRecord != null )
        {
            points += intruderRecord.getAttemptCount();

            // minimum
            long delayPenalty = Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.INTRUDER_MIN_DELAY_PENALTY_MS ) );
            delayPenalty += points * Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.INTRUDER_DELAY_PER_COUNT_MS ) );

            // add some randomness;
            delayPenalty += PwmRandom.getInstance().nextInt( ( int ) Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.INTRUDER_DELAY_MAX_JITTER_MS ) ) );
            delayPenalty = Math.min( delayPenalty, Long.parseLong( pwmDomain.getConfig().readAppProperty( AppProperty.INTRUDER_MAX_DELAY_PENALTY_MS ) ) );

            {
                final long finalDelay = delayPenalty;
                LOGGER.trace( sessionLabel, () -> "delaying response " + finalDelay + "ms due to intruder record: " + JsonFactory.get().serialize( intruderRecord ) );
            }

            TimeDuration.of( delayPenalty, TimeDuration.Unit.MILLISECONDS ).pause();
        }
    }

    private void sendAlert( final IntruderRecord intruderRecord, final SessionLabel sessionLabel )
    {
        if ( intruderRecord == null )
        {
            return;
        }

        if ( intruderRecord.getType() == IntruderRecordType.USER_ID )
        {
            try
            {
                final UserIdentity identity = UserIdentity.fromDelimitedKey( sessionLabel, intruderRecord.getSubject() );
                sendIntruderNoticeEmail( pwmDomain, sessionLabel, identity );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( sessionLabel, () -> "unable to send intruder mail, can't read userDN/ldapProfile from stored record: " + e.getMessage() );
            }
        }
    }

    private static void sendIntruderNoticeEmail(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
    {
        final Locale locale = LocaleHelper.getLocaleForSessionID( pwmDomain, sessionLabel.getSessionID() );
        final EmailItemBean configuredEmailSetting = pwmDomain.getConfig().readSettingAsEmail( PwmSetting.EMAIL_INTRUDERNOTICE, locale );

        if ( configuredEmailSetting == null )
        {
            return;
        }

        try
        {
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmDomain.getPwmApplication(),
                    SessionLabel.SYSTEM_LABEL,
                    userIdentity, locale
            );

            final MacroRequest macroRequest = MacroRequest.forUser(
                    pwmDomain.getPwmApplication(),
                    sessionLabel,
                    userInfo,
                    null
            );

            pwmDomain.getPwmApplication().getEmailQueue().submitEmail( configuredEmailSetting, userInfo, macroRequest );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( sessionLabel, () -> "error reading user info while sending intruder notice for user " + userIdentity + ", error: " + e.getMessage() );
        }
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return serviceInfo;
    }

    public int countForNetworkEndpointInRequest( final PwmRequest pwmRequest )
    {
        final String srcAddress = pwmRequest.getPwmSession().getSessionStateBean().getSrcAddress();
        if ( srcAddress == null || srcAddress.isEmpty() )
        {
            return 0;
        }

        final Optional<IntruderRecord> intruderRecord = recordManagers.get( IntruderRecordType.ADDRESS ).readIntruderRecord( srcAddress );
        if ( intruderRecord.isEmpty() )
        {
            return 0;
        }

        return intruderRecord.get().getAttemptCount();
    }
}
