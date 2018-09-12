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
import password.pwm.bean.SessionLabel;
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
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.localdb.WorkQueueProcessor;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.X509Utils;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import java.io.Serializable;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class SyslogAuditService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SyslogAuditService.class );

    private static final int WARNING_WINDOW_MS = 30 * 60 * 1000;
    private static final String SYSLOG_INSTANCE_NAME = "syslog-audit";
    private static final int LENGTH_OVERSIZE = 1024;

    private static final Map<String, String> CEF_VALUE_ESCAPES;


    static
    {
        final Map<String, String> map = new LinkedHashMap<>( );
        map.put( "\\", "\\\\" );
        map.put( "=", "\\=" );
        map.put( "|", "\"" );
        CEF_VALUE_ESCAPES = Collections.unmodifiableMap( map );
    }

    private SyslogIF syslogInstance = null;
    private ErrorInformation lastError = null;
    private List<X509Certificate> certificates = null;
    private WorkQueueProcessor<String> workQueueProcessor;

    private List<SyslogIF> syslogInstances = new ArrayList<>();

    private final Configuration configuration;
    private final PwmApplication pwmApplication;
    private final SyslogOutputFormat syslogOutputFormat;
    private final Map<String, String> syslogCefExtensions;

    public SyslogAuditService( final PwmApplication pwmApplication )
            throws LocalDBException
    {
        syslogOutputFormat = pwmApplication.getConfig().readSettingAsEnum( PwmSetting.AUDIT_SYSLOG_OUTPUT_FORMAT, SyslogOutputFormat.class );
        this.pwmApplication = pwmApplication;
        this.configuration = pwmApplication.getConfig();
        this.certificates = configuration.readSettingAsCertificate( PwmSetting.AUDIT_SYSLOG_CERTIFICATES );
        this.syslogCefExtensions = figureCefSyslogExtensions( configuration );

        final List<String> syslogConfigStringArray = configuration.readSettingAsStringArray( PwmSetting.AUDIT_SYSLOG_SERVERS );
        try
        {
            for ( String entry : syslogConfigStringArray )
            {
                final SyslogConfig syslogCfg = SyslogConfig.fromConfigString( entry );
                final SyslogIF syslogInstance = makeSyslogInstance( syslogCfg );
                syslogInstances.add( syslogInstance );
            }
            LOGGER.trace( "queued service running for syslog entries" );
        }
        catch ( IllegalArgumentException e )
        {
            LOGGER.error( "error parsing syslog configuration for  syslogConfigStrings ERROR: " + e.getMessage() );
        }

        final WorkQueueProcessor.Settings settings = WorkQueueProcessor.Settings.builder()
                .maxEvents( Integer.parseInt( configuration.readAppProperty( AppProperty.QUEUE_SYSLOG_MAX_COUNT ) ) )
                .retryDiscardAge( new TimeDuration( Long.parseLong( configuration.readAppProperty( AppProperty.QUEUE_SYSLOG_MAX_AGE_MS ) ) ) )
                .retryInterval( new TimeDuration( Long.parseLong( configuration.readAppProperty( AppProperty.QUEUE_SYSLOG_RETRY_TIMEOUT_MS ) ) ) )
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
            switch ( syslogOutputFormat )
            {
                case JSON:
                    syslogMsg = convertAuditRecordToSyslogMessage( event, configuration );
                    break;

                case CEF:
                    syslogMsg = convertAuditRecordToCEFMessage( event, configuration );
                    break;

                default:
                    JavaHelper.unhandledSwitchStatement( syslogOutputFormat );
                    throw new IllegalStateException();
            }
        }
        catch ( PwmUnrecoverableException e )
        {
            final String msg = "error generating syslog message text: " + e.getMessage();
            final ErrorInformation errorInfo = new ErrorInformation( PwmError.ERROR_SYSLOG_WRITE_ERROR, msg );
            throw new PwmOperationalException( errorInfo );
        }


        try
        {
            workQueueProcessor.submit( syslogMsg );
        }
        catch ( PwmOperationalException e )
        {
            LOGGER.warn( "unable to add syslog message to queue: " + e.getMessage() );
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

        for ( SyslogIF syslogInstance : syslogInstances )
        {
            try
            {
                syslogInstance.info( auditRecord );
                LOGGER.trace( "delivered syslog audit event: " + auditRecord );
                lastError = null;
                StatisticsManager.incrementStat( this.pwmApplication, Statistic.SYSLOG_MESSAGES_SENT );
                return WorkQueueProcessor.ProcessResult.SUCCESS;
            }
            catch ( Exception e )
            {
                final String errorMsg = "error while sending syslog message to remote service: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SYSLOG_WRITE_ERROR, errorMsg, new String[]
                        {
                                e.getMessage(),
                        }
                );
                lastError = errorInformation;
                LOGGER.error( errorInformation.toDebugStr() );
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


    private static String convertAuditRecordToSyslogMessage(
            final AuditRecord auditRecord,
            final Configuration configuration
    )
    {
        final int maxLength = Integer.parseInt( configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_MAX_MESSAGE_LENGTH ) );
        String jsonValue = "";
        final StringBuilder message = new StringBuilder();
        message.append( PwmConstants.PWM_APP_NAME );
        message.append( " " );

        jsonValue = JsonUtil.serialize( auditRecord );

        if ( message.length() + jsonValue.length() <= maxLength )
        {
            message.append( jsonValue );
        }
        else
        {
            final AuditRecord inputRecord = JsonUtil.cloneUsingJson( auditRecord, auditRecord.getClass() );
            inputRecord.message = inputRecord.message == null ? "" : inputRecord.message;
            inputRecord.narrative = inputRecord.narrative == null ? "" : inputRecord.narrative;

            final String truncateMessage = configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_TRUNCATE_MESSAGE );
            final AuditRecord copiedRecord = JsonUtil.cloneUsingJson( auditRecord, auditRecord.getClass() );
            copiedRecord.message = "";
            copiedRecord.narrative = "";
            final int shortenedMessageLength = message.length()
                    + JsonUtil.serialize( copiedRecord ).length()
                    + truncateMessage.length();
            final int maxMessageAndNarrativeLength = maxLength - ( shortenedMessageLength + ( truncateMessage.length() * 2 ) );
            int maxMessageLength = inputRecord.getMessage().length();
            int maxNarrativeLength = inputRecord.getNarrative().length();

            {
                int top = maxMessageAndNarrativeLength;
                while ( maxMessageLength + maxNarrativeLength > maxMessageAndNarrativeLength )
                {
                    top--;
                    maxMessageLength = Math.min( maxMessageLength, top );
                    maxNarrativeLength = Math.min( maxNarrativeLength, top );
                }
            }

            copiedRecord.message = inputRecord.getMessage().length() > maxMessageLength
                    ? inputRecord.message.substring( 0, maxMessageLength ) + truncateMessage
                    : inputRecord.message;

            copiedRecord.narrative = inputRecord.getNarrative().length() > maxNarrativeLength
                    ? inputRecord.narrative.substring( 0, maxNarrativeLength ) + truncateMessage
                    : inputRecord.narrative;

            message.append( JsonUtil.serialize( copiedRecord ) );
        }

        return message.toString();
    }

    private String convertAuditRecordToCEFMessage(
            final AuditRecord auditRecord,
            final Configuration configuration
    )
            throws PwmUnrecoverableException
    {
        final Map<String, Object> auditRecordMap = JsonUtil.deserializeMap( JsonUtil.serialize( auditRecord ) );
        final String separator = "|";

        final String headerSeverity = configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_HEADER_SEVERITY );
        final String headerProduct = configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_HEADER_PRODUCT );
        final String headerVendor = configuration.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_HEADER_VENDOR );


        final MacroMachine macroMachine = MacroMachine.forNonUserSpecific( pwmApplication, SessionLabel.SYSTEM_LABEL );

        final String auditFieldName = LocaleHelper.getLocalizedMessage(
                PwmConstants.DEFAULT_LOCALE,
                auditRecord.getEventCode().getMessage(),
                configuration
        );

        final StringBuilder cefOutput = new StringBuilder(  );

        // cef declaration:version prefix
        cefOutput.append( "CEF:0" );

        // Device Vendor
        cefOutput.append( separator );
        cefOutput.append( macroMachine.expandMacros( headerVendor ) );

        // Device Product
        cefOutput.append( separator );
        cefOutput.append( macroMachine.expandMacros( headerProduct ) );

        // Device Version
        cefOutput.append( separator );
        cefOutput.append( PwmConstants.SERVLET_VERSION );

        // Device Event Class ID
        cefOutput.append( separator );
        cefOutput.append( auditRecord.getEventCode() );

        // field name
        cefOutput.append( separator );
        cefOutput.append( auditFieldName );

        // severity
        cefOutput.append( separator );
        cefOutput.append( macroMachine.expandMacros( headerSeverity ) );

        boolean extensionAdded = false;
        for ( final Map.Entry<String, String> entry : syslogCefExtensions.entrySet() )
        {
            final Object value = auditRecordMap.get( entry.getKey() );
            if ( value != null )
            {
                if ( !extensionAdded )
                {
                    cefOutput.append( separator );
                    extensionAdded = true;
                }
                else
                {
                    cefOutput.append( " " );
                }
                cefOutput.append( entry.getValue() );
                cefOutput.append( "=" );
                cefOutput.append( escapeCEFValue( value.toString() ) );
            }
        }

        return cefOutput.toString();
    }



    private static String escapeCEFValue( final String value )
    {
        String replacedValue = value;
        for ( final Map.Entry<String, String> entry : CEF_VALUE_ESCAPES.entrySet() )
        {
            final String pattern = entry.getKey();
            final String replacement = entry.getValue();
            replacedValue = replacedValue.replace( pattern, replacement );
        }
        return replacedValue;
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
            catch ( IllegalArgumentException e )
            {
                throw new IllegalArgumentException( "unknown protocol '" + parts[ 0 ] + "'" );
            }

            final int port;
            try
            {
                port = Integer.parseInt( parts[ 2 ] );
            }
            catch ( NumberFormatException e )
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
                                            new X509Utils.CertMatchingTrustManager( configuration, certificates ),
                                    },
                            new java.security.SecureRandom() );
                    return sc.getSocketFactory();
                }
                catch ( NoSuchAlgorithmException | KeyManagementException e )
                {
                    LOGGER.error( "unexpected error loading syslog certificates: " + e.getMessage() );
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

    private Map<String, String> figureCefSyslogExtensions( final Configuration config )
    {
        final String pairSplitter = ":";
        final String keyValueSplitter = ",";
        final String configuredString = config.readAppProperty( AppProperty.AUDIT_SYSLOG_CEF_EXTENSIONS );

        final List<String> pairs = Arrays.asList( configuredString.split( pairSplitter ) );
        final Map<String, String> map = StringUtil.convertStringListToNameValuePair( pairs, keyValueSplitter );
        return Collections.unmodifiableMap( map );
    }
}
