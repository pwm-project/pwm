/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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
import password.pwm.util.java.TimeDuration;

import java.io.Serializable;
import java.time.Instant;

public class ReportStatusInfo implements Serializable {
    private TimeDuration jobDuration = TimeDuration.ZERO;
    private Instant finishDate;
    private int count;
    private int errors;
    private ErrorInformation lastError;
    private String settingsHash;
    private ReportEngineProcess currentProcess = ReportEngineProcess.None;

    public enum ReportEngineProcess {
        RollOver("Initializing"),
        ReadData("Process LDAP Records"),
        None("Idle"),
        SearchLDAP("Searching LDAP"),
        Clear("Clearing Records"),

        ;

        private final String label;

        ReportEngineProcess(final String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }


    public ReportStatusInfo(final String settingsHash) {
        this.settingsHash = settingsHash;
    }

    public String getSettingsHash() {
        return settingsHash;
    }

    public TimeDuration getJobDuration() {
        return jobDuration;
    }

    public void setJobDuration(final TimeDuration jobDuration) {
        this.jobDuration = jobDuration;
    }

    public Instant getFinishDate() {
        return finishDate;
    }

    public void setFinishDate(final Instant finishDate) {
        this.finishDate = finishDate;
    }

    public int getCount() {
        return count;
    }

    public void setCount(final int count) {
        this.count = count;
    }


    public int getErrors() {
        return errors;
    }

    public void setErrors(final int errors) {
        this.errors = errors;
    }

    public ErrorInformation getLastError() {
        return lastError;
    }

    public void setLastError(final ErrorInformation lastError) {
        this.lastError = lastError;
    }

    public ReportEngineProcess getCurrentProcess() {
        return currentProcess;
    }

    public void setCurrentProcess(final ReportEngineProcess currentProcess) {
        this.currentProcess = currentProcess;
    }
}
