/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import password.pwm.svc.report.ReportService;
import password.pwm.svc.report.ReportStatusInfo;
import password.pwm.util.java.PwmNumberFormat;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.localdb.LocalDBException;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ReportStatusBean implements Serializable {
    private Map<String,Object> presentable = new LinkedHashMap<>();
    private ReportStatusInfo raw;
    private boolean controllable;
    private Set<ReportService.ReportCommand> availableCommands;

    public static ReportStatusBean makeReportStatusData(final ReportService reportService, final Locale locale)
            throws LocalDBException
    {
        final PwmNumberFormat numberFormat = PwmNumberFormat.forLocale(locale);

        final ReportStatusBean returnMap = new ReportStatusBean();
        final ReportStatusInfo reportInfo = reportService.getReportStatusInfo();
        final LinkedHashMap<String,Object> presentableMap = new LinkedHashMap<>();
        final Set<ReportService.ReportCommand> availableCommands = new HashSet<>();

        presentableMap.put("Job Engine", reportInfo.getCurrentProcess().getLabel());

        switch (reportInfo.getCurrentProcess()) {
            case RollOver:
            {
                presentableMap.put("Users Processed",
                        numberFormat.format(reportService.getSummaryData().getTotalUsers())
                                + " of " + numberFormat.format(reportService.getTotalRecords()));
                availableCommands.add(ReportService.ReportCommand.Stop);
            }
            break;

            case ReadData:
            {
                presentableMap.put("Users Processed", numberFormat.format(reportInfo.getCount()));
                presentableMap.put("Users Remaining", numberFormat.format(reportService.getWorkQueueSize()));
                if (reportInfo.getJobDuration() != null) {
                    presentableMap.put("Job Time", reportInfo.getJobDuration().asLongString(locale));
                }
                if (reportInfo.getCount() > 0) {
                    final BigDecimal eventRate = reportService.getEventRate().setScale(2, RoundingMode.UP);
                    presentableMap.put("Users/Second", eventRate);
                    if (!eventRate.equals(BigDecimal.ZERO)) {
                        final int usersRemaining = reportService.getWorkQueueSize();
                        final float secondsRemaining = usersRemaining / eventRate.floatValue();
                        final TimeDuration remainingDuration = new TimeDuration(((int) secondsRemaining) * 1000);
                        presentableMap.put("Estimated Time Remaining", remainingDuration.asLongString(locale));
                    }
                }
                availableCommands.add(ReportService.ReportCommand.Stop);
            }
            break;

            case None:
            {
                if (reportInfo.getFinishDate() != null) {
                    presentableMap.put("Last Job Completed", reportInfo.getFinishDate());
                }
                availableCommands.add(ReportService.ReportCommand.Start);
                if (reportService.getTotalRecords() > 0) {
                    availableCommands.add(ReportService.ReportCommand.Clear);
                }

            }
            break;

            default:
                break;
                /* no action */
        }

        {
            if (reportInfo.getErrors() > 0) {
                presentableMap.put("Error Count", numberFormat.format(reportInfo.getErrors()));
            }
            if (reportInfo.getLastError() != null) {
                presentableMap.put("Last Error", reportInfo.getLastError().toDebugStr());
            }
            final int totalRecords = reportService.getTotalRecords();
            presentableMap.put("Records in Cache", numberFormat.format(totalRecords));
            if (totalRecords > 0) {
                presentableMap.put("Mean Record Cache Time", reportService.getSummaryData().getMeanCacheTime());
            }
        }


        returnMap.setControllable(true);
        returnMap.setRaw(reportInfo);
        returnMap.setPresentable(presentableMap);
        returnMap.setAvailableCommands(availableCommands);
        return returnMap;
    }

    public Map<String, Object> getPresentable() {
        return presentable;
    }

    public void setPresentable(final Map<String, Object> presentable) {
        this.presentable = presentable;
    }

    public ReportStatusInfo getRaw() {
        return raw;
    }

    public void setRaw(final ReportStatusInfo raw) {
        this.raw = raw;
    }

    public boolean isControllable() {
        return controllable;
    }

    public void setControllable(final boolean controllable) {
        this.controllable = controllable;
    }

    public Set<ReportService.ReportCommand> getAvailableCommands() {
        return availableCommands;
    }

    public void setAvailableCommands(final Set<ReportService.ReportCommand> availableCommands) {
        this.availableCommands = availableCommands;
    }
}
