/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.util.db;

import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PwmDBSizeCache implements PwmDB {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmDBSizeCache.class);

    private final PwmDB innerDB;

    private final Map<DB, Integer> sizeCache = new ConcurrentHashMap<DB, Integer>();
    private final Map<DB, Object> lockMap = new ConcurrentHashMap<DB, Object>();

    PwmDBSizeCache(final PwmDB innerDB) {
        for (final DB loopDB : DB.values()) {
            lockMap.put(loopDB, new Object());
        }

        if (innerDB == null) {
            throw new IllegalArgumentException("innerDB can not be null");
        }

        this.innerDB = innerDB;
    }

    public long diskSpaceUsed() {
        return innerDB.diskSpaceUsed();
    }

    public static PwmDBSizeCache createDbSizeCachePwmDB(final PwmDB innerDB) {
        if (innerDB instanceof PwmDBSizeCache) {
            return (PwmDBSizeCache)innerDB;
        }
        return new PwmDBSizeCache(innerDB);
    }

    @WriteOperation
    public void close() throws PwmDBException {
        innerDB.close();
    }

    public boolean contains(final DB db, final String key) throws PwmDBException {
        return innerDB.contains(db,key);
    }

    public String get(final DB db, final String key) throws PwmDBException {
        return innerDB.get(db,key);
    }

    @WriteOperation
    public void init(final File dbDirectory, final String initString) throws PwmDBException {
        innerDB.init(dbDirectory, initString);
    }

    public Iterator<TransactionItem> iterator(final DB db) throws PwmDBException {
        final Iterator<TransactionItem> innerIterator = innerDB.iterator(db);
        return (Iterator<TransactionItem>) new SizeIterator<TransactionItem>(db, innerIterator);
    }

    private class SizeIterator<T> implements Iterator {
        private final Iterator<T> innerIterator;
        private final DB db;

        SizeIterator(final DB db, final Iterator<T> innerIterator) {
            this.innerIterator = innerIterator;
            this.db = db;
        }

        public boolean hasNext() { return innerIterator.hasNext(); }

        public T next() { return innerIterator.next(); }

        public void remove() {
            innerIterator.remove();
            try { decrementSize(db); } catch (Exception e) { throw new RuntimeException(e); }
        }
    }

    @WriteOperation
    public void putAll(final DB db, final Map<String,String> keyValueMap) throws PwmDBException {
        try {
            innerDB.putAll(db, keyValueMap);
        } finally {
            clearSize(db);
        }
    }

    @WriteOperation
    public boolean put(final DB db, final String key, final String value) throws PwmDBException {
        final boolean preExisting = innerDB.put(db,key,value);
        if (!preExisting) {
            incrementSize(db);
        }
        return preExisting;
    }

    @WriteOperation
    public boolean remove(final DB db, final String key) throws PwmDBException {
        final boolean result = innerDB.remove(db,key);
        if (result) {
            decrementSize(db);
        }
        return result;
    }

    @WriteOperation
    public void removeAll(final DB db, final Collection<String> keys) throws PwmDBException {
        try {
            innerDB.removeAll(db,keys);
        } finally {
            clearSize(db);
        }
    }

    public void returnIterator(final DB db) throws PwmDBException {
        innerDB.returnIterator(db);
    }

    public int size(final DB db) throws PwmDBException {
        final long startTime = System.currentTimeMillis();
        final int theSize;
        final boolean fromCache;

        synchronized (lockMap.get(db)) {
            if (sizeCache.containsKey(db)) {
                theSize = sizeCache.get(db);
                fromCache = true;
            } else {
                final int realSize = innerDB.size(db);
                sizeCache.put(db, realSize);
                theSize = realSize;
                fromCache = false;
            }
        }

        if (!fromCache) {
            final TimeDuration timeDuration = TimeDuration.fromCurrent(startTime);
            final StringBuilder debugMsg = new StringBuilder();
            debugMsg.append("performed real size lookup of ").append(theSize).append(" for ").append(db);
            debugMsg.append(": ").append(timeDuration.asCompactString());

            LOGGER.debug(debugMsg);
        }

        return theSize;
    }

    @WriteOperation
    public void truncate(final DB db) throws PwmDBException {
        try {
            innerDB.truncate(db);
        } finally {
            clearSize(db);
        }
    }

    private void incrementSize(final DB db) {
        incrementSize(db,1);
    }

    private void incrementSize(final DB db, final int amount) {
        synchronized (lockMap.get(db)) {
            if (sizeCache.containsKey(db)) {
                final int currentSize = sizeCache.get(db);
                sizeCache.put(db, currentSize + amount);
            }
        }
    }

    private void decrementSize(final DB db) {
        synchronized (lockMap.get(db)) {
            if (sizeCache.containsKey(db)) {
                final int currentSize = sizeCache.get(db);
                sizeCache.put(db, currentSize - 1);
            }
        }
    }

    private void clearSize(final DB db) {
        synchronized (lockMap.get(db)) {
            sizeCache.remove(db);
        }
    }
}
