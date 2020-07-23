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

package password.pwm.svc.email;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.bean.EmailItemBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.AtomicLoopIntIncrementer;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.localdb.WorkQueueProcessor;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Transport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Jason D. Rivard
 */
public class EmailService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( EmailService.class );

    private PwmApplication pwmApplication;
    private final Map<EmailServer, ErrorInformation> serverErrors = new ConcurrentHashMap<>( );
    private ErrorInformation startupError;
    private final List<EmailServer> servers = new ArrayList<>( );
    private WorkQueueProcessor<EmailItemBean> workQueueProcessor;
    private AtomicLoopIntIncrementer serverIncrementer;
    private Set<Integer> retryableStatusResponses = Collections.emptySet();

    private PwmService.STATUS status = STATUS.NEW;

    private final ThreadLocal<EmailConnection> threadLocalTransport = new ThreadLocal<>();

    public void init( final PwmApplication pwmApplication )
            throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;

        try
        {
            servers.addAll( EmailServerUtil.makeEmailServersMap( pwmApplication.getConfig() ) );
        }
        catch ( final PwmUnrecoverableException e )
        {
            startupError = e.getErrorInformation();
            LOGGER.error( () -> "unable to startup email service: " + e.getMessage() );
            status = STATUS.CLOSED;
            return;
        }

        if ( servers.isEmpty() )
        {
            status = STATUS.CLOSED;
            LOGGER.debug( () -> "no email servers configured, will remain closed" );
            return;
        }

        serverIncrementer = AtomicLoopIntIncrementer.builder().ceiling( servers.size() - 1 ).build();

        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            LOGGER.warn( () -> "localdb is not open, EmailService will remain closed" );
            status = STATUS.CLOSED;
            return;
        }

        final WorkQueueProcessor.Settings settings = WorkQueueProcessor.Settings.builder()
                .maxEvents( Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.QUEUE_EMAIL_MAX_COUNT ) ) )
                .retryDiscardAge( TimeDuration.of( pwmApplication.getConfig().readSettingAsLong( PwmSetting.EMAIL_MAX_QUEUE_AGE ), TimeDuration.Unit.SECONDS ) )
                .retryInterval( TimeDuration.of(
                        Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.QUEUE_EMAIL_RETRY_TIMEOUT_MS ) ),
                        TimeDuration.Unit.MILLISECONDS )
                )
                .preThreads( Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.QUEUE_EMAIL_MAX_THREADS ) ) )
                .build();
        final LocalDBStoredQueue localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue( pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.EMAIL_QUEUE );

        workQueueProcessor = new WorkQueueProcessor<>( pwmApplication, localDBStoredQueue, settings, new EmailItemProcessor(), this.getClass() );

        retryableStatusResponses = readRetryableStatusCodes( pwmApplication.getConfig() );

        status = STATUS.OPEN;
    }

    public void close( )
    {
        status = STATUS.CLOSED;
        if ( workQueueProcessor != null )
        {
            workQueueProcessor.close();
        }
    }

    @Override
    public STATUS status( )
    {
        return status;
    }

    public List<HealthRecord> healthCheck( )
    {
        if ( startupError != null )
        {
            return Collections.singletonList( HealthRecord.forMessage( HealthMessage.ServiceClosed, this.getClass().getSimpleName(), startupError.toDebugStr() ) );
        }

        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            return Collections.singletonList( HealthRecord.forMessage( HealthMessage.ServiceClosed_LocalDBUnavail, this.getClass().getSimpleName() ) );
        }

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY )
        {
            return Collections.singletonList( HealthRecord.forMessage( HealthMessage.ServiceClosed_AppReadOnly, this.getClass().getSimpleName() ) );
        }

        final List<HealthRecord> records = new ArrayList<>( );
        final Map<EmailServer, ErrorInformation> localMap = new HashMap<>( serverErrors );
        for ( final Map.Entry<EmailServer, ErrorInformation> entry : localMap.entrySet() )
        {
            final ErrorInformation errorInformation = entry.getValue();
            records.add( HealthRecord.forMessage( HealthMessage.Email_SendFailure, errorInformation.toDebugStr() ) );
        }

        return records;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        final Map<String, String> debugItems = new LinkedHashMap<>();
        if ( workQueueProcessor != null )
        {
            debugItems.putAll( workQueueProcessor.debugInfo() );
        }
        if ( status() == STATUS.OPEN )
        {
            return new ServiceInfoBean( Collections.singletonList( DataStorageMethod.LOCALDB ), debugItems );
        }
        else
        {
            return new ServiceInfoBean( Collections.emptyList(), debugItems );
        }
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

        public String convertToDebugString( final EmailItemBean emailItemBean )
        {
            return emailItemBean.toDebugString();
        }
    }

    private boolean determineIfItemCanBeDelivered( final EmailItemBean emailItem )
    {

        if ( servers.isEmpty() )
        {
            LOGGER.debug( () -> "discarding email send event (no SMTP server address configured) " + emailItem.toDebugString() );
            return false;
        }

        if ( emailItem.getFrom() == null || emailItem.getFrom().length() < 1 )
        {
            LOGGER.error( () -> "discarding email event (no from address): " + emailItem.toDebugString() );
            return false;
        }

        if ( emailItem.getTo() == null || emailItem.getTo().length() < 1 )
        {
            LOGGER.error( () -> "discarding email event (no to address): " + emailItem.toDebugString() );
            return false;
        }

        if ( emailItem.getSubject() == null || emailItem.getSubject().length() < 1 )
        {
            LOGGER.error( () -> "discarding email event (no subject): " + emailItem.toDebugString() );
            return false;
        }

        if ( ( emailItem.getBodyPlain() == null || emailItem.getBodyPlain().length() < 1 ) && ( emailItem.getBodyHtml() == null || emailItem.getBodyHtml().length() < 1 ) )
        {
            LOGGER.error( () -> "discarding email event (no body): " + emailItem.toDebugString() );
            return false;
        }

        return true;
    }

    public void submitEmail(
            final EmailItemBean emailItem,
            final UserInfo userInfo,
            final MacroMachine macroMachine
    )
            throws PwmUnrecoverableException
    {
        submitEmailImpl( emailItem, userInfo, macroMachine, false );
    }

    public void submitEmailImmediate(
            final EmailItemBean emailItem,
            final UserInfo userInfo,
            final MacroMachine macroMachine
    )
            throws PwmUnrecoverableException
    {
        submitEmailImpl( emailItem, userInfo, macroMachine, true );
    }

    private void submitEmailImpl(
            final EmailItemBean emailItem,
            final UserInfo userInfo,
            final MacroMachine macroMachine,
            final boolean immediate
    )
            throws PwmUnrecoverableException
    {
        if ( emailItem == null )
        {
            return;
        }

        final EmailItemBean finalBean;
        {
            EmailItemBean workingItemBean = emailItem;
            if ( ( emailItem.getTo() == null || emailItem.getTo().isEmpty() ) && userInfo != null )
            {
                final String toAddress = userInfo.getUserEmailAddress();
                workingItemBean = EmailServerUtil.newEmailToAddress( workingItemBean, toAddress );
            }

            if ( macroMachine != null )
            {
                workingItemBean = EmailServerUtil.applyMacrosToEmail( workingItemBean, macroMachine );
            }

            if ( workingItemBean.getTo() == null || workingItemBean.getTo().length() < 1 )
            {
                LOGGER.error( () -> "no destination address available for email, skipping; email: " + emailItem.toDebugString() );
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
            LOGGER.warn( () -> "unable to add email to queue: " + e.getMessage() );
        }
    }

    private final AtomicInteger newThreadLocalTransport = new AtomicInteger();
    private final AtomicInteger useExistingConnection = new AtomicInteger();
    private final AtomicInteger useExistingTransport = new AtomicInteger();
    private final AtomicInteger newConnectionCounter = new AtomicInteger();

    private String stats( )
    {
        final Map<String, Integer> map = new HashMap<>();
        map.put( "newThreadLocalTransport", newThreadLocalTransport.get() );
        map.put( "useExistingConnection", newThreadLocalTransport.get() );
        map.put( "useExistingTransport", useExistingTransport.get() );
        map.put( "newConnectionCounter", newConnectionCounter.get() );
        return StringUtil.mapToString( map );
    }

    private WorkQueueProcessor.ProcessResult sendItem( final EmailItemBean emailItemBean )
    {
        EmailConnection serverTransport = null;

        // create a new MimeMessage object (using the Session created above)
        try
        {
            if ( threadLocalTransport.get() == null )
            {

                LOGGER.trace( () -> "initializing new threadLocal transport, stats: " + stats() );
                threadLocalTransport.set( getSmtpTransport( ) );
                newThreadLocalTransport.getAndIncrement();
            }
            else
            {
                LOGGER.trace( () -> "using existing threadLocal transport, stats: " + stats() );
                useExistingTransport.getAndIncrement();
            }

            serverTransport = threadLocalTransport.get();

            if ( !serverTransport.getTransport().isConnected() )
            {
                LOGGER.trace( () -> "connecting threadLocal transport, stats: " + stats() );
                threadLocalTransport.set( getSmtpTransport( ) );
                serverTransport = threadLocalTransport.get();
                newConnectionCounter.getAndIncrement();
            }
            else
            {
                LOGGER.trace( () -> "using existing threadLocal: stats: " + stats() );
                useExistingConnection.getAndIncrement();
            }

            final List<Message> messages = EmailServerUtil.convertEmailItemToMessages(
                    emailItemBean,
                    this.pwmApplication.getConfig(),
                    serverTransport.getEmailServer()
            );

            for ( final Message message : messages )
            {
                message.saveChanges();
                serverTransport.getTransport().sendMessage( message, message.getAllRecipients() );
            }

            serverErrors.remove( serverTransport.getEmailServer() );

            LOGGER.debug( () -> "sent email: " + emailItemBean.toDebugString() );
            StatisticsManager.incrementStat( pwmApplication, Statistic.EMAIL_SEND_SUCCESSES );
            return WorkQueueProcessor.ProcessResult.SUCCESS;
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

            if ( serverTransport != null )
            {
                serverErrors.put( serverTransport.getEmailServer(), errorInformation );
            }
            LOGGER.error( errorInformation );

            if ( EmailServerUtil.examineSendFailure( e, retryableStatusResponses ) )
            {
                LOGGER.error( () -> "error sending email (" + e.getMessage() + ") " + emailItemBean.toDebugString() + ", will retry" );
                StatisticsManager.incrementStat( pwmApplication, Statistic.EMAIL_SEND_FAILURES );
                return WorkQueueProcessor.ProcessResult.RETRY;
            }
            else
            {
                LOGGER.error( () -> "error sending email (" + e.getMessage() + ") " + emailItemBean.toDebugString() + ", permanent failure, discarding message" );
                StatisticsManager.incrementStat( pwmApplication, Statistic.EMAIL_SEND_DISCARDS );
                return WorkQueueProcessor.ProcessResult.FAILED;
            }
        }
    }

    private EmailConnection getSmtpTransport( )
            throws PwmUnrecoverableException
    {

        // the global server incrementer rotates the server list by 1 offset each attempt to get an smtp transport.
        int nextSlot = serverIncrementer.next();

        for ( int i = 0; i < servers.size(); i++ )
        {
            nextSlot = nextSlot >= ( servers.size() - 1 )
                    ? 0
                    : nextSlot + 1;

            final EmailServer server = servers.get( nextSlot );
            try
            {
                final Transport transport = EmailServerUtil.makeSmtpTransport( server );

                serverErrors.remove( server );
                return new EmailConnection( server, transport );
            }
            catch ( final Exception e )
            {
                final String exceptionMsg = JavaHelper.readHostileExceptionMessage( e );
                final String msg = "unable to connect to email server '" + server.toDebugString() + "', error: " + exceptionMsg;
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_UNREACHABLE, msg );
                serverErrors.put( server, errorInformation );
                LOGGER.warn( () -> errorInformation.toDebugStr() );
            }
        }

        throw PwmUnrecoverableException.newException( PwmError.ERROR_SERVICE_UNREACHABLE, "unable to reach any configured email server" );
    }

    private static Set<Integer> readRetryableStatusCodes( final Configuration configuration )
    {
        final String rawAppProp = configuration.readAppProperty( AppProperty.SMTP_RETRYABLE_SEND_RESPONSE_STATUSES );
        if ( StringUtil.isEmpty( rawAppProp ) )
        {
            return Collections.emptySet();
        }

        final Set<Integer> returnData = new HashSet<>();
        for ( final String loopString : rawAppProp.split( "," ) )
        {
            final Integer loopInt = Integer.parseInt( loopString );
            returnData.add( loopInt );
        }
        return Collections.unmodifiableSet( returnData );
    }
}

