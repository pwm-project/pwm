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

package password.pwm.svc.event;

import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.util.Date;
import java.util.Iterator;

public interface AuditVault {

    void init( PwmApplication pwmApplication,  LocalDB localDB,  Settings settings) throws LocalDBException, PwmException;

    void close();

    int size();

    Date oldestRecord();

    Iterator<AuditRecord> readVault();

    String sizeToDebugString();

    void add(AuditRecord record) throws PwmOperationalException;

    class Settings {
        private long maxRecordCount;
        private TimeDuration maxRecordAge;


        public Settings(final long maxRecordCount, final TimeDuration maxRecordAge) {
            this.maxRecordCount = maxRecordCount;
            this.maxRecordAge = maxRecordAge;
        }

        public long getMaxRecordCount() {
            return maxRecordCount;
        }

        public TimeDuration getMaxRecordAge() {
            return maxRecordAge;
        }
    }

}
