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

package password.pwm.svc.intruder;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.svc.PwmService;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.SystemAuditRecord;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.DataStore;
import password.pwm.util.DataStoreFactory;
import password.pwm.util.PwmScheduler;
import password.pwm.util.db.DatabaseDataStore;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.java.ClosableIterator;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBDataStore;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmRandom;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class IntruderService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( IntruderService.class );

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.CLOSED;
    private ErrorInformation startupError;
    private Timer timer;

    private final Map<IntruderRecordType, IntruderRecordManager> recordManagers = new HashMap<>();
    private IntruderSettings intruderSettings;


    private ServiceInfoBean serviceInfo = ServiceInfoBean.builder().build();

    public IntruderService( )
    {
        for ( final IntruderRecordType recordType : IntruderRecordType.values() )
        {
            recordManagers.put( recordType, new StubRecordManager() );
        }
    }

    @Override
    public STATUS status( )
    {
        return status;
    }

    @Override
    public void init( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmApplication = pwmApplication;
        final AppConfig config = this.pwmApplication.getConfig();
        this.intruderSettings = IntruderSettings.fromConfiguration( pwmApplication.getConfig() );

        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "unable to start IntruderManager, LocalDB unavailable" );
            LOGGER.error( errorInformation::toDebugStr );
            startupError = errorInformation;
            status = STATUS.CLOSED;
            return;
        }
        if ( !this.pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.INTRUDER_ENABLE ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "intruder module not enabled" );
            LOGGER.debug( errorInformation::toDebugStr );
            status = STATUS.CLOSED;
            return;
        }
        final DataStore dataStore;
        {
            final String debugMsg;
            final DataStorageMethod storageMethodUsed;
            switch ( intruderSettings.getIntruderStorageMethod() )
            {
                case AUTO:
                    dataStore = DataStoreFactory.autoDbOrLocalDBstore( this.pwmApplication, DatabaseTable.INTRUDER, LocalDB.DB.INTRUDER );
                    if ( dataStore instanceof DatabaseDataStore )
                    {
                        debugMsg = "starting using auto-configured data store, Remote Database selected";
                        storageMethodUsed = DataStorageMethod.DB;
                    }
                    else
                    {
                        debugMsg = "starting using auto-configured data store, LocalDB selected";
                        storageMethodUsed = DataStorageMethod.LOCALDB;
                    }
                    break;

                case DATABASE:
                    dataStore = new DatabaseDataStore( this.pwmApplication.getDatabaseService(), DatabaseTable.INTRUDER );
                    debugMsg = "starting using Remote Database data store";
                    storageMethodUsed = DataStorageMethod.DB;
                    break;

                case LOCALDB:
                    dataStore = new LocalDBDataStore( pwmApplication.getLocalDB(), LocalDB.DB.INTRUDER );
                    debugMsg = "starting using LocalDB data store";
                    storageMethodUsed = DataStorageMethod.LOCALDB;
                    break;

                default:
                    startupError = new ErrorInformation( PwmError.ERROR_INTERNAL, "unknown storageMethod selected: " + intruderSettings.getIntruderStorageMethod() );
                    status = STATUS.CLOSED;
                    return;
            }
            LOGGER.info( () -> debugMsg );
            serviceInfo = ServiceInfoBean.builder().storageMethod( storageMethodUsed ).build();
        }
        final IntruderRecordStore recordStore;
        {
            recordStore = new IntruderDataStore( dataStore, this );
            final String threadName = PwmScheduler.makeThreadName( pwmApplication, this.getClass() ) + " timer";
            timer = new Timer( threadName, true );
            final long maxRecordAge = Long.parseLong( this.pwmApplication.getConfig().readAppProperty( AppProperty.INTRUDER_RETENTION_TIME_MS ) );
            final long cleanerRunFrequency = Long.parseLong( this.pwmApplication.getConfig().readAppProperty( AppProperty.INTRUDER_CLEANUP_FREQUENCY_MS ) );
            timer.schedule( new TimerTask()
            {
                @Override
                public void run( )
                {
                    try
                    {
                        recordStore.cleanup( TimeDuration.of( maxRecordAge, TimeDuration.Unit.MILLISECONDS ) );
                    }
                    catch ( final Exception e )
                    {
                        LOGGER.error( () -> "error cleaning recordStore: " + e.getMessage(), e );
                    }
                }
            }, 1000, cleanerRunFrequency );
        }

        try
        {
            initializeRecordManagers( config, recordStore );
            status = STATUS.OPEN;
        }
        catch ( final Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "unexpected error starting intruder manager: " + e.getMessage() );
            LOGGER.error( errorInformation::toDebugStr );
            startupError = errorInformation;
            close();
        }
    }

    private void initializeRecordManagers( final AppConfig config, final IntruderRecordStore recordStore )
    {
        this.recordManagers.clear();

        for ( final IntruderRecordType type : IntruderRecordType.values() )
        {
            final IntruderSettings.IntruderRecordTypeSettings typeSettings = intruderSettings.getTargetSettings().get( type );
            if ( typeSettings.isConfigured() )
            {
                LOGGER.debug( () -> "starting record manager for type '" + type + "' with settings: " + typeSettings.toString() );
                recordManagers.put( type, new IntruderRecordManagerImpl( type, recordStore, typeSettings ) );
            }
            else
            {
                LOGGER.debug( () -> "skipping record manager for type '" + type + "' (not configured)" );
                recordManagers.put( type, new StubRecordManager() );
            }
        }
    }

    public void clear( )
    {

    }

    @Override
    public void close( )
    {
        status = STATUS.CLOSED;
        if ( timer != null )
        {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        if ( startupError != null && status != STATUS.OPEN )
        {
            return Collections.singletonList( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.ServiceClosed,
                    this.getClass().getSimpleName(),
                    startupError.toDebugStr() ) );
        }
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
                    JavaHelper.unhandledSwitchStatement( recordType );
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
        if ( recordType == null )
        {
            throw new IllegalArgumentException( "recordType is required" );
        }

        if ( subject == null || subject.length() < 1 )
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
            final UserIdentity userIdentity = UserIdentity.fromKey( subject, pwmApplication );
            final UserAuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createUserAuditRecord(
                    AuditEvent.INTRUDER_USER_ATTEMPT,
                    userIdentity,
                    sessionLabel
            );
            pwmApplication.getAuditManager().submit( sessionLabel, auditRecord );
        }
        else
        {
            // send intruder attempt audit event
            final Map<String, Object> messageObj = new LinkedHashMap<>();
            messageObj.put( "type", recordType );
            messageObj.put( "subject", subject );
            final String message = JsonUtil.serializeMap( messageObj );
            final SystemAuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createSystemAuditRecord( AuditEvent.INTRUDER_ATTEMPT, message );
            pwmApplication.getAuditManager().submit( sessionLabel, auditRecord );
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
                    final UserIdentity userIdentity = UserIdentity.fromKey( subject, pwmApplication );
                    final UserAuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createUserAuditRecord(
                            AuditEvent.INTRUDER_USER_LOCK,
                            userIdentity,
                            sessionLabel
                    );
                    pwmApplication.getAuditManager().submit( sessionLabel, auditRecord );
                    sendAlert( manager.readIntruderRecord( subject ), sessionLabel );
                }
                else
                {
                    // send intruder attempt lock event
                    final Map<String, Object> messageObj = new LinkedHashMap<>();
                    messageObj.put( "type", recordType );
                    messageObj.put( "subject", subject );
                    final String message = JsonUtil.serializeMap( messageObj );
                    final SystemAuditRecord auditRecord = new AuditRecordFactory( pwmApplication ).createSystemAuditRecord( AuditEvent.INTRUDER_LOCK, message );
                    pwmApplication.getAuditManager().submit( sessionLabel, auditRecord );
                }


                manager.markAlerted( subject );
                final StatisticsManager statisticsManager = pwmApplication.getStatisticsManager();
                if ( statisticsManager != null && statisticsManager.status() == STATUS.OPEN )
                {
                    statisticsManager.incrementValue( Statistic.INTRUDER_ATTEMPTS );
                    statisticsManager.updateEps( EpsStatistic.INTRUDER_ATTEMPTS, 1 );
                    statisticsManager.incrementValue( recordType.getLockStatistic() );
                }
            }
            throw e;
        }

        delayPenalty( manager.readIntruderRecord( subject ), sessionLabel == null ? null : sessionLabel );
    }

    private void delayPenalty( final IntruderRecord intruderRecord, final SessionLabel sessionLabel )
    {
        int points = 0;
        if ( intruderRecord != null )
        {
            points += intruderRecord.getAttemptCount();

            // minimum
            long delayPenalty = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.INTRUDER_MIN_DELAY_PENALTY_MS ) );
            delayPenalty += points * Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.INTRUDER_DELAY_PER_COUNT_MS ) );

            // add some randomness;
            delayPenalty += PwmRandom.getInstance().nextInt( ( int ) Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.INTRUDER_DELAY_MAX_JITTER_MS ) ) );
            delayPenalty = Math.min( delayPenalty, Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.INTRUDER_MAX_DELAY_PENALTY_MS ) ) );

            {
                final long finalDelay = delayPenalty;
                LOGGER.trace( sessionLabel, () -> "delaying response " + finalDelay + "ms due to intruder record: " + JsonUtil.serialize( intruderRecord ) );
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
                final UserIdentity identity = UserIdentity.fromDelimitedKey( intruderRecord.getSubject() );
                sendIntruderNoticeEmail( pwmApplication, sessionLabel, identity );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( sessionLabel, () -> "unable to send intruder mail, can't read userDN/ldapProfile from stored record: " + e.getMessage() );
            }
        }
    }

    public List<Map<String, Object>> getRecords( final IntruderRecordType recordType, final int maximum )
            throws PwmException
    {
        final IntruderRecordManager manager = recordManagers.get( recordType );
        final ArrayList<Map<String, Object>> returnList = new ArrayList<>();

        ClosableIterator<IntruderRecord> theIterator = null;
        try
        {
            theIterator = manager.iterator();
            while ( theIterator.hasNext() && returnList.size() < maximum )
            {
                final IntruderRecord intruderRecord = theIterator.next();
                if ( intruderRecord != null && intruderRecord.getType() == recordType )
                {
                    final Map<String, Object> rowData = new HashMap<>();
                    rowData.put( "subject", intruderRecord.getSubject() );
                    rowData.put( "timestamp", intruderRecord.getTimeStamp() );
                    rowData.put( "count", String.valueOf( intruderRecord.getAttemptCount() ) );
                    try
                    {
                        check( recordType, intruderRecord.getSubject() );
                        rowData.put( "status", "watching" );
                    }
                    catch ( final PwmException e )
                    {
                        rowData.put( "status", "locked" );
                    }
                    returnList.add( rowData );
                }
            }
        }
        finally
        {
            if ( theIterator != null )
            {
                theIterator.close();
            }
        }
        return returnList;
    }

    public IntruderServiceClient convenience( )
    {
        return new IntruderServiceClient( pwmApplication, this );
    }

    private static void sendIntruderNoticeEmail(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
    {
        /*
        //final Locale locale = LocaleHelper.getLocaleForSessionID( pwmApplication, sessionLabel.getSessionID() );
        final Locale locale = null;
        final AppConfig config = pwmApplication.getConfig();
        //final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_INTRUDERNOTICE, locale );
        final EmailItemBean configuredEmailSetting = null;

        if ( configuredEmailSetting == null )
        {
            return;
        }

        try
        {
            final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                    pwmApplication,
                    SessionLabel.SYSTEM_LABEL,
                    userIdentity, locale
            );

            final MacroRequest macroRequest = MacroRequest.forUser(
                    pwmApplication,
                    sessionLabel,
                    userInfo,
                    null
            );

            pwmApplication.getEmailQueue().submitEmail( configuredEmailSetting, userInfo, macroRequest );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.error( sessionLabel, () -> "error reading user info while sending intruder notice for user " + userIdentity + ", error: " + e.getMessage() );
        }

         */

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

        final IntruderRecord intruderRecord = recordManagers.get( IntruderRecordType.ADDRESS ).readIntruderRecord( srcAddress );
        if ( intruderRecord == null )
        {
            return 0;
        }

        return intruderRecord.getAttemptCount();
    }
}
