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

package password.pwm.svc.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.graylog2.syslog4j.SyslogIF;
import org.graylog2.syslog4j.impl.AbstractSyslogConfigIF;
import org.graylog2.syslog4j.impl.AbstractSyslogWriter;
import org.graylog2.syslog4j.impl.backlog.NullSyslogBackLogHandler;
import org.graylog2.syslog4j.impl.net.AbstractNetSyslog;
import org.graylog2.syslog4j.impl.net.tcp.TCPNetSyslog;
import org.graylog2.syslog4j.impl.net.tcp.TCPNetSyslogConfig;
import org.graylog2.syslog4j.impl.net.tcp.ssl.SSLTCPNetSyslog;
import org.graylog2.syslog4j.impl.net.tcp.ssl.SSLTCPNetSyslogConfig;
import org.graylog2.syslog4j.impl.net.tcp.ssl.SSLTCPNetSyslogWriter;
import org.graylog2.syslog4j.impl.net.udp.UDPNetSyslog;
import org.graylog2.syslog4j.impl.net.udp.UDPNetSyslogConfig;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SyslogOutputFormat;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthStatus;
import password.pwm.health.HealthTopic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.localdb.WorkQueueProcessor;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmTrustManager;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.io.Serializable;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class SyslogAuditService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SyslogAuditService.class );

    private static final int WARNING_WINDOW_MS = 30 * 60 * 1000;
    private static final String SYSLOG_INSTANCE_NAME = "syslog-audit";
    private static final int LENGTH_OVERSIZE = 1024;

    private SyslogIF syslogInstance = null;
    private ErrorInformation lastError = null;
    private List<X509Certificate> certificates = null;
    private WorkQueueProcessor<String> workQueueProcessor;

    private List<SyslogIF> syslogInstances = new ArrayList<>();

    private final Configuration configuration;
    private final PwmApplication pwmApplication;
    private final AuditFormatter auditFormatter;


    SyslogAuditService( final PwmApplication pwmApplication )
            throws LocalDBException
    {
        this.pwmApplication = pwmApplication;
        this.configuration = pwmApplication.getConfig();
        this.certificates = configuration.readSettingAsCertificate( PwmSetting.AUDIT_SYSLOG_CERTIFICATES );

        final List<String> syslogConfigStringArray = configuration.readSettingAsStringArray( PwmSetting.AUDIT_SYSLOG_SERVERS );
        try
        {
            for ( final String entry : syslogConfigStringArray )
            {
                final SyslogConfig syslogCfg = SyslogConfig.fromConfigString( entry );
                final SyslogIF syslogInstance = makeSyslogInstance( syslogCfg );
                syslogInstances.add( syslogInstance );
            }
            LOGGER.trace( () -> "queued service running for syslog entries" );
        }
        catch ( final IllegalArgumentException e )
        {
            LOGGER.error( () -> "error parsing syslog configuration for  syslogConfigStrings ERROR: " + e.getMessage() );
        }

        {
            final SyslogOutputFormat syslogOutputFormat = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.AUDIT_SYSLOG_OUTPUT_FORMAT, SyslogOutputFormat.class );
            switch ( syslogOutputFormat )
            {
                case JSON:
                    auditFormatter = new JsonAuditFormatter();
                    break;

                case CEF:
                    auditFormatter = new CEFAuditFormatter();
                    break;

                default:
                    JavaHelper.unhandledSwitchStatement( syslogOutputFormat );
                    throw new IllegalStateException();
            }
        }

        final WorkQueueProcessor.Settings settings = WorkQueueProcessor.Settings.builder()
                .maxEvents( Integer.parseInt( configuration.readAppProperty( AppProperty.QUEUE_SYSLOG_MAX_COUNT ) ) )
                .retryDiscardAge( TimeDuration.of( Long.parseLong( configuration.readAppProperty( AppProperty.QUEUE_SYSLOG_MAX_AGE_MS ) ), TimeDuration.Unit.MILLISECONDS ) )
                .retryInterval( TimeDuration.of( Long.parseLong( configuration.readAppProperty( AppProperty.QUEUE_SYSLOG_RETRY_TIMEOUT_MS ) ), TimeDuration.Unit.MILLISECONDS ) )
                .build();

        final LocalDBStoredQueue localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue( pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.SYSLOG_QUEUE );

        workQueueProcessor = new WorkQueueProcessor<>( pwmApplication, localDBStoredQueue, settings, new SyslogItemProcessor(), this.getClass() );
    }

    private class SyslogItemProcessor implements WorkQueueProcessor.ItemProcessor<String>
    {
        @Override
        public WorkQueueProcessor.ProcessResult process( final String workItem )
        {
            return processEvent( workItem );
        }

        @Override
        public String convertToDebugString( final String workItem )
        {
            return JsonUtil.serialize( workItem );
        }
    }


    private SyslogIF makeSyslogInstance( final SyslogConfig syslogConfig )
    {
        final AbstractSyslogConfigIF syslogConfigIF;
        final AbstractNetSyslog syslogInstance;

        switch ( syslogConfig.getProtocol() )
        {
            case sslTcp:
            case tls:
            {
                syslogConfigIF = new SSLTCPNetSyslogConfig();
                ( ( SSLTCPNetSyslogConfig ) syslogConfigIF ).setBackLogHandlers( Collections.singletonList( new NullSyslogBackLogHandler() ) );
                syslogInstance = new LocalTrustSSLTCPNetSyslog();
            }
            break;

            case tcp:
            {
                syslogConfigIF = new TCPNetSyslogConfig();
                ( ( TCPNetSyslogConfig ) syslogConfigIF ).setBackLogHandlers( Collections.singletonList( new NullSyslogBackLogHandler() ) );
                syslogInstance = new TCPNetSyslog();
            }
            break;

            case udp:
            {
                syslogConfigIF = new UDPNetSyslogConfig();
                syslogInstance = new UDPNetSyslog();
            }
            break;

            default:
                throw new IllegalArgumentException( "unknown protocol type" );
        }

        final int maxLength = Integer.parseInt( configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_MAX_MESSAGE_LENGTH ) );

        syslogConfigIF.setThreaded( false );
        syslogConfigIF.setMaxQueueSize( 0 );
        syslogConfigIF.setMaxMessageLength( maxLength + LENGTH_OVERSIZE );
        syslogConfigIF.setThrowExceptionOnWrite( true );
        syslogConfigIF.setHost( syslogConfig.getHost() );
        syslogConfigIF.setPort( syslogConfig.getPort() );
        syslogInstance.initialize( SYSLOG_INSTANCE_NAME, syslogConfigIF );
        return syslogInstance;
    }

    public void add( final AuditRecord event ) throws PwmOperationalException
    {

        final String syslogMsg;
        try
        {
            syslogMsg = auditFormatter.convertAuditRecordToMessage( pwmApplication, event );
        }
        catch ( final PwmUnrecoverableException e )
        {
            final String msg = "error generating syslog message text: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_SYSLOG_WRITE_ERROR, msg );
            throw new PwmOperationalException( errorInfo );
        }



        try
        {
            workQueueProcessor.submit( syslogMsg );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.warn( () -> "unable to add syslog message to queue: " + e.getMessage() );
        }
    }

    public List<HealthRecord> healthCheck( )
    {
        final List<HealthRecord> healthRecords = new ArrayList<>();
        if ( lastError != null )
        {
            final ErrorInformation errorInformation = lastError;
            if ( TimeDuration.fromCurrent( errorInformation.getDate() ).isShorterThan( WARNING_WINDOW_MS ) )
            {
                healthRecords.add( new HealthRecord( HealthStatus.WARN, HealthTopic.Audit,
                        errorInformation.toUserStr( PwmConstants.DEFAULT_LOCALE, configuration ) ) );
            }
        }
        return healthRecords;
    }

    private WorkQueueProcessor.ProcessResult processEvent( final String auditRecord )
    {

        for ( final SyslogIF syslogInstance : syslogInstances )
        {
            try
            {
                syslogInstance.info( auditRecord );
                LOGGER.trace( () -> "delivered syslog audit event: " + auditRecord );
                lastError = null;
                StatisticsManager.incrementStat( this.pwmApplication, Statistic.SYSLOG_MESSAGES_SENT );
                return WorkQueueProcessor.ProcessResult.SUCCESS;
            }
            catch ( final Exception e )
            {
                final String errorMsg = "error while sending syslog message to remote service: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SYSLOG_WRITE_ERROR, errorMsg, new String[]
                        {
                                e.getMessage(),
                        }
                );
                lastError = errorInformation;
                LOGGER.error( () -> errorInformation.toDebugStr() );
            }
        }
        return WorkQueueProcessor.ProcessResult.RETRY;
    }

    public void close( )
    {
        final SyslogIF syslogIF = syslogInstance;
        if ( syslogIF != null )
        {
            syslogIF.shutdown();
        }
        workQueueProcessor.close();
        syslogInstance = null;
    }

    @Getter
    @AllArgsConstructor( access = AccessLevel.PRIVATE )
    public static class SyslogConfig implements Serializable
    {
        public enum Protocol
        {
            sslTcp, tcp, udp, tls
        }

        private Protocol protocol;
        private String host;
        private int port;

        public static SyslogConfig fromConfigString( final String input ) throws IllegalArgumentException
        {
            if ( input == null )
            {
                throw new IllegalArgumentException( "input cannot be null" );
            }

            final String[] parts = input.split( "," );
            if ( parts.length != 3 )
            {
                throw new IllegalArgumentException( "input must have three comma separated parts." );
            }

            final Protocol protocol;
            try
            {
                protocol = Protocol.valueOf( parts[ 0 ] );
            }
            catch ( final IllegalArgumentException e )
            {
                throw new IllegalArgumentException( "unknown protocol '" + parts[ 0 ] + "'" );
            }

            final int port;
            try
            {
                port = Integer.parseInt( parts[ 2 ] );
            }
            catch ( final NumberFormatException e )
            {
                throw new IllegalArgumentException( "invalid port number '" + parts[ 2 ] + "'" );
            }

            return new SyslogConfig( protocol, parts[ 1 ], port );
        }

        public String toString( )
        {
            return JsonUtil.serialize( this );
        }
    }

    public int queueSize( )
    {
        return workQueueProcessor.queueSize();
    }

    private class LocalTrustSyslogWriterClass extends SSLTCPNetSyslogWriter
    {
        private LocalTrustSyslogWriterClass( )
        {
            super();
        }

        @Override
        protected SocketFactory obtainSocketFactory( )
        {
            if ( certificates != null && certificates.size() >= 1 )
            {
                try
                {
                    final SSLContext sc = SSLContext.getInstance( "SSL" );
                    sc.init( null, new X509TrustManager[]
                                    {
                                            PwmTrustManager.createPwmTrustManager( configuration, certificates ),
                                    },
                            new java.security.SecureRandom() );
                    return sc.getSocketFactory();
                }
                catch ( final NoSuchAlgorithmException | KeyManagementException e )
                {
                    LOGGER.error( () -> "unexpected error loading syslog certificates: " + e.getMessage() );
                }
            }

            return super.obtainSocketFactory();
        }
    }

    private class LocalTrustSSLTCPNetSyslog extends SSLTCPNetSyslog
    {


        @Override
        public AbstractSyslogWriter createWriter( )
        {
            final LocalTrustSyslogWriterClass newClass = new LocalTrustSyslogWriterClass();
            newClass.initialize( this );
            return newClass;
        }
    }
}
