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
import password.pwm.Helper;

import java.io.File;
import java.util.*;

/**
 * @author Jason D. Rivard                                                 
 */
public class PwmDBFactory {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmDBFactory.class);
    private static final String DEFAULT_IMPLEMENTATION = Berkeley_PwmDb.class.getName();

    private static final Collection<String> SIZE_CACHE_USERS = Collections.unmodifiableList(Arrays.asList(
            Berkeley_PwmDb.class.getName(),
            Derby_PwmDb.class.getName(),
            JDBM_PwmDb.class.getName())
    );

    private static final Map<File, PwmDB> singletonMap = Collections.synchronizedMap(new HashMap<File, PwmDB>());


// -------------------------- STATIC METHODS --------------------------

    public static synchronized PwmDB getInstance(final File dbDirectory, String className, final String initString)
            throws Exception
    {
        PwmDB db = singletonMap.get(dbDirectory);

        if (db == null) {
            final long startTime = System.currentTimeMillis();
            className = className != null ? className : DEFAULT_IMPLEMENTATION;
            db = createInstance(className);
            LOGGER.debug("initializing " + className + " pwmDB instance");

            if (SIZE_CACHE_USERS.contains(className)) {
                db = PwmDBSizeCache.createDbSizeCachePwmDB(db);
            }

            db = ValidatingPwmDB.createValidatingPwmDB(db);

            initInstance(db, dbDirectory, initString);
            final TimeDuration openTime = new TimeDuration(System.currentTimeMillis() - startTime);
            LOGGER.info("pwmDB open in " + (openTime.asCompactString()) + ", space on disk: " + Helper.formatDiskSize(db.diskSpaceUsed()));
        }

        return db;
    }

    private static PwmDB createInstance(final String className)
            throws Exception
    {
        final PwmDB pwmDB;
        try {
            final Class c = Class.forName(className);
            final Object impl = c.newInstance();
            if (!(impl instanceof PwmDB)) {
                throw new Exception("unable to createSharedHistoryManager new PwmDB, " + className + " is not instance of " + PwmDB.class.getName());
            }
            pwmDB = (PwmDB) impl;
        } catch (Exception e) {
            LOGGER.warn("error creating new PwmDB instance: " + e.getClass().getName() + ":" + e.getMessage());
            throw new Exception("Messages instantiating new PwmDB instance: " + e.getMessage(), e);
        }

        return pwmDB;
    }

    private static void initInstance(final PwmDB pwmDB, final File dbFileLocation, final String initString)
            throws Exception
    {
        try {
            if (dbFileLocation.mkdir()) {
                LOGGER.trace("createad directory at " + dbFileLocation.getAbsolutePath());
            }
            pwmDB.init(dbFileLocation, initString);
        } catch (Exception e) {
            LOGGER.warn("error while initializing pwmDB instance: " + e.getMessage());
            throw e;
        }

        LOGGER.trace("db init completed for " + pwmDB.getClass().toString());
    }

    public static class ValidatingPwmDB implements PwmDB {
        private final PwmDB innerDB;

        private ValidatingPwmDB(final PwmDB innerDB) {
            if (innerDB == null) {
                throw new IllegalArgumentException("innerDB can not be null");
            }

            this.innerDB = innerDB;
        }

        public long diskSpaceUsed() {
            return innerDB.diskSpaceUsed();
        }

        public static ValidatingPwmDB createValidatingPwmDB(final PwmDB innerDB) {
            if (innerDB instanceof ValidatingPwmDB) {
                return (ValidatingPwmDB)innerDB;
            }
            return new ValidatingPwmDB(innerDB);
        }

        @WriteOperation
        public void close() throws PwmDBException {
            innerDB.close();
        }

        public boolean contains(final DB db, final String key) throws PwmDBException {
            validateDBValue(db);
            ValidateKeyValue(key);
            return innerDB.contains(db,key);
        }

        public String get(final DB db, final String key) throws PwmDBException {
            validateDBValue(db);
            ValidateKeyValue(key);
            return innerDB.get(db,key);
        }

        @WriteOperation
        public void init(final File dbDirectory, final String initString) throws PwmDBException {
            innerDB.init(dbDirectory, initString);
        }

        public Iterator<TransactionItem> iterator(final DB db) throws PwmDBException {
            validateDBValue(db);
            return innerDB.iterator(db);
        }

        @WriteOperation
        public void putAll(final DB db, final Map<String,String> keyValueMap) throws PwmDBException {
            validateDBValue(db);
            for (final String loopKey : keyValueMap.keySet()) {
                try {
                    ValidateKeyValue(loopKey);
                    validateValueValue(keyValueMap.get(loopKey));
                } catch (NullPointerException e) {
                    throw new NullPointerException(e.getMessage() + " for transaction record: '" + loopKey + "'");
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(e.getMessage() + " for transaction record: '" + loopKey + "'");
                }
            }

            innerDB.putAll(db, keyValueMap);
        }

        @WriteOperation
        public boolean put(final DB db, final String key, final String value) throws PwmDBException {
            validateDBValue(db);
            ValidateKeyValue(key);
            validateValueValue(value);
            return innerDB.put(db,key,value);
        }

        @WriteOperation
        public boolean remove(final DB db, final String key) throws PwmDBException {
            validateDBValue(db);
            ValidateKeyValue(key);
            return innerDB.remove(db,key);
        }

        @WriteOperation
        public void removeAll(final DB db, final Collection<String> keys) throws PwmDBException {
            validateDBValue(db);
            for (final String loopKey : keys) {
                try {
                    validateValueValue(loopKey);
                } catch (NullPointerException e) {
                    throw new NullPointerException(e.getMessage() + " for transaction record: '" + loopKey + "'");
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(e.getMessage() + " for transaction record: '" + loopKey + "'");
                }
            }

            innerDB.removeAll(db, keys);
        }

        public void returnIterator(final DB db) throws PwmDBException {
            validateDBValue(db);
            innerDB.returnIterator(db);
        }

        public int size(final DB db) throws PwmDBException {
            validateDBValue(db);
            return innerDB.size(db);
        }

        @WriteOperation
        public void truncate(final DB db) throws PwmDBException {
            validateDBValue(db);
            innerDB.truncate(db);
        }

        private void validateDBValue(final DB db) {
            if (db == null) {
                throw new NullPointerException("db cannot be null");
            }
        }

        private void ValidateKeyValue(final String key) {
            if (key == null) {
                throw new NullPointerException("key cannot be null");
            }

            if (key.length() < 0) {
                throw new IllegalArgumentException("key length cannot be zero length");
            }

            if (key.length() > PwmDB.MAX_KEY_LENGTH) {
                throw new IllegalArgumentException("key length cannot be greater than " + PwmDB.MAX_KEY_LENGTH);
            }
        }

        private void validateValueValue(final String value) {
            if (value == null) {
                throw new NullPointerException("value cannot be null");
            }

            if (value.length() > PwmDB.MAX_VALUE_LENGTH) {
                throw new IllegalArgumentException("key length cannot be greater than " + PwmDB.MAX_VALUE_LENGTH);
            }
        }
    }
}
