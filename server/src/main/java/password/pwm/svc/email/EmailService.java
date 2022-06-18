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

package password.pwm.svc.email;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Transport;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.user.UserInfo;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.ConditionalTaskExecutor;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.localdb.WorkQueueProcessor;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Jason D. Rivard
 */
public class EmailService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( EmailService.class );

    private WorkQueueProcessor<EmailItemBean> workQueueProcessor;
    private EmailServiceSettings emailServiceSettings;
    private EmailConnectionPool connectionPool = EmailConnectionPool.emptyConnectionPool();

    private final AtomicReference<ErrorInformation> lastSendError = new AtomicReference<>();

    private final ConditionalTaskExecutor statsLogger = ConditionalTaskExecutor.forPeriodicTask(
            this::logStats,
            TimeDuration.MINUTE.asDuration() );
    private final ReentrantLock submitLock = new ReentrantLock();

    @Override
    protected Set<PwmApplication.Condition> openConditions()
    {
        return Collections.emptySet();
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.emailServiceSettings = EmailServiceSettings.fromConfiguration( this.getPwmApplication().getConfig() );

        if ( this.getPwmApplication().getLocalDB() == null || this.getPwmApplication().getLocalDB().status() != LocalDB.Status.OPEN )
        {
            LOGGER.debug( getSessionLabel(), () -> "localDB is not open, EmailService will remain closed" );
            return STATUS.CLOSED;
        }

        LOGGER.trace( getSessionLabel(), () -> "initializing with settings: " + JsonFactory.get().serialize( emailServiceSettings ) );

        final List<EmailServer> servers;
        try
        {
            servers = new ArrayList<>( EmailServerUtil.makeEmailServersMap( this.getPwmApplication().getConfig() ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            setStartupError( e.getErrorInformation() );
            LOGGER.error( getSessionLabel(), () -> "unable to startup email service: " + e.getMessage() );
            return STATUS.CLOSED;
        }

        if ( servers.isEmpty() )
        {
            LOGGER.debug( getSessionLabel(), () -> "no email servers configured, will remain closed" );
            return STATUS.CLOSED;
        }

        LOGGER.debug( getSessionLabel(), () -> "starting with settings: " + JsonFactory.get().serialize( emailServiceSettings ) );

        final WorkQueueProcessor.Settings settings = WorkQueueProcessor.Settings.builder()
                .maxEvents( emailServiceSettings.getQueueMaxItems() )
                .retryDiscardAge( emailServiceSettings.getQueueDiscardAge() )
                .retryInterval( emailServiceSettings.getQueueRetryTimeout() )
                .preThreads( emailServiceSettings.getMaxThreads() )
                .build();
        final LocalDBStoredQueue localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue(
                this.getPwmApplication(), this.getPwmApplication().getLocalDB(), LocalDB.DB.EMAIL_QUEUE );

        workQueueProcessor = new WorkQueueProcessor<>( this.getPwmApplication(), this.getSessionLabel(), localDBStoredQueue, settings, new EmailItemProcessor(), this.getClass() );

        connectionPool = new EmailConnectionPool( servers, emailServiceSettings, getSessionLabel() );

        statsLogger.conditionallyExecuteTask();

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
    {
        setStatus( STATUS.CLOSED );
        if ( workQueueProcessor != null )
        {
            workQueueProcessor.close();
            workQueueProcessor = null;
        }
        if ( connectionPool != null )
        {
            connectionPool.close();
            connectionPool = EmailConnectionPool.emptyConnectionPool();
        }
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        if ( getStartupError() != null )
        {
            return Collections.singletonList( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.ServiceClosed,
                    this.getClass().getSimpleName(),
                    getStartupError().toDebugStr() ) );
        }

        if ( getPwmApplication().getLocalDB() == null || getPwmApplication().getLocalDB().status() != LocalDB.Status.OPEN )
        {
            return Collections.singletonList( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.ServiceClosed_LocalDBUnavail,
                    this.getClass().getSimpleName() ) );
        }

        if ( getPwmApplication().getApplicationMode() == PwmApplicationMode.READ_ONLY )
        {
            return Collections.singletonList( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.ServiceClosed_AppReadOnly,
                    this.getClass().getSimpleName() ) );
        }

        final List<HealthRecord> records = new ArrayList<>( );
        {
            final ErrorInformation lastError = lastSendError.get();
            if ( lastError != null )
            {
                records.add( HealthRecord.forMessage(
                        DomainID.systemId(),
                        HealthMessage.Email_SendFailure,
                        lastError.toDebugStr() ) );

            }
        }

        records.addAll( EmailServerUtil.checkAllConfiguredServers( connectionPool.getServers(), getSessionLabel() ) );

        return Collections.unmodifiableList( records );
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        if ( status() == STATUS.OPEN )
        {
            return ServiceInfoBean.builder()
                    .storageMethod( DataStorageMethod.LOCALDB )
                    .debugProperties( stats() )
                    .build();
        }

        return ServiceInfoBean.builder()
                .debugProperties( stats() )
                .build();
    }

    public int queueSize( )
    {
        return workQueueProcessor == null
                ? 0
                : workQueueProcessor.queueSize();
    }

    public Instant eldestItem( )
    {
        return workQueueProcessor == null
                ? null
                : workQueueProcessor.eldestItem();
    }

    private class EmailItemProcessor implements WorkQueueProcessor.ItemProcessor<EmailItemBean>
    {
        @Override
        public WorkQueueProcessor.ProcessResult process( final EmailItemBean workItem )
        {
            return sendItem( workItem );
        }

        @Override
        public String convertToDebugString( final EmailItemBean emailItemBean )
        {
            return emailItemBean.toDebugString();
        }
    }

    private void logStats()
    {
        LOGGER.trace( getSessionLabel(), () -> "stats: " + StringUtil.mapToString( stats() ) );
    }


    private Map<String, String> stats()
    {
        final Map<String, String> stats = new TreeMap<>( );
        if ( workQueueProcessor != null )
        {
            stats.putAll( workQueueProcessor.debugInfo() );
            stats.put( "workQueueSize", String.valueOf( workQueueProcessor.queueSize() ) );
        }
        if ( connectionPool != null )
        {
            stats.put( "idleConnections", Integer.toString( connectionPool.idleConnectionCount() ) );
            stats.put( "activeConnections", Integer.toString( connectionPool.activeConnectionCount() ) );

            for ( final EmailServer emailServer : connectionPool.getServers() )
            {
                final StatisticCounterBundle<EmailServer.ServerStat> serverStats = emailServer.getConnectionStats();
                for ( final EmailServer.ServerStat serverStat : EmailServer.ServerStat.values() )
                {
                    final String name = serverStat.name() + "[" + emailServer.getId() + "]";
                    stats.put( name, String.valueOf( serverStats.get( serverStat ) ) );
                }
                {
                    final String name = "averageSendTime[" + emailServer.getId() + "]";
                    final TimeDuration value = TimeDuration.fromDuration( emailServer.getAverageSendTime().getAverageAsDuration() );
                    stats.put( name, value.asCompactString() );

                }
            }
        }
        stats.put( "maxThreads", String.valueOf( emailServiceSettings.getMaxThreads() ) );

        return Collections.unmodifiableMap( stats );
    }

    private boolean determineIfItemCanBeDelivered( final EmailItemBean emailItem )
    {
        if ( status() != STATUS.OPEN )
        {
            LOGGER.debug( getSessionLabel(), () -> "discarding email send event, no service is not running" );
            return false;
        }

        try
        {
            validateEmailItem( emailItem );
            return true;
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.debug( getSessionLabel(), () -> "discarding email send event: " + e.getMessage() );
        }
        return false;
    }

    private static void validateEmailItem( final EmailItemBean emailItem )
            throws PwmOperationalException
    {


        if ( StringUtil.isEmpty( emailItem.getFrom() ) )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_EMAIL_SEND_FAILURE,
                    "missing from address in email item" ) );
        }

        if ( StringUtil.isEmpty( emailItem.getTo() ) )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_EMAIL_SEND_FAILURE,
                    "missing to address in email item" ) );
        }

        if ( StringUtil.isEmpty( emailItem.getSubject() ) )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_EMAIL_SEND_FAILURE,
                    "missing subject in email item" ) );
        }

        if ( StringUtil.isEmpty( emailItem.getBodyPlain() ) && StringUtil.isEmpty( emailItem.getBodyHtml() ) )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_EMAIL_SEND_FAILURE,
                    "missing body in email item" ) );
        }
    }

    public void submitEmail(
            final EmailItemBean emailItem,
            final UserInfo userInfo,
            final MacroRequest macroRequest
    )
            throws PwmUnrecoverableException
    {
        submitEmailImpl( emailItem, userInfo, macroRequest, false );
    }

    public void submitEmailImmediate(
            final EmailItemBean emailItem,
            final UserInfo userInfo,
            final MacroRequest macroRequest
    )
            throws PwmUnrecoverableException
    {
        submitEmailImpl( emailItem, userInfo, macroRequest, true );
    }

    private void submitEmailImpl(
            final EmailItemBean emailItem,
            final UserInfo userInfo,
            final MacroRequest macroRequest,
            final boolean immediate
    )
            throws PwmUnrecoverableException
    {
        if ( emailItem == null )
        {
            return;
        }

        if ( status() != STATUS.OPEN )
        {
            LOGGER.trace( getSessionLabel(), () -> "email service is closed, discarding email job: " + emailItem.toDebugString() );
            return;
        }

        submitLock.lock();
        try
        {
            final EmailItemBean finalBean;
            {
                EmailItemBean workingItemBean = emailItem;
                if ( ( emailItem.getTo() == null || emailItem.getTo().isEmpty() ) && userInfo != null )
                {
                    final String toAddress = userInfo.getUserEmailAddress();
                    workingItemBean = EmailServerUtil.newEmailToAddress( workingItemBean, toAddress );
                }

                if ( macroRequest != null )
                {
                    workingItemBean = EmailServerUtil.applyMacrosToEmail( workingItemBean, macroRequest );
                }

                if ( StringUtil.isEmpty( workingItemBean.getTo() ) )
                {
                    LOGGER.error( getSessionLabel(), () -> "no destination address available for email, skipping; email: " + emailItem.toDebugString() );
                }

                if ( !determineIfItemCanBeDelivered( emailItem ) )
                {
                    return;
                }
                finalBean = workingItemBean;
            }

            try
            {
                if ( immediate )
                {
                    workQueueProcessor.submitImmediate( finalBean );
                }
                else
                {
                    workQueueProcessor.submit( finalBean );
                }
            }
            catch ( final PwmOperationalException e )
            {
                LOGGER.warn( getSessionLabel(), () -> "unable to add email to queue: " + e.getMessage() );
            }
        }
        finally
        {
            submitLock.unlock();
        }

        statsLogger.conditionallyExecuteTask();
    }


    public static void sendEmailSynchronous(
            final EmailServer emailServer,
            final AppConfig domainConfig,
            final EmailItemBean emailItem,
            final MacroRequest macroRequest,
            final SessionLabel sessionLabel
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        try
        {
            validateEmailItem( emailItem );
            EmailItemBean workingItemBean = emailItem;
            if ( macroRequest != null )
            {
                workingItemBean = EmailServerUtil.applyMacrosToEmail( workingItemBean, macroRequest );
            }
            final Transport transport = EmailServerUtil.makeSmtpTransport( emailServer, sessionLabel );
            final List<Message> messages = EmailServerUtil.convertEmailItemToMessages(
                    workingItemBean,
                    domainConfig,
                    emailServer,
                    sessionLabel
            );

            for ( final Message message : messages )
            {
                message.saveChanges();
                transport.sendMessage( message, message.getAllRecipients() );
            }
            transport.close();
        }
        catch ( final MessagingException e )
        {
            final String errorMsg = "error sending message: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_EMAIL_SEND_FAILURE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    private WorkQueueProcessor.ProcessResult sendItem( final EmailItemBean emailItemBean )
    {
        try
        {
            executeEmailSend( emailItemBean );
            return WorkQueueProcessor.ProcessResult.SUCCESS;
        }
        catch ( final MessagingException | PwmException e )
        {
            if ( EmailServerUtil.examineSendFailure( e, emailServiceSettings.getRetryableStatusResponses(), getSessionLabel() ) )
            {
                LOGGER.error( getSessionLabel(), () -> "error sending email (" + e.getMessage() + ") "
                        + emailItemBean.toDebugString() + ", will retry" );
                StatisticsClient.incrementStat( getPwmApplication(), Statistic.EMAIL_SEND_FAILURES );
                return WorkQueueProcessor.ProcessResult.RETRY;
            }
            else
            {
                LOGGER.error( getSessionLabel(), () -> "error sending email (" + e.getMessage() + ") "
                        + emailItemBean.toDebugString() + ", permanent failure, discarding message" );
                StatisticsClient.incrementStat( getPwmApplication(), Statistic.EMAIL_SEND_DISCARDS );
                return WorkQueueProcessor.ProcessResult.FAILED;
            }
        }
    }


    private void executeEmailSend( final EmailItemBean emailItemBean )
            throws PwmUnrecoverableException, MessagingException
    {
        final Instant startTime = Instant.now();
        EmailConnection emailConnection = null;

        try
        {
            emailConnection = connectionPool.getConnection();

            final List<Message> messages = EmailServerUtil.convertEmailItemToMessages(
                    emailItemBean,
                    this.getPwmApplication().getConfig(),
                    emailConnection.getEmailServer(),
                    getSessionLabel()
            );

            for ( final Message message : messages )
            {
                message.saveChanges();
                emailConnection.getTransport().sendMessage( message, message.getAllRecipients() );
            }

            emailConnection.incrementSentItems();
            emailConnection.getEmailServer().getConnectionStats().increment( EmailServer.ServerStat.sendCount );
            final TimeDuration sendTime = TimeDuration.fromCurrent( startTime );
            emailConnection.getEmailServer().getAverageSendTime().update( sendTime.asMillis() );
            lastSendError.set( null );

            LOGGER.debug( getSessionLabel(), () -> "sent email: " + emailItemBean.toDebugString(), sendTime );
            StatisticsClient.incrementStat( getPwmApplication(), Statistic.EMAIL_SEND_SUCCESSES );
        }
        catch ( final MessagingException | PwmException e )
        {
            final ErrorInformation errorInformation;
            if ( e instanceof PwmException )
            {
                errorInformation = ( ( PwmException ) e ).getErrorInformation();
            }
            else
            {
                final String errorMsg = "error sending email: " + e.getMessage();
                errorInformation = new ErrorInformation(
                        PwmError.ERROR_EMAIL_SEND_FAILURE,
                        errorMsg,
                        new String[] {
                                emailItemBean.toDebugString(),
                                JavaHelper.readHostileExceptionMessage( e ),
                        }
                );
            }

            if ( emailConnection != null )
            {
                lastSendError.set( errorInformation );
                emailConnection.getEmailServer().getConnectionStats().increment( EmailServer.ServerStat.sendFailures );

            }
            LOGGER.error( errorInformation );
            throw e;
        }
        finally
        {
            if ( emailConnection != null )
            {
                connectionPool.returnEmailConnection( emailConnection );
            }
        }

        statsLogger.conditionallyExecuteTask();
    }


}

