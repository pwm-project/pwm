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
import org.apache.commons.csv.CSVPrinter;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.i18n.Display;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AuditService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AuditService.class );

    private AuditSettings settings;

    private SyslogAuditService syslogManager;
    private ErrorInformation lastError;
    private AuditVault auditVault;
    private final StatisticCounterBundle<DebugKey> statisticCounterBundle = new StatisticCounterBundle<>( DebugKey.class );

    enum DebugKey
    {
        emailsSent,
        writes,
    }

    public AuditService( )
    {
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        settings = AuditSettings.fromConfig( pwmApplication.getConfig() );

        if ( pwmApplication.getApplicationMode() == null || pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY )
        {
            LOGGER.warn( getSessionLabel(), () -> "unable to start - Application is in read-only mode" );
            return STATUS.CLOSED;
        }

        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            LOGGER.warn( getSessionLabel(), () -> "unable to start - LocalDB is not available" );
            return STATUS.CLOSED;
        }

        final List<String> syslogConfigString = pwmApplication.getConfig().readSettingAsStringArray( PwmSetting.AUDIT_SYSLOG_SERVERS );
        if ( syslogConfigString != null && !syslogConfigString.isEmpty() )
        {
            try
            {
                syslogManager = new SyslogAuditService( pwmApplication, getSessionLabel() );
            }
            catch ( final Exception e )
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SYSLOG_WRITE_ERROR, "startup error: " + e.getMessage() );
                LOGGER.error( errorInformation::toDebugStr );
            }
        }

        auditVault = new LocalDbAuditVault();
        auditVault.init( pwmApplication, getSessionLabel(), pwmApplication.getLocalDB(), settings );

        return STATUS.OPEN;
    }

    @Override
    public void shutdownImpl( )
    {
        if ( syslogManager != null )
        {
            syslogManager.close();
        }

        if ( auditVault != null )
        {
            auditVault.close();
        }

        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    public Iterator<AuditRecord> readVault( )
    {
        return auditVault.readVault();
    }

    private void sendAsEmail( final AuditRecord record )
            throws PwmUnrecoverableException
    {
        if ( record == null || record.eventCode() == null )
        {
            return;
        }

        if ( StringUtil.isEmpty( settings.alertFromAddress() ) )
        {
            return;
        }

        switch ( record.eventCode().getType() )
        {
            case SYSTEM:
                for ( final String toAddress : settings.systemEmailAddresses() )
                {
                    sendAsEmail( getPwmApplication(), record, toAddress, settings.alertFromAddress() );
                }
                break;

            case USER:
            case HELPDESK:
            {
                for ( final String toAddress : settings.userEmailAddresses() )
                {
                    sendAsEmail( getPwmApplication(), record, toAddress, settings.alertFromAddress() );
                }
            }
            break;

            default:
                PwmUtil.unhandledSwitchStatement( record.eventCode().getType() );

        }
    }

    private void sendAsEmail(
            final PwmApplication pwmApplication,
            final AuditRecord record,
            final String toAddress,
            final String fromAddress

    )
            throws PwmUnrecoverableException
    {
        final MacroRequest macroRequest = MacroRequest.forNonUserSpecific( pwmApplication, getSessionLabel() );

        String subject = macroRequest.expandMacros( pwmApplication.getConfig().readAppProperty( AppProperty.AUDIT_EVENTS_EMAILSUBJECT ) );
        subject = subject.replace( "%EVENT%", record.eventCode().getLocalizedString( pwmApplication.getConfig(), PwmConstants.DEFAULT_LOCALE ) );

        final String body;
        {
            final String jsonRecord = JsonFactory.get().serialize( record );
            final Map<String, Object> mapRecord = JsonFactory.get().deserializeMap( jsonRecord, String.class, Object.class );
            body = StringUtil.mapToString( mapRecord, "=", "\n" );
        }

        final EmailItemBean emailItem = new EmailItemBean(
                toAddress,
                fromAddress,
                subject,
                body,
                null );

        pwmApplication.getEmailQueue().submitEmail( emailItem, null, macroRequest );
        statisticCounterBundle.increment( DebugKey.emailsSent );
    }

    public Optional<Instant> eldestVaultRecord( )
    {
        if ( status() != STATUS.OPEN || auditVault == null )
        {
            return Optional.empty();
        }

        return Optional.of( auditVault.oldestRecord() );
    }

    public String sizeToDebugString( )
    {
        return auditVault == null
                ? LocaleHelper.getLocalizedMessage( PwmConstants.DEFAULT_LOCALE, Display.Value_NotApplicable, null )
                : auditVault.sizeToDebugString();
    }

    public void submit( final SessionLabel sessionLabel, final AuditRecord auditRecord )
            throws PwmUnrecoverableException
    {
        final String jsonRecord = JsonFactory.get().serialize( auditRecord );

        if ( status() != STATUS.OPEN )
        {
            LOGGER.debug( sessionLabel, () -> "discarding audit event (AuditManager is not open); event=" + jsonRecord );
            return;
        }

        if ( auditRecord.eventCode() == null )
        {
            LOGGER.error( sessionLabel, () -> "discarding audit event, missing event type; event=" + jsonRecord );
            return;
        }

        if ( !settings.permittedEvents().contains( auditRecord.eventCode() ) )
        {
            LOGGER.debug( () -> "discarding event, " + auditRecord.eventCode() + " are being ignored; event=" + jsonRecord );
            return;
        }

        // add to debug log
        LOGGER.info( sessionLabel, () -> "audit event: " + jsonRecord );

        // add to audit db
        if ( auditVault != null )
        {
            try
            {
                auditVault.add( auditRecord );
            }
            catch ( final PwmOperationalException e )
            {
                LOGGER.warn( sessionLabel, () -> "discarding audit event due to storage error: " + e.getMessage() );
            }
        }

        // email alert
        sendAsEmail( auditRecord );

        // add to user history record
        if ( auditRecord instanceof UserAuditRecord )
        {
            final DomainID domainID = auditRecord.domain();
            if ( domainID != null )
            {
                final PwmDomain pwmDomain = getPwmApplication().domains().get( domainID );
                if ( pwmDomain != null )
                {
                    final String perpetratorDN = ( ( UserAuditRecord ) auditRecord ).perpetratorDN();
                    if ( StringUtil.notEmpty( perpetratorDN ) )
                    {
                        pwmDomain.getUserHistoryService().write( sessionLabel, ( UserAuditRecord ) auditRecord );
                    }
                    else
                    {
                        LOGGER.trace( sessionLabel, () -> "skipping update of user history, audit record does not have a perpetratorDN: "
                                + JsonFactory.get().serialize( auditRecord ) );
                    }
                }
            }
        }

        // send to syslog
        if ( syslogManager != null )
        {
            try
            {
                syslogManager.add( auditRecord );
            }
            catch ( final PwmOperationalException e )
            {
                lastError = e.getErrorInformation();
            }
        }

        // update statistics
        statisticCounterBundle.increment( DebugKey.writes );
        StatisticsClient.incrementStat( getPwmApplication(), Statistic.AUDIT_EVENTS );
    }



    @SuppressFBWarnings( "PSC_PRESIZE_COLLECTIONS" )
    public int outputVaultToCsv( final OutputStream outputStream, final Locale locale, final boolean includeHeader )
            throws IOException
    {
        final AppConfig config = getPwmApplication().getConfig();

        final CSVPrinter csvPrinter = PwmUtil.makeCsvPrinter( outputStream );

        csvPrinter.printComment( " " + PwmConstants.PWM_APP_NAME + " audit record output " );
        csvPrinter.printComment( " " + StringUtil.toIsoDate( Instant.now() ) );

        if ( includeHeader )
        {
            final List<String> headers = new ArrayList<>();
            headers.add( "Type" );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_EventCode", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_Timestamp", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_GUID", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_Message", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_Instance", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_PerpetratorID", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_PerpetratorDN", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_TargetID", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_TargetDN", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_SourceAddress", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_SourceHost", config, password.pwm.i18n.Admin.class ) );
            headers.add( LocaleHelper.getLocalizedMessage( locale, "Field_Audit_Domain", config, password.pwm.i18n.Admin.class ) );
            csvPrinter.printRecord( headers );
        }

        int counter = 0;
        for ( final Iterator<AuditRecord> recordIterator = readVault(); recordIterator.hasNext(); )
        {
            final AuditRecord loopRecord = recordIterator.next();
            counter++;

            final List<String> lineOutput = new ArrayList<>();
            lineOutput.add( loopRecord.eventCode().getType().toString() );
            lineOutput.add( loopRecord.eventCode().toString() );
            lineOutput.add( StringUtil.toIsoDate( loopRecord.timestamp() ) );
            lineOutput.add( loopRecord.guid() );
            lineOutput.add( loopRecord.message() == null ? "" : loopRecord.message() );
            if ( loopRecord instanceof SystemAuditRecord )
            {
                lineOutput.add( loopRecord.instance() );
            }
            if ( loopRecord instanceof UserAuditRecord )
            {
                lineOutput.add( ( ( UserAuditRecord ) loopRecord ).perpetratorID() );
                lineOutput.add( ( ( UserAuditRecord ) loopRecord ).perpetratorDN() );
                lineOutput.add( "" );
                lineOutput.add( "" );
                lineOutput.add( ( ( UserAuditRecord ) loopRecord ).sourceAddress() );
                lineOutput.add( ( ( UserAuditRecord ) loopRecord ).sourceHost() );
            }
            if ( loopRecord instanceof HelpdeskAuditRecord )
            {
                lineOutput.add( ( ( HelpdeskAuditRecord ) loopRecord ).perpetratorID() );
                lineOutput.add( ( ( HelpdeskAuditRecord ) loopRecord ).perpetratorDN() );
                lineOutput.add( ( ( HelpdeskAuditRecord ) loopRecord ).targetID() );
                lineOutput.add( ( ( HelpdeskAuditRecord ) loopRecord ).targetDN() );
                lineOutput.add( ( ( HelpdeskAuditRecord ) loopRecord ).sourceAddress() );
                lineOutput.add( ( ( HelpdeskAuditRecord ) loopRecord ).sourceHost() );
            }
            if ( loopRecord.domain() != null )
            {
                lineOutput.add( loopRecord.domain().stringValue() );
            }
            csvPrinter.printRecord( lineOutput );
        }
        csvPrinter.flush();

        return counter;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder()
                .storageMethod( DataStorageMethod.LOCALDB )
                .debugProperties( statisticCounterBundle.debugStats( PwmConstants.DEFAULT_LOCALE ) )
                .build();
    }

    public int syslogQueueSize( )
    {
        return syslogManager != null ? syslogManager.queueSize() : 0;
    }
}
