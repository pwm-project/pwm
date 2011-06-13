/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2011 The PWM Project
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PwmDBAdaptor implements PwmDB {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmDBAdaptor.class);

    private final PwmDBProvider innerDB;

    private final Set<PwmDBEventListener> EVENT_LISTENERS = new HashSet<PwmDBEventListener>();

    private final SizeCacheManager SIZE_CACHE_MANAGER = new SizeCacheManager();

    PwmDBAdaptor(final PwmDBProvider innerDB) {
        if (innerDB == null) {
            throw new IllegalArgumentException("innerDB can not be null");
        }

        this.innerDB = innerDB;

    }

    public File getFileLocation() {
        return innerDB.getFileLocation();
    }

    @WriteOperation
    public void close() throws PwmDBException {
        innerDB.close();
    }

    public boolean contains(final DB db, final String key) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        ParameterValidator.validateKeyValue(key);

        final boolean value = innerDB.contains(db, key);
        notifyEvent(EventType.READ, db, key, null);
        return value;
    }


    public String get(final DB db, final String key) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        ParameterValidator.validateKeyValue(key);

        final String value = innerDB.get(db, key);
        notifyEvent(EventType.READ, db, key, value);
        return value;
    }

    @WriteOperation
    public void init(final File dbDirectory, final Map<String, String> initParameters, boolean readOnly) throws PwmDBException {
        innerDB.init(dbDirectory, initParameters, readOnly);
    }

    public Iterator<String> iterator(final DB db) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        final Iterator<String> innerIterator = innerDB.iterator(db);
        return (Iterator<String>) new SizeIterator<String>(db, innerIterator);
    }

    private class SizeIterator<T> implements Iterator {
        private final Iterator<T> innerIterator;
        private final DB db;
        private T key;

        SizeIterator(final DB db, final Iterator<T> innerIterator) {
            this.innerIterator = innerIterator;
            this.db = db;
        }

        public boolean hasNext() {
            return innerIterator.hasNext();
        }

        public T next() {
            key = innerIterator.next();
            return key;
        }

        public void remove() {
            innerIterator.remove();
            try {
                SIZE_CACHE_MANAGER.decrementSize(db);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            try {
                notifyEvent(EventType.WRITE, db, key.toString(), null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @WriteOperation
    public void putAll(final DB db, final Map<String, String> keyValueMap) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        for (final String loopKey : keyValueMap.keySet()) {
            try {
                ParameterValidator.validateKeyValue(loopKey);
                ParameterValidator.validateValueValue(keyValueMap.get(loopKey));
            } catch (NullPointerException e) {
                throw new NullPointerException(e.getMessage() + " for transaction record: '" + loopKey + "'");
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(e.getMessage() + " for transaction record: '" + loopKey + "'");
            }
        }

        try {
            innerDB.putAll(db, keyValueMap);
        } finally {
            SIZE_CACHE_MANAGER.clearSize(db);
        }

        for (final String loopKey : keyValueMap.keySet()) {
            notifyEvent(EventType.WRITE, db, loopKey, keyValueMap.get(loopKey));
        }

    }

    @WriteOperation
    public boolean put(final DB db, final String key, final String value) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        ParameterValidator.validateKeyValue(key);
        ParameterValidator.validateValueValue(value);

        final boolean preExisting = innerDB.put(db, key, value);
        if (!preExisting) {
            SIZE_CACHE_MANAGER.incrementSize(db);
        }

        notifyEvent(EventType.WRITE, db, key, value);

        return preExisting;
    }

    @WriteOperation
    public boolean remove(final DB db, final String key) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        ParameterValidator.validateKeyValue(key);

        final boolean result = innerDB.remove(db, key);
        if (result) {
            SIZE_CACHE_MANAGER.decrementSize(db);
        }

        notifyEvent(EventType.WRITE, db, key, null);

        return result;
    }

    @WriteOperation
    public void removeAll(final DB db, final Collection<String> keys) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        for (final String loopKey : keys) {
            try {
                ParameterValidator.validateValueValue(loopKey);
            } catch (NullPointerException e) {
                throw new NullPointerException(e.getMessage() + " for transaction record: '" + loopKey + "'");
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(e.getMessage() + " for transaction record: '" + loopKey + "'");
            }
        }

        innerDB.removeAll(db, keys);

        try {
            innerDB.removeAll(db, keys);
        } finally {
            SIZE_CACHE_MANAGER.clearSize(db);
        }

        for (final String key : keys) {
            notifyEvent(EventType.WRITE, db, key, null);
        }
    }

    public void returnIterator(final DB db) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        innerDB.returnIterator(db);
    }

    public int size(final DB db) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        return SIZE_CACHE_MANAGER.getSizeForDB(db, innerDB);
    }

    @WriteOperation
    public void truncate(final DB db) throws PwmDBException {
        ParameterValidator.validateDBValue(db);
        try {
            innerDB.truncate(db);
        } finally {
            SIZE_CACHE_MANAGER.clearSize(db);
        }
    }

    public void addEventListener(final PwmDBEventListener eventListener) {
        EVENT_LISTENERS.add(eventListener);
    }

    public void removeEventListener(final PwmDBEventListener eventListener) {
        EVENT_LISTENERS.remove(eventListener);
    }

    private void notifyEvent(final EventType eventType, final DB DB, final String key, final String value) {

        final PwmDBEvent eventInfo = new PwmDBEvent() {
            public EventType getEventType() {
                return eventType;
            }

            public DB getDB() {
                return DB;
            }

            public String getKey() {
                return key;
            }

            public String getValue() {
                return value;
            }
        };

        for (final PwmDBEventListener eventListener : EVENT_LISTENERS) {
            eventListener.processAction(eventInfo);
        }

    }

    public Status getStatus() {
        if (innerDB == null) {
            return Status.CLOSED;
        }

        return innerDB.getStatus();
    }

    private static class SizeCacheManager {
        private static final Integer CACHE_DIRTY = -1;
        private static final Integer CACHE_WORKING = -2;

        private final ConcurrentMap<DB, Integer> sizeCache = new ConcurrentHashMap<DB, Integer>();

        private SizeCacheManager() {
            for (final DB db : DB.values()) {
                sizeCache.put(db, CACHE_DIRTY);
            }
        }

        private void incrementSize(final DB db) {
            modifySize(db, +1);
        }

        private void decrementSize(final DB db) {
            modifySize(db, -1);
        }

        private void modifySize(final DB db, final int amount) {
            // retrieve the current cache size.
            final Integer cachedSize = sizeCache.get(db);

            //update the cached value only if there is a meaningful value cached.
            if (cachedSize >= 0) {

                // calculate the new value
                final int newSize = cachedSize + amount;

                // replace the cached value with the new value, only if it hasn't been touched by another thread since it was
                // retrieved from the cache a few lines ago.
                if (!sizeCache.replace(db, cachedSize, newSize)) {

                    // the cache was modified by some other thread, and so is no longer accurate.  Mark it dirty.
                    clearSize(db);
                }
            }

        }

        private void clearSize(final DB db) {
            sizeCache.put(db, CACHE_DIRTY);
        }

        private int getSizeForDB(final DB db, final PwmDBProvider pwmDBProvider) throws PwmDBException {
            // get the cached size out of the cache store
            final Integer cachedSize = sizeCache.get(db);

            if (cachedSize != null && cachedSize >= 0) {
                // if there is a good cache value and its not dirty (-1) or being populated by another thread (-2)
                return cachedSize;
            }

            final long beginTime = System.currentTimeMillis();

            // mark the cache as population in progress
            sizeCache.put(db, CACHE_WORKING);

            // get the "real" value.  this is the line that might take a long time
            final int theSize = pwmDBProvider.size(db);
            final TimeDuration timeDuration = TimeDuration.fromCurrent(beginTime);

            // so long as nothing else has touched the cache (perhaps another thread populated it, or someone else marked it dirty, then
            // go ahead and update it.
            final boolean savedInCache = sizeCache.replace(db, CACHE_WORKING, theSize);

            final StringBuilder debugMsg = new StringBuilder();
            debugMsg.append("performed real size lookup of ").append(theSize).append(" for ").append(db);
            debugMsg.append(": ").append(timeDuration.asCompactString());
            debugMsg.append(savedInCache ? ", cached" : ", not cached");
            LOGGER.debug(debugMsg);

            return theSize;
        }

    }

    private static class ParameterValidator {
        private static void validateDBValue(final PwmDB.DB db) {
            if (db == null) {
                throw new NullPointerException("db cannot be null");
            }
        }

        private static void validateKeyValue(final String key) {
            if (key == null) {
                throw new NullPointerException("key cannot be null");
            }

            if (key.length() < 0) {
                throw new IllegalArgumentException("key length cannot be zero length");
            }

            if (key.length() > PwmDB.MAX_KEY_LENGTH) {
                throw new IllegalArgumentException("key length " + key.length() + " is greater than max " + PwmDB.MAX_KEY_LENGTH);
            }
        }

        private static void validateValueValue(final String value) {
            if (value == null) {
                throw new NullPointerException("value cannot be null");
            }

            if (value.length() > PwmDB.MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("value length " + value.length() + " is greater than max " + PwmDB.MAX_VALUE_LENGTH);
            }
        }
    }
}
