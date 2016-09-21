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

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.svc.PwmService;
import password.pwm.util.*;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;
import password.pwm.util.localdb.LocalDBStoredQueue;
import password.pwm.util.logging.PwmLogger;

import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LocalDbAuditVault implements AuditVault {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LocalDbAuditVault.class);

    private LocalDBStoredQueue auditDB;
    private Settings settings;
    private Date oldestRecord;

    private int maxBulkRemovals = 105;

    private ScheduledExecutorService executorService;
    private volatile PwmService.STATUS status = PwmService.STATUS.NEW;


    public LocalDbAuditVault(
    )
            throws LocalDBException
    {
    }

    public void init(
            final PwmApplication pwmApplication,
            final LocalDB localDB,
            final Settings settings
    )
            throws PwmException
    {
        this.settings = settings;
        this.auditDB = LocalDBStoredQueue.createLocalDBStoredQueue(pwmApplication, localDB, LocalDB.DB.AUDIT_EVENTS);
        this.maxBulkRemovals = Integer.parseInt(pwmApplication.getConfig().readAppProperty(AppProperty.AUDIT_EVENTS_LOCALDB_MAX_BULK_REMOVALS));

        readOldestRecord();

        executorService = Executors.newSingleThreadScheduledExecutor(
                Helper.makePwmThreadFactory(
                        Helper.makeThreadName(pwmApplication,this.getClass()) + "-",
                        true
                ));

        status = PwmService.STATUS.OPEN;
        executorService.scheduleWithFixedDelay(new TrimmerThread(), 0, 10, TimeUnit.MINUTES);
    }

    public void close() {
        executorService.shutdown();
        status = PwmService.STATUS.CLOSED;
    }

    public PwmService.STATUS getStatus() {
        return status;
    }

    @Override
    public Date oldestRecord() {
        return oldestRecord;
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

    @Override
    public String sizeToDebugString() {
        final long storedEvents = this.size();
        final long maxEvents = settings.getMaxRecordCount();
        final Percent percent = new Percent(storedEvents, maxEvents);

        return storedEvents + " / " + maxEvents + " (" + percent.pretty(2) + ")";
    }

    private static AuditRecord deSerializeRecord(final String input) {
        final Map<String,String> tempMap = JsonUtil.deserializeStringMap(input);
        String errorMsg = "";
        try {
            if (tempMap != null) {
                final String eventCode = tempMap.get("eventCode");
                if (eventCode != null && eventCode.length() > 0) {
                    final AuditEvent event;
                    try {
                        event = AuditEvent.valueOf(eventCode);
                    } catch (IllegalArgumentException e) {
                        errorMsg = "error de-serializing audit record: " + e.getMessage();
                        LOGGER.error(errorMsg);
                        return null;
                    }
                    final Class clazz = event.getType().getDataClass();
                    final com.google.gson.reflect.TypeToken typeToken = com.google.gson.reflect.TypeToken.get(clazz);
                    return JsonUtil.deserialize(input, typeToken);
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

        if (auditDB.size() > settings.getMaxRecordCount()) {
            removeRecords(1);
        }
    }

    private void readOldestRecord() {
        if (auditDB != null && !auditDB.isEmpty()) {
            final String stringFirstRecord = auditDB.getFirst();
            final UserAuditRecord firstRecord = JsonUtil.deserialize(stringFirstRecord, UserAuditRecord.class);
            oldestRecord = firstRecord.getTimestamp();
        }
    }

    private void removeRecords(int count) {
        auditDB.removeFirst(count);
        readOldestRecord();
    }

    private class TrimmerThread implements Runnable {

        // keep transaction duration around 100ms if possible.
        final TransactionSizeCalculator transactionSizeCalculator = new TransactionSizeCalculator(
                new TransactionSizeCalculator.SettingsBuilder()
                        .setDurationGoal(new TimeDuration(101, TimeUnit.MILLISECONDS))
                        .setMaxTransactions(5003)
                        .setMinTransactions(3)
                        .createSettings()
        );

        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            while (trim(transactionSizeCalculator.getTransactionSize())
                    && status == PwmService.STATUS.OPEN
                    ) {
                final long executeTime = System.currentTimeMillis() - startTime;
                transactionSizeCalculator.recordLastTransactionDuration(executeTime);
                transactionSizeCalculator.pause();
                startTime = System.currentTimeMillis();
            }
        }

        private boolean trim(final int maxRemovals) {
            if (auditDB.isEmpty()) {
                return false;
            }

            if (auditDB.size() > settings.getMaxRecordCount() + maxRemovals) {
                removeRecords(maxRemovals);
                return true;
            }

            int workActions = 0;
            while (oldestRecord != null
                    && workActions < maxRemovals
                    && !auditDB.isEmpty()
                    && status == PwmService.STATUS.OPEN
                    ) {
                if (TimeDuration.fromCurrent(oldestRecord).isLongerThan(settings.getMaxRecordAge())) {
                    removeRecords(1);
                    workActions++;
                } else {
                    break;
                }
            }

            return workActions > 0;
        }
    }
}
