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

package password.pwm.svc.userhistory;

import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMessage;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.svc.event.UserAuditRecord;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.logging.PwmLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UserHistoryService extends AbstractPwmService implements PwmService
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( UserHistoryService.class );

    private UserHistorySettings settings;
    private ServiceInfoBean serviceInfo = ServiceInfoBean.builder().build();

    private ErrorInformation lastError;
    private UserHistoryStore userHistoryStore;

    private PwmDomain pwmDomain;

    public UserHistoryService( )
    {
    }

    @Override
    public STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmDomain = pwmApplication.domains().get( domainID );

        if ( pwmApplication.getApplicationMode() == null || pwmApplication.getApplicationMode() == PwmApplicationMode.READ_ONLY )
        {
            LOGGER.trace( getSessionLabel(), () -> "unable to start - Application is in read-only mode" );
            return STATUS.CLOSED;
        }

        if ( pwmApplication.getLocalDB() == null || pwmApplication.getLocalDB().status() != LocalDB.Status.OPEN )
        {
            LOGGER.trace( getSessionLabel(), () -> "unable to start - LocalDB is not available" );
            return STATUS.CLOSED;
        }

        init( pwmDomain );

        return STATUS.OPEN;
    }

    private void init( final PwmDomain pwmDomain )
    {
        settings = UserHistorySettings.fromConfig( pwmDomain.getConfig() );

        {
            final String debugMsg;
            final DataStorageMethod storageMethodUsed;
            switch ( settings.getUserEventStorageMethod() )
            {
                case AUTO:
                    if ( pwmDomain.getConfig().getAppConfig().hasDbConfigured() )
                    {
                        debugMsg = "starting using auto-configured data store, Remote Database selected";
                        this.userHistoryStore = new DatabaseUserHistory( pwmDomain );
                        storageMethodUsed = DataStorageMethod.DB;
                    }
                    else
                    {
                        debugMsg = "starting using auto-configured data store, LDAP selected";
                        this.userHistoryStore = new LdapXmlUserHistory( pwmDomain );
                        storageMethodUsed = DataStorageMethod.LDAP;
                    }
                    break;

                case DATABASE:
                    this.userHistoryStore = new DatabaseUserHistory( pwmDomain );
                    debugMsg = "starting using Remote Database data store";
                    storageMethodUsed = DataStorageMethod.DB;
                    break;

                case LDAP:
                    this.userHistoryStore = new LdapXmlUserHistory( pwmDomain );
                    debugMsg = "starting using LocalDB data store";
                    storageMethodUsed = DataStorageMethod.LDAP;
                    break;

                default:
                    lastError = new ErrorInformation( PwmError.ERROR_INTERNAL, "unknown storageMethod selected: " + settings.getUserEventStorageMethod() );
                    setStatus( STATUS.CLOSED );
                    return;
            }
            LOGGER.trace( getSessionLabel(), () -> debugMsg );
            serviceInfo = ServiceInfoBean.builder().storageMethod( storageMethodUsed ).build();
        }

        setStatus( STATUS.OPEN );
    }

    @Override
    public void close( )
    {
        setStatus( STATUS.CLOSED );
    }

    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        if ( status() != STATUS.OPEN )
        {
            return Collections.emptyList();
        }

        final List<HealthRecord> healthRecords = new ArrayList<>();

        if ( lastError != null )
        {
            healthRecords.add( HealthRecord.forMessage(
                    DomainID.systemId(),
                    HealthMessage.ServiceClosed,
                    this.getClass().getSimpleName(),
                    lastError.toDebugStr() ) );
        }

        return Collections.unmodifiableList( healthRecords );
    }

    public List<UserAuditRecord> readUserHistory( final SessionLabel sessionLabel, final UserInfo userInfoBean )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final List<UserAuditRecord> results = userHistoryStore.readUserHistory( sessionLabel, userInfoBean );
        LOGGER.trace( sessionLabel, () -> "read " + results.size() + " user history records", () -> TimeDuration.fromCurrent( startTime ) );
        return results;
    }

    public void write( final SessionLabel sessionLabel, final UserAuditRecord auditRecord )
            throws PwmUnrecoverableException
    {
        // add to user history record
        if ( settings.getUserStoredEvents().contains( auditRecord.getEventCode() ) )
        {
            final String perpetratorDN = auditRecord.getPerpetratorDN();
            if ( StringUtil.notEmpty( perpetratorDN ) )
            {
                userHistoryStore.updateUserHistory( sessionLabel, auditRecord );
            }
            else
            {
                LOGGER.trace( sessionLabel, () -> "skipping update of user history, audit record does not have a perpetratorDN: "
                        + JsonFactory.get().serialize( auditRecord ) );
            }
        }

        // update statistics
        StatisticsClient.incrementStat( pwmDomain, Statistic.AUDIT_EVENTS );
    }


    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return serviceInfo;
    }
}
