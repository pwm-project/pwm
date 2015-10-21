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

package password.pwm.svc.event;

import password.pwm.PwmApplication;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.logging.PwmLogger;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;

public class LocalDbAuditVault implements AuditVault {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LocalDbAuditVault.class);

    private static final int MAX_REMOVALS_PER_ADD = 100;

    private LocalDBStoredQueue auditDB;
    private Settings settings;
    private Date oldestRecord;

    public LocalDbAuditVault(
            final PwmApplication pwmApplication,
            final LocalDB localDB
    )
            throws LocalDBException
    {
        this.auditDB = LocalDBStoredQueue.createLocalDBStoredQueue(pwmApplication, localDB, LocalDB.DB.AUDIT_EVENTS);
    }

    public void init(final Settings settings) {
        this.settings = settings;
    }

    @Override
    public int size() {
        return auditDB.size();
    }

    public Iterator<AuditRecord> readVault() {
        return new IteratorWrapper(auditDB.descendingIterator());
    }

    private static class IteratorWrapper implements Iterator<AuditRecord> {
        private Iterator<String> innerIter;

        private IteratorWrapper(Iterator<String> innerIter) {
            this.innerIter = innerIter;
        }

        @Override
        public boolean hasNext() {
            return innerIter.hasNext();
        }

        @Override
        public AuditRecord next() {
            final String value = innerIter.next();
            return deSerializeRecord(value);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static AuditRecord deSerializeRecord(final String input) {
        final Map<String,String> tempMap = JsonUtil.deserializeStringMap(input);
        String errorMsg = "";
        try {
            if (tempMap != null) {
                final String eventCode = tempMap.get("eventCode");
                if (eventCode != null && eventCode.length() > 0) {
                    final AuditEvent event = AuditEvent.valueOf(eventCode);
                    if (event != null) {
                        switch (event.getType()) {
                            case USER:
                                return JsonUtil.deserialize(input, UserAuditRecord.class);
                            case SYSTEM:
                                return JsonUtil.deserialize(input, SystemAuditRecord.class);
                            case HELPDESK:
                                return JsonUtil.deserialize(input, HelpdeskAuditRecord.class);
                            default:
                                throw new IllegalArgumentException("unknown audit record type: " + event.getType());
                        }
                    }
                }
            }
        } catch (Exception e) {
            errorMsg = e.getMessage();
        }
        LOGGER.debug("unable to deserialize stored record '" + input + "', error: " + errorMsg);
        return null;
    }

    public void add(AuditRecord record) {
        if (record == null) {
            return;
        }

        final String jsonRecord = JsonUtil.serialize(record);
        auditDB.addLast(jsonRecord);
        trim();
    }

    private void trim() {
        if (oldestRecord != null && TimeDuration.fromCurrent(oldestRecord).isLongerThan(settings.getMaxRecordAge())) {
            return;
        }

        if (auditDB.isEmpty()) {
            return;
        }

        int workActions = 0;
        while (workActions < MAX_REMOVALS_PER_ADD && !auditDB.isEmpty()) {
            final String stringFirstRecord = auditDB.getFirst();
            final UserAuditRecord firstRecord = JsonUtil.deserialize(stringFirstRecord, UserAuditRecord.class);
            oldestRecord = firstRecord.getTimestamp();
            if (TimeDuration.fromCurrent(oldestRecord).isLongerThan(settings.getMaxRecordAge())) {
                auditDB.removeFirst();
                workActions++;
            } else {
                return;
            }
        }

        while (auditDB.size() > settings.getMaxRecordCount() && workActions < MAX_REMOVALS_PER_ADD) {
            auditDB.removeFirst();
            workActions++;
        }
    }
}
