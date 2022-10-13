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

package password.pwm.svc.report;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.AbstractPwmService;
import password.pwm.svc.PwmService;
import password.pwm.util.java.StatisticCounterBundle;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;


public class ReportService extends AbstractPwmService implements PwmService
{
    private PwmDomain pwmDomain;
    private ReportSettings settings = ReportSettings.builder().build();

    private final Set<ReportProcess> outstandingReportProcesses = Collections.newSetFromMap( new WeakHashMap<>() );

    private final StatisticCounterBundle<CounterStats> statisticCounterBundle = new StatisticCounterBundle<>( CounterStats.class );

    private enum CounterStats
    {
        ReportsStarted,
        ReportsCompleted,
        RecordsRead,
        RecordReadErrors,
    }

    public ReportService( )
    {
    }

    public ReportSettings getSettings()
    {
        return settings;
    }

    void closeReportProcess( final ReportProcess reportProcess )
    {
        outstandingReportProcesses.remove( reportProcess );

        statisticCounterBundle.increment( CounterStats.ReportsCompleted );

        reportProcess.getResult().ifPresent( result ->
        {
            statisticCounterBundle.increment( CounterStats.RecordsRead, result.getRecordCount() );
            statisticCounterBundle.increment( CounterStats.RecordReadErrors, result.getErrorCount() );
        } );
    }

    public enum ReportCommand
    {
        Start,
        Stop,
        Clear,
    }

    protected STATUS postAbstractInit( final PwmApplication pwmApplication, final DomainID domainID )
            throws PwmException
    {
        this.pwmDomain = pwmApplication.domains().get( domainID );
        this.settings = ReportSettings.readSettingsFromConfig( this.getPwmApplication().getConfig() );
        return STATUS.OPEN;
    }

    public void shutdownImpl( )
    {
        setStatus( STATUS.CLOSED );

        for ( final ReportProcess reportProcess : outstandingReportProcesses )
        {
            if ( reportProcess != null )
            {
                reportProcess.close();
            }
        }
    }

    public ReportProcess createReportProcess(
            final ReportProcessRequest request
    )
    {
        final ReportProcess reportProcess = ReportProcess.createReportProcess( pwmDomain, this, request, settings );
        outstandingReportProcesses.add( reportProcess );
        statisticCounterBundle.increment( CounterStats.ReportsStarted );
        return reportProcess;
    }


    @Override
    public List<HealthRecord> serviceHealthCheck( )
    {
        return Collections.emptyList();
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return ServiceInfoBean.builder()
                .debugProperties( statisticCounterBundle.debugStats( PwmConstants.DEFAULT_LOCALE ) )
                .storageMethod( DataStorageMethod.LDAP ).build();
    }
}
