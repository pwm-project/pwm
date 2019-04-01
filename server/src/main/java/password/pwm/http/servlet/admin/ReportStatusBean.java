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
import java.util.HashSet;
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
        final Set<ReportService.ReportCommand> availableCommands = new HashSet<>();

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
