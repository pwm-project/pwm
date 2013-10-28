/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.util.intruder;

import password.pwm.PwmService;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataStoreException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.DataStore;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.util.*;

class DataStoreRecordStore implements RecordStore {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(DataStoreRecordStore.class);
    private static final int MAX_REMOVALS_PER_CYCLE = 10 * 1000;

    private final IntruderManager intruderManager;
    private final DataStore dataStore;

    private Date eldestRecord = new Date(0);

    public DataStoreRecordStore(final DataStore dataStore, final IntruderManager intruderManager) {
        this.dataStore = dataStore;
        this.intruderManager = intruderManager;
    }

    public IntruderRecord read(final String key) {
        if (key == null || key.length() < 1) {
            return null;
        }

        final String value;
        try {
            value = dataStore.get(key);
        } catch (PwmDataStoreException e) {
            LOGGER.error("error reading stored intruder record: " + e.getMessage());
            return null;
        }

        if (value == null || value.length() < 1) {
            return null;
        }

        try {
            return Helper.getGson().fromJson(value,IntruderRecord.class);
        } catch (Exception e) {
            LOGGER.error("error decoding IntruderRecord:" + e.getMessage());
        }

        //read failed, try to delete record
        try { dataStore.remove(key); } catch (PwmDataStoreException e) { /*noop*/ }
        return null;
    }

    @Override
    public void write(final String key, final IntruderRecord record) throws PwmOperationalException {
        final String jsonRecord = Helper.getGson().toJson(record);
        try {
            dataStore.put(key, jsonRecord);
        } catch (PwmDataStoreException e) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,"error writing to LocalDB: " + e.getMessage()));
        }
    }

    @Override
    public Iterator<IntruderRecord> iterator() throws PwmOperationalException {
        try {
            return new RecordIterator(dataStore.iterator());
        } catch (PwmDataStoreException e) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"iterator unavailable:" + e.getMessage());
        }
    }

    private class RecordIterator implements Iterator<IntruderRecord> {
        private final Iterator<String> dbIterator;

        private RecordIterator(Iterator<String> dbIterator) {
            this.dbIterator = dbIterator;
        }

        @Override
        public boolean hasNext() {
            return dbIterator.hasNext();
        }

        @Override
        public IntruderRecord next() {
            final String key = dbIterator.next();
            return read(key);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }


    @Override
    public void cleanup(final TimeDuration maxRecordAge) {
        if (TimeDuration.fromCurrent(eldestRecord).isShorterThan(maxRecordAge)) {
            return;
        }
        eldestRecord = new Date();

        final long startTime = System.currentTimeMillis();
        int recordsExamined = 0;
        int recordsRemoved = 0;

        boolean complete = false;

        while (!complete && intruderManager.status() == PwmService.STATUS.OPEN) {

            List<String> recordsToRemove = discoverPurgableKeys(maxRecordAge);
            if (recordsToRemove.isEmpty()) {
                complete = true;
            }
            try {
                for (final String key : recordsToRemove) {
                    dataStore.remove(key);
                }
            } catch (PwmDataStoreException e) {
                LOGGER.error("unable to perform removal of identified stale records: " + e.getMessage());
            }
            recordsRemoved += recordsToRemove.size();
            recordsToRemove.clear();
        }
        final TimeDuration totalDuration = TimeDuration.fromCurrent(startTime);
        LOGGER.trace("completed cleanup of intruder table in " + totalDuration.asCompactString() + ", recordsExamined=" + recordsExamined + ", recordsRemoved=" + recordsRemoved);
    }

    private List<String> discoverPurgableKeys(final TimeDuration maxRecordAge) {
        final List<String> recordsToRemove = new ArrayList<String>();
        DataStore.DataStoreIterator<String> dbIterator = null;
        try {
            dbIterator = dataStore.iterator();
            while (intruderManager.status() == PwmService.STATUS.OPEN && dbIterator.hasNext() && recordsToRemove.size() < MAX_REMOVALS_PER_CYCLE) {
                final String key = dbIterator.next();
                final IntruderRecord record = read(key);
                if (record != null) {
                    if (TimeDuration.fromCurrent(record.getTimeStamp()).isLongerThan(maxRecordAge)) {
                        recordsToRemove.add(key);
                    }
                    if (eldestRecord.compareTo(record.getTimeStamp()) == 1) {
                        eldestRecord = record.getTimeStamp();
                    }
                }
            }
        } catch (PwmDataStoreException e) {
            LOGGER.error("unable to perform intruder table cleanup: " + e.getMessage());
        } finally {
            if (dbIterator != null) {
                dbIterator.close();
            }
        }
        return recordsToRemove;
    }
}
