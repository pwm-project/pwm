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

package password.pwm.svc.event;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
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
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SyslogOutputFormat;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.health.HealthTopic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.localdb.WorkQueueProcessor;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmTrustManager;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
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

    private final List<SyslogIF> syslogInstances;
    private final WorkQueueProcessor<String> workQueueProcessor;
    private final AppConfig appConfig;
    private final PwmApplication pwmApplication;
    private final AuditFormatter auditFormatter;
    private final SessionLabel sessionLabel;


    SyslogAuditService( final PwmApplication pwmApplication, final SessionLabel sessionLabel )
            throws LocalDBException
    {
        this.pwmApplication = pwmApplication;
        this.appConfig = pwmApplication.getConfig();

        this.syslogInstances = makeSyslogIFs( appConfig );
        this.auditFormatter = makeAuditFormatter( appConfig );
        this.workQueueProcessor = makeWorkQueueProcessor( pwmApplication, appConfig );
        this.sessionLabel = sessionLabel;
    }

    private WorkQueueProcessor<String> makeWorkQueueProcessor(
            final PwmApplication pwmApplication,
            final AppConfig appConfig
    )
            throws LocalDBException
    {
        final WorkQueueProcessor.Settings settings = WorkQueueProcessor.Settings.builder()
                .maxEvents( Integer.parseInt( appConfig.readAppProperty( AppProperty.QUEUE_SYSLOG_MAX_COUNT ) ) )
                .retryDiscardAge( TimeDuration.of( Long.parseLong( appConfig.readAppProperty( AppProperty.QUEUE_SYSLOG_MAX_AGE_MS ) ), TimeDuration.Unit.MILLISECONDS ) )
                .retryInterval( TimeDuration.of( Long.parseLong( appConfig.readAppProperty( AppProperty.QUEUE_SYSLOG_RETRY_TIMEOUT_MS ) ), TimeDuration.Unit.MILLISECONDS ) )
                .build();

        final LocalDBStoredQueue localDBStoredQueue = LocalDBStoredQueue.createLocalDBStoredQueue(
                pwmApplication, pwmApplication.getLocalDB(), LocalDB.DB.SYSLOG_QUEUE );

        return new WorkQueueProcessor<>( pwmApplication, sessionLabel, localDBStoredQueue, settings, new SyslogItemProcessor(), this.getClass() );
    }

    private static AuditFormatter makeAuditFormatter( final AppConfig appConfig )
    {
        final SyslogOutputFormat syslogOutputFormat = appConfig.readSettingAsEnum( PwmSetting.AUDIT_SYSLOG_OUTPUT_FORMAT, SyslogOutputFormat.class );
        switch ( syslogOutputFormat )
        {
            case JSON:
                return new JsonAuditFormatter();

            case CEF:
                return new CEFAuditFormatter();

            default:
                MiscUtil.unhandledSwitchStatement( syslogOutputFormat );
                throw new IllegalStateException();
        }
    }

    private static List<SyslogIF> makeSyslogIFs(
            final AppConfig appConfig
    )
    {
        final List<String> syslogConfigStringArray = appConfig.readSettingAsStringArray( PwmSetting.AUDIT_SYSLOG_SERVERS );
        final List<SyslogIF> returnData = new ArrayList<>( syslogConfigStringArray.size() );
        final List<X509Certificate> certificates = appConfig.readSettingAsCertificate( PwmSetting.AUDIT_SYSLOG_CERTIFICATES );

        try
        {
            for ( final String entry : syslogConfigStringArray )
            {
                final SyslogConfig syslogCfg = SyslogConfig.fromConfigString( entry );
                final SyslogIF syslogInstance = makeSyslogInstance( appConfig, certificates, syslogCfg );
                returnData.add( syslogInstance );
            }
            LOGGER.trace( () -> "queued service running for syslog entries" );
        }
        catch ( final IllegalArgumentException e )
        {
            LOGGER.error( () -> "error parsing syslog configuration for  syslogConfigStrings ERROR: " + e.getMessage() );
        }
        return List.copyOf( returnData );
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
            return JsonFactory.get().serialize( workItem );
        }
    }


    private static SyslogIF makeSyslogInstance(
            final AppConfig appConfig,
            final List<X509Certificate> certificates,
            final SyslogConfig syslogConfig
    )
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
                syslogInstance = new LocalTrustSSLTCPNetSyslog( appConfig, certificates );
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

        final int maxLength = Integer.parseInt( appConfig.readAppProperty( AppProperty.AUDIT_SYSLOG_MAX_MESSAGE_LENGTH ) );

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
            LOGGER.warn( sessionLabel, () -> "unable to add syslog message to queue: " + e.getMessage() );
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
                healthRecords.add( HealthRecord.forMessage(
                        DomainID.systemId(),
                        HealthMessage.ServiceError,
                        HealthTopic.Audit,
                        this.getClass().getSimpleName(),
                        errorInformation.toUserStr( PwmConstants.DEFAULT_LOCALE, appConfig ) ) );
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
                StatisticsClient.incrementStat( pwmApplication, Statistic.SYSLOG_MESSAGES_SENT );
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
                LOGGER.error( errorInformation::toDebugStr );
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

    @Value
    @AllArgsConstructor( access = AccessLevel.PRIVATE )
    public static class SyslogConfig
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
            return JsonFactory.get().serialize( this );
        }
    }

    public int queueSize( )
    {
        return workQueueProcessor.queueSize();
    }

    @SuppressFBWarnings( "SE_BAD_FIELD" )
    private static class LocalTrustSyslogWriterClass extends SSLTCPNetSyslogWriter
    {
        private final AppConfig appConfig;
        private final List<X509Certificate> certificates;

        LocalTrustSyslogWriterClass(
                final AppConfig appConfig,
                final List<X509Certificate> certificates
        )
        {
            super();
            this.appConfig = appConfig;
            this.certificates = certificates;
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
                                            PwmTrustManager.createPwmTrustManager( appConfig, certificates ),
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

    @SuppressFBWarnings( "SE_BAD_FIELD" )
    private static class LocalTrustSSLTCPNetSyslog extends SSLTCPNetSyslog
    {
        private final AppConfig appConfig;
        private final List<X509Certificate> certificates;

        LocalTrustSSLTCPNetSyslog(
                final AppConfig appConfig,
                final List<X509Certificate> certificates
        )
        {
            super();
            this.appConfig = appConfig;
            this.certificates = certificates;
        }

        @Override
        public AbstractSyslogWriter createWriter( )
        {
            final LocalTrustSyslogWriterClass newClass = new LocalTrustSyslogWriterClass( appConfig, certificates );
            newClass.initialize( this );
            return newClass;
        }
    }
}
