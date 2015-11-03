/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.svc.report;

import password.pwm.error.ErrorInformation;
import password.pwm.svc.stats.EventRateMeter;
import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.util.Date;

public class ReportStatusInfo implements Serializable {
    private Date startDate;
    private Date finishDate;
    private boolean inProgress;
    private int count;
    private int updated;
    private int total;
    private EventRateMeter eventRateMeter = new EventRateMeter(TimeDuration.MINUTE);
    private int errors;
    private ErrorInformation lastError;
    private String settingsHash;
    private ReportEngineProcess currentProcess = ReportEngineProcess.None;

    public enum ReportEngineProcess {
        RollOver,
        DredgeTask,
        None,
    }


    public ReportStatusInfo(String settingsHash) {
        this.settingsHash = settingsHash;
    }

    public String getSettingsHash() {
        return settingsHash;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getFinishDate() {
        return finishDate;
    }

    public void setFinishDate(Date finishDate) {
        this.finishDate = finishDate;
    }

    public boolean isInProgress() {
        return inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public EventRateMeter getEventRateMeter() {
        return eventRateMeter;
    }

    public void setEventRateMeter(EventRateMeter eventRateMeter) {
        this.eventRateMeter = eventRateMeter;
    }

    public int getErrors() {
        return errors;
    }

    public void setErrors(int errors) {
        this.errors = errors;
    }

    public ErrorInformation getLastError() {
        return lastError;
    }

    public void setLastError(ErrorInformation lastError) {
        this.lastError = lastError;
    }

    public ReportEngineProcess getCurrentProcess() {
        return currentProcess;
    }

    public void setCurrentProcess(ReportEngineProcess currentProcess) {
        this.currentProcess = currentProcess;
    }
}
