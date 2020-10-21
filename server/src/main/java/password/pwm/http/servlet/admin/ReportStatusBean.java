/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.http.servlet.admin;

import lombok.Builder;
import lombok.Value;
import password.pwm.http.bean.DisplayElement;
import password.pwm.svc.report.ReportService;
import password.pwm.svc.report.ReportStatusInfo;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Value
@Builder
public class ReportStatusBean implements Serializable
{
    private final List<DisplayElement> presentable;
    private final ReportStatusInfo raw;
    private final boolean controllable;
    private final Set<ReportService.ReportCommand> availableCommands;

    static ReportStatusBean makeReportStatusData( final ReportService reportService, final Locale locale )
    {
        final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale( locale );
        final ReportStatusInfo reportInfo = reportService.getReportStatusInfo();
        final List<DisplayElement> presentableMap = new ArrayList<>();
        final Set<ReportService.ReportCommand> availableCommands = EnumSet.noneOf( ReportService.ReportCommand.class );

        presentableMap.add( new DisplayElement( "jobEngine", DisplayElement.Type.string, "Job Engine", reportInfo.getCurrentProcess().getLabel() ) );

        switch ( reportInfo.getCurrentProcess() )
        {
            case RollOver:
            {
                presentableMap.add( new DisplayElement( "usersProcessed", DisplayElement.Type.string, "Users Processed",
                        numberFormat.format( reportService.getSummaryData().getTotalUsers().intValue() )
                                + " of " + numberFormat.format( reportService.getTotalRecords() ) ) );
                availableCommands.add( ReportService.ReportCommand.Stop );
            }
            break;

            case ReadData:
            {
                presentableMap.add( new DisplayElement( "usersProcessed", DisplayElement.Type.string, "Users Processed",
                        numberFormat.format( reportInfo.getCount() ) ) );
                presentableMap.add( new DisplayElement( "usersRemaining", DisplayElement.Type.string, "Users Remaining",
                        numberFormat.format( reportService.getWorkQueueSize() ) ) );
                if ( reportInfo.getJobDuration() != null )
                {
                    presentableMap.add( new DisplayElement( "jobTime", DisplayElement.Type.string, "Job Time",
                            reportInfo.getJobDuration().asLongString( locale ) ) );
                }
                if ( reportInfo.getCount() > 0 )
                {
                    final BigDecimal eventRate = reportService.getEventRate().setScale( 2, RoundingMode.UP );
                    if ( eventRate != null )
                    {
                        presentableMap.add( new DisplayElement( "usersPerSecond", DisplayElement.Type.number, "Users/Second", eventRate.toString() ) );
                    }
                    if ( !BigDecimal.ZERO.equals( eventRate ) )
                    {
                        final int usersRemaining = reportService.getWorkQueueSize();
                        final float secondsRemaining = usersRemaining / eventRate.floatValue();
                        final TimeDuration remainingDuration = TimeDuration.of( ( int ) secondsRemaining, TimeDuration.Unit.SECONDS );
                        presentableMap.add( new DisplayElement( "timeRemaining", DisplayElement.Type.string, "Estimated Time Remaining",
                                remainingDuration.asLongString( locale ) ) );
                    }
                }
                availableCommands.add( ReportService.ReportCommand.Stop );
            }
            break;

            case None:
            {
                if ( reportInfo.getFinishDate() != null )
                {
                    presentableMap.add( new DisplayElement( "lastCompleted", DisplayElement.Type.timestamp,  "Last Job Completed",
                            JavaHelper.toIsoDate( reportInfo.getFinishDate() ) ) );
                }
                availableCommands.add( ReportService.ReportCommand.Start );
                if ( reportService.getTotalRecords() > 0 )
                {
                    availableCommands.add( ReportService.ReportCommand.Clear );
                }

            }
            break;

            default:
                break;
            /* no action */
        }

        {
            if ( reportInfo.getErrors() > 0 )
            {
                presentableMap.add( new DisplayElement( "errorCount", DisplayElement.Type.number, "Error Count", numberFormat.format( reportInfo.getErrors() ) ) );
            }
            if ( reportInfo.getLastError() != null )
            {
                presentableMap.add( new DisplayElement( "lastError", DisplayElement.Type.string, "Last Error", reportInfo.getLastError().toDebugStr() ) );
            }
            final long totalRecords = reportService.getTotalRecords();
            presentableMap.add( new DisplayElement( "recordsInCache", DisplayElement.Type.string, "Records in Cache", numberFormat.format( totalRecords ) ) );
        }

        return ReportStatusBean.builder()
                .controllable( true )
                .raw( reportInfo )
                .presentable( presentableMap )
                .availableCommands( availableCommands )
                .build();
    }
}
