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
import java.util.Optional;
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
    private final Map<EmailServer, Optional<ErrorInformation>> serverErrors = new ConcurrentHashMap<>( );
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

        servers.addAll( EmailServerUtil.makeEmailServersMap( pwmApplication.getConfig() ) );

        if ( servers.isEmpty() )
        {
            status = STATUS.CLOSED;
            LOGGER.debug( () -> "no email servers configured, will remain closed" );
            return;
        }

        for ( final EmailServer emailServer : servers )
        {
            serverErrors.put( emailServer, Optional.empty() );
        }

        serverIncrementer = new AtomicLoopIntIncrementer( servers.size() - 1 );

        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            LOGGER.warn( "localdb is not open, EmailService will remain closed" );
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
        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            return Collections.singletonList( HealthRecord.forMessage( HealthMessage.ServiceClosed_LocalDBUnavail, this.getClass().getSimpleName() ) );
        }

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY )
        {
            return Collections.singletonList( HealthRecord.forMessage( HealthMessage.ServiceClosed_AppReadOnly, this.getClass().getSimpleName() ) );
        }

        final List<HealthRecord> records = new ArrayList<>( );
        for ( final Map.Entry<EmailServer, Optional<ErrorInformation>> entry : serverErrors.entrySet() )
        {
            if ( entry.getValue().isPresent() )
            {
                final ErrorInformation errorInformation = entry.getValue().get();
                records.add( HealthRecord.forMessage( HealthMessage.Email_SendFailure, errorInformation.toDebugStr() ) );
            }
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
            LOGGER.error( "discarding email event (no from address): " + emailItem.toDebugString() );
            return false;
        }

        if ( emailItem.getTo() == null || emailItem.getTo().length() < 1 )
        {
            LOGGER.error( "discarding email event (no to address): " + emailItem.toDebugString() );
            return false;
        }

        if ( emailItem.getSubject() == null || emailItem.getSubject().length() < 1 )
        {
            LOGGER.error( "discarding email event (no subject): " + emailItem.toDebugString() );
            return false;
        }

        if ( ( emailItem.getBodyPlain() == null || emailItem.getBodyPlain().length() < 1 ) && ( emailItem.getBodyHtml() == null || emailItem.getBodyHtml().length() < 1 ) )
        {
            LOGGER.error( "discarding email event (no body): " + emailItem.toDebugString() );
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
                LOGGER.error( "no destination address available for email, skipping; email: " + emailItem.toDebugString() );
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
        catch ( PwmOperationalException e )
        {
            LOGGER.warn( "unable to add email to queue: " + e.getMessage() );
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

            serverErrors.put( serverTransport.getEmailServer(), Optional.empty() );

            LOGGER.debug( () -> "sent email: " + emailItemBean.toDebugString() );
            StatisticsManager.incrementStat( pwmApplication, Statistic.EMAIL_SEND_SUCCESSES );
            return WorkQueueProcessor.ProcessResult.SUCCESS;
        }
        catch ( MessagingException | PwmException e )
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
                serverErrors.put( serverTransport.getEmailServer(), Optional.of( errorInformation ) );
            }
            LOGGER.error( errorInformation );

            if ( EmailServerUtil.examineSendFailure( e, retryableStatusResponses ) )
            {
                LOGGER.error( "error sending email (" + e.getMessage() + ") " + emailItemBean.toDebugString() + ", will retry" );
                StatisticsManager.incrementStat( pwmApplication, Statistic.EMAIL_SEND_FAILURES );
                return WorkQueueProcessor.ProcessResult.RETRY;
            }
            else
            {
                LOGGER.error( "error sending email (" + e.getMessage() + ") " + emailItemBean.toDebugString() + ", permanent failure, discarding message" );
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

                serverErrors.put( server, Optional.empty() );
                return new EmailConnection( server, transport );
            }
            catch ( MessagingException e )
            {
                final String msg = "unable to connect to email server '" + server.toDebugString() + "', error: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_UNREACHABLE, msg );
                serverErrors.put( server, Optional.of( errorInformation ) );
                LOGGER.warn( errorInformation.toDebugStr() );
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

