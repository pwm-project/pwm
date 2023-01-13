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

package password.pwm.svc.sms;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.SmsItemBean;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.httpclient.PwmHttpClientResponse;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.json.JsonFactory;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class SmsQueueService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SmsQueueService.class );

    public static void sendSmsUsingQueue(
            final PwmApplication pwmApplication,
            final String to,
            final String message,
            final SessionLabel sessionLabel,
            final MacroRequest macroRequest
    )
    {
        final SmsQueueService smsQueue = pwmApplication.getSmsQueue();
        if ( smsQueue == null )
        {
            LOGGER.error( sessionLabel, () -> "SMS queue is unavailable, unable to send SMS to: " + to );
            return;
        }

        final SmsItemBean smsItemBean = new SmsItemBean(
                macroRequest.expandMacros( to ),
                macroRequest.expandMacros( message )
        );

        try
        {
            smsQueue.addSmsToQueue( smsItemBean, sessionLabel );
        }
        catch ( final PwmUnrecoverableException e )
        {
            LOGGER.warn( sessionLabel, () -> "unable to add sms to queue: " + e.getMessage() );
        }
    }

    public static final String TOKEN_USER = "%USER%";
    public static final String TOKEN_SENDERID = "%SENDERID%";
    public static final String TOKEN_MESSAGE = "%MESSAGE%";
    public static final String TOKEN_TO = "%TO%";
    public static final String TOKEN_PASS = "%PASS%";
    public static final String TOKEN_REQUESTID = "%REQUESTID%";

    private SmsSendEngine smsSendEngine;

    private WorkQueueProcessor<SmsItemBean> workQueueProcessor;
    private ErrorInformation lastError;

    public SmsQueueService( )
    {
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            LOGGER.warn( getSessionLabel(), () -> "localdb is not open,  will remain closed" );
            return STATUS.CLOSED;
        }

        final WorkQueueProcessor.Settings settings = WorkQueueProcessor.Settings.builder()
                .maxEvents( Integer.parseInt( pwmApplication.getConfig().readAppProperty( AppProperty.QUEUE_SMS_MAX_COUNT ) ) )
                .retryDiscardAge( TimeDuration.of( pwmApplication.getConfig().readSettingAsLong( PwmSetting.SMS_MAX_QUEUE_AGE ), TimeDuration.Unit.SECONDS ) )
                .retryInterval( TimeDuration.of(
                        Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.QUEUE_SMS_RETRY_TIMEOUT_MS ) ),
                        TimeDuration.Unit.MILLISECONDS )
                )
                .build();

        final LocalDBStoredQueue localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue( pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.SMS_QUEUE );

        workQueueProcessor = new WorkQueueProcessor<>( pwmApplication, getSessionLabel(), localDBStoredQueue, settings, new SmsItemProcessor(), this.getClass() );

        smsSendEngine = new SmsSendEngine( pwmApplication, pwmApplication.getConfig() );

        return STATUS.OPEN;
    }

    private class SmsItemProcessor implements WorkQueueProcessor.ItemProcessor<SmsItemBean>
    {
        @Override
        public WorkQueueProcessor.ProcessResult process( final SmsItemBean workItem )
        {
            try
            {
                for ( final String msgPart : splitMessage( workItem.message() ) )
                {
                    smsSendEngine.sendSms( workItem.to(), msgPart, getSessionLabel() );
                }
                StatisticsClient.incrementStat( getPwmApplication(), Statistic.SMS_SEND_SUCCESSES );
                lastError = null;
            }
            catch ( final PwmUnrecoverableException e )
            {
                StatisticsClient.incrementStat( getPwmApplication(), Statistic.SMS_SEND_DISCARDS );
                StatisticsClient.incrementStat( getPwmApplication(), Statistic.SMS_SEND_FAILURES );
                LOGGER.error( () -> "discarding sms message due to permanent failure: " + e.getErrorInformation().toDebugStr() );
                lastError = e.getErrorInformation();
                return WorkQueueProcessor.ProcessResult.FAILED;
            }
            catch ( final PwmOperationalException e )
            {
                StatisticsClient.incrementStat( getPwmApplication(), Statistic.SMS_SEND_FAILURES );
                lastError = e.getErrorInformation();
                return WorkQueueProcessor.ProcessResult.RETRY;
            }

            return WorkQueueProcessor.ProcessResult.SUCCESS;
        }

        @Override
        public String convertToDebugString( final SmsItemBean workItem )
        {
            final Map<String, Object> debugOutputMap = new LinkedHashMap<>();

            debugOutputMap.put( "to", workItem.to() );

            return JsonFactory.get().serializeMap( debugOutputMap );
        }
    }

    public void addSmsToQueue( final SmsItemBean smsItem, final SessionLabel sessionLabel )
            throws PwmUnrecoverableException
    {
        final SmsItemBean shortenedBean = shortenMessageIfNeeded( smsItem, sessionLabel );
        if ( !determineIfItemCanBeDelivered( shortenedBean ) )
        {
            return;
        }

        try
        {
            workQueueProcessor.submit( shortenedBean );
        }
        catch ( final Exception e )
        {
            LOGGER.error( sessionLabel, () -> "error writing to LocalDB queue, discarding sms send request: " + e.getMessage() );
        }
    }

    private SmsItemBean shortenMessageIfNeeded(
            final SmsItemBean smsItem,
            final SessionLabel sessionLabel
    )
            throws PwmUnrecoverableException
    {
        final boolean shorten = getPwmApplication().getConfig().readSettingAsBoolean( PwmSetting.SMS_USE_URL_SHORTENER );
        if ( shorten )
        {
            final String message = smsItem.message();
            final String shortenedMessage = getPwmApplication().getUrlShortener().shortenUrlInText( message, sessionLabel );
            return new SmsItemBean( smsItem.to(), shortenedMessage );
        }
        return smsItem;
    }

    private boolean determineIfItemCanBeDelivered( final SmsItemBean smsItem )
    {
        if ( !getPwmApplication().getConfig().isSmsConfigured() )
        {
            return false;
        }

        if ( StringUtil.isEmpty( smsItem.to() ) )
        {
            LOGGER.debug( () -> "discarding sms send event (no to address) " + smsItem );
            return false;
        }

        if ( StringUtil.isEmpty( smsItem.message() ) )
        {
            LOGGER.debug( () -> "discarding sms send event (no message) " + smsItem );
            return false;
        }

        return true;
    }

    @Override
    public void shutdownImpl( )
    {
        if ( workQueueProcessor != null )
        {
            workQueueProcessor.close();
        }
        workQueueProcessor = null;

        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        if ( lastError != null )
        {
            return Collections.singletonList( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.SMS_SendFailure,
                    lastError.toDebugStr() ) );
        }
        return Collections.emptyList();
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
            return ServiceInfoBean.builder()
                    .storageMethod( DataStorageMethod.LOCALDB )
                    .debugProperties( debugItems ).build();
        }

        return ServiceInfoBean.builder().debugProperties( debugItems ).build();
    }

    private List<String> splitMessage( final String input )
    {
        final int size = ( int ) getPwmApplication().getConfig().readSettingAsLong( PwmSetting.SMS_MAX_TEXT_LENGTH );

        final List<String> returnObj = new ArrayList<>( ( input.length() + size - 1 ) / size );

        for ( int start = 0; start < input.length(); start += size )
        {
            returnObj.add( input.substring( start, Math.min( input.length(), start + size ) ) );
        }
        return returnObj;
    }


    public static PwmHttpClientResponse sendDirectMessage(
            final PwmDomain pwmDomain,
            final DomainConfig domainConfig,
            final SessionLabel sessionLabel,
            final SmsItemBean smsItemBean

    )
            throws PwmUnrecoverableException, PwmOperationalException
    {
        final SmsSendEngine smsSendEngine = new SmsSendEngine( pwmDomain.getPwmApplication(), domainConfig.getAppConfig() );
        smsSendEngine.sendSms( smsItemBean.to(), smsItemBean.message(), sessionLabel );
        return smsSendEngine.getLastResponse();
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

}
