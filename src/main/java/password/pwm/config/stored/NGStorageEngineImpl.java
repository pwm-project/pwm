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
 *
 */

package password.pwm.config.stored;

import password.pwm.bean.UserIdentity;
import password.pwm.config.StoredValue;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class NGStorageEngineImpl implements StorageEngine {
    private final Map<StoredConfigReference, StoredValue> values = new TreeMap<>();
    private final Map<StoredConfigReference, ValueMetaData> metaValues = new TreeMap<>();
    private final ConfigChangeLog changeLog;

    private final ReentrantReadWriteLock bigLock = new ReentrantReadWriteLock();
    private boolean writeLocked = false;

    public NGStorageEngineImpl(
            final Map<StoredConfigReference, StoredValue> values,
            final Map<StoredConfigReference, ValueMetaData> metaValues
    ) {
        this.values.putAll(values);
        this.metaValues.putAll(metaValues);
        changeLog = new ConfigChangeLogImpl(this);
    }

    public ConfigChangeLog changeLog() {
        return changeLog;
    }

    public StoredValue read(StoredConfigReference storedConfigReference) {
        bigLock.readLock().lock();
        try {
            return values.get(storedConfigReference);
        } finally {
            bigLock.readLock().unlock();
        }
    }

    public ValueMetaData readMetaData(final StoredConfigReference storedConfigReference) {
        return metaValues.get(storedConfigReference);
    }

    public void write(StoredConfigReference reference, StoredValue value, UserIdentity userIdentity)
    {
        checkWriteLock();
        bigLock.writeLock().lock();
        try {
            if (values.containsKey(reference)) {
                changeLog.updateChangeLog(reference, values.get(reference), value);
            } else {
                changeLog.updateChangeLog(reference, value);
            }
            values.put(reference, value);
            final ValueMetaData valueMetaData = new ValueMetaData(new Date(), userIdentity);
            metaValues.put(reference, valueMetaData);
        } finally {
            bigLock.writeLock().unlock();
        }
    }

    public void reset(StoredConfigReference reference, UserIdentity userIdentity)
    {
        checkWriteLock();
        bigLock.writeLock().lock();
        try {
            if (values.containsKey(reference)) {
                changeLog.updateChangeLog(reference, values.get(reference), null);
            } else {
                changeLog.updateChangeLog(reference, null);
            }
            values.remove(reference);
            if (metaValues.containsKey(reference)) {
                final ValueMetaData valueMetaData = new ValueMetaData(new Date(), userIdentity);
                metaValues.put(reference, valueMetaData);
            }
        } finally {
            bigLock.writeLock().unlock();
        }
    }

    private void checkWriteLock()
    {
        if (writeLocked) {
            throw new IllegalStateException("attempt to modify writeLock configuration");
        }
    }

    public boolean isWriteLocked() {
        return writeLocked;
    }

    public void writeLock() {
        writeLocked = true;
    }
}
