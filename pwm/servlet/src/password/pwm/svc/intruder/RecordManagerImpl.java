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

package password.pwm.svc.intruder;

import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.ClosableIterator;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureEngine;

class RecordManagerImpl implements RecordManager {
    private static final PwmLogger LOGGER = PwmLogger.forClass(RecordManagerImpl.class);

    private final RecordType recordType;
    private final RecordStore recordStore;
    private final IntruderSettings settings;

    RecordManagerImpl(final RecordType recordType, final RecordStore recordStore, final IntruderSettings settings) {
        this.recordType = recordType;
        this.recordStore = recordStore;
        this.settings = settings;
    }

    public boolean checkSubject(final String subject) {
        if (subject == null || subject.length() < 1) {
            throw new IllegalArgumentException("subject is required value");
        }

        final IntruderRecord record = readIntruderRecord(subject);
        if (record == null) {
            return false;
        }
        if (TimeDuration.fromCurrent(record.getTimeStamp()).isLongerThan(settings.getCheckDuration())) {
            return false;
        }
        if (record.getAttemptCount() >= settings.getCheckCount()) {
            return true;
        }
        return false;
    }

    public void markSubject(final String subject) {
        if (subject == null || subject.length() < 1) {
            throw new IllegalArgumentException("subject is required value");
        }

        IntruderRecord record = readIntruderRecord(subject);

        if (record == null) {
            record = new IntruderRecord(recordType, subject);
        }

        final TimeDuration age = TimeDuration.fromCurrent(record.getTimeStamp());
        if (age.isLongerThan(settings.getCheckDuration())) {
            LOGGER.debug("re-setting existing outdated record=" + JsonUtil.serialize(record) + " (" + age.asCompactString() + ")");
            record = new IntruderRecord(recordType, subject);
        }

        record.incrementAttemptCount();

        writeIntruderRecord(record);
    }

    public void clearSubject(final String subject) {
        final IntruderRecord record = readIntruderRecord(subject);
        if (record == null) {
            return;
        }

        if (record.getAttemptCount() == 0) {
            return;
        }

        record.clearAttemptCount();
        writeIntruderRecord(record);
    }

    public boolean isAlerted(final String subject) {
        final IntruderRecord record = readIntruderRecord(subject);
        return record != null && record.isAlerted();
    }

    public void markAlerted(final String subject)
    {
        final IntruderRecord record = readIntruderRecord(subject);
        if (record == null || record.isAlerted()) {
            return;
        }
        record.setAlerted();
        writeIntruderRecord(record);
    }

    @Override
    public IntruderRecord readIntruderRecord(final String subject) {
        try {
            return recordStore.read(makeKey(subject));
        } catch (PwmException e) {
            LOGGER.error("unable to read read intruder record from storage: " + e.getMessage());
        }
        return null;
    }

    private void writeIntruderRecord(final IntruderRecord intruderRecord) {
        try {
            recordStore.write(makeKey(intruderRecord.getSubject()),intruderRecord);
        } catch (PwmOperationalException e) {
            LOGGER.warn("unexpected error attempting to write intruder record " + JsonUtil.serialize(intruderRecord) + ", error: " + e.getMessage());
        }
    }

    private String makeKey(final String subject) throws PwmOperationalException {
        final String md5sum;
        try {
            md5sum = SecureEngine.md5sum(subject);
        } catch (PwmUnrecoverableException e) {
            throw new PwmOperationalException(PwmError.ERROR_UNKNOWN,"error generating md5sum for intruder record: " + e.getMessage());
        }
        return md5sum + recordType.toString();
    }



    @Override
    public ClosableIterator<IntruderRecord> iterator() throws PwmOperationalException {
        return new RecordIterator<>(recordStore.iterator());
    }

    public static class RecordIterator<IntruderRecord> implements ClosableIterator<IntruderRecord> {
        private ClosableIterator<IntruderRecord> innerIter;

        public RecordIterator(final ClosableIterator<IntruderRecord> recordIterator) throws PwmOperationalException {
            this.innerIter = recordIterator;
        }

        public boolean hasNext() {
            return innerIter.hasNext();
        }

        public IntruderRecord next() {
            IntruderRecord record = null;
            while (innerIter.hasNext() && record == null) {
                record = innerIter.next();
            }
            return record;
        }

        public void remove() {
        }

        public void close() {
            innerIter.close();
        }
    }
}
