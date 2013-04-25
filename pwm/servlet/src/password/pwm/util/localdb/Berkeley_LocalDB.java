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

package password.pwm.util.localdb;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.collections.StoredMap;
import com.sleepycat.je.*;
import com.sleepycat.util.RuntimeExceptionWrapper;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static password.pwm.util.localdb.LocalDB.DB;

/**
 * @author Jason D. Rivard
 */
public class Berkeley_LocalDB implements LocalDBProvider {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Berkeley_LocalDB.class, true);

    private final static boolean IS_TRANSACTIONAL = true;
    private final static int OPEN_RETRY_SECONDS = 60;
    private final static int CLOSE_RETRY_SECONDS = 120;
    private final static int ITERATOR_LIMIT = 100;

    private final static TupleBinding<String> STRING_TUPLE = TupleBinding.getPrimitiveBinding(String.class);

    private Environment environment;
    private final Map<DB, StoredMap<String, String>> cachedMaps = new ConcurrentHashMap<DB, StoredMap<String, String>>();
    private final Map<DB, Database> cachedDatabases = new ConcurrentHashMap<DB, Database>();

    // cache of dbIterators
    private final Set<LocalDB.PwmDBIterator<String>> dbIterators = Collections.newSetFromMap(new ConcurrentHashMap<LocalDB.PwmDBIterator<String>,Boolean>());

    private LocalDB.Status status = LocalDB.Status.NEW;

    private boolean readOnly;

// -------------------------- STATIC METHODS --------------------------

    private static Database openDatabase(final DB db, final Environment environment, final boolean readonly)
            throws DatabaseException {
        final DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(IS_TRANSACTIONAL);
        dbConfig.setReadOnly(readonly);

        return environment.openDatabase(null, db.toString(), dbConfig);
    }

    private static StoredMap<String, String> openStoredMap(final Database database)
            throws DatabaseException {
        final StoredMap<String, String> storedMap = new StoredMap<String, String>(database, STRING_TUPLE, STRING_TUPLE, true);
        storedMap.getClass();
        return storedMap;
    }

    private static Environment openEnvironment(final File databaseDirectory, final Map<String, String> initProps, final boolean readonly)
            throws DatabaseException {
        if (databaseDirectory.mkdir()) {
            LOGGER.info("created file system directory " + databaseDirectory.toString());
        }

        LOGGER.trace("beginning open of db environment (" + JEVersion.CURRENT_VERSION.getVersionString() + ")");

        final EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        environmentConfig.setTransactional(IS_TRANSACTIONAL);
        environmentConfig.setReadOnly(readonly);

        if (initProps != null) {
            for (final String key : initProps.keySet()) {
                environmentConfig.setConfigParam(key, initProps.get(key));
            }
        }

        LOGGER.trace("opening environment with config: " + environmentConfig.toString());

        final long environmentOpenStartTime = System.currentTimeMillis();
        Environment environment = null;
        while (environment == null && TimeDuration.fromCurrent(environmentOpenStartTime).isShorterThan(OPEN_RETRY_SECONDS * 1000)) {
            try {
                environment = new Environment(databaseDirectory, environmentConfig);
            } catch (EnvironmentLockedException e) {
                LOGGER.info("unable to open environment (will retry for up to " + OPEN_RETRY_SECONDS + " seconds): " + e.getMessage());
                Helper.pause(1000);
            }
        }
        LOGGER.trace("db environment open");
        return environment;
    }

// --------------------------- CONSTRUCTORS ---------------------------

    Berkeley_LocalDB()
            throws Exception {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    public void close()
            throws LocalDBException {
        LOGGER.debug("LocalDB closing....");
        status = LocalDB.Status.CLOSED;

        for (final DB key : cachedDatabases.keySet()) {
            try {
                cachedDatabases.get(key).close();
            } catch (DatabaseException e) {
                LOGGER.error("error while closing database " + key.toString() + ": " + e.getMessage());
            }
        }

        cachedDatabases.clear();
        cachedMaps.clear();
        final long startTime = System.currentTimeMillis();

        boolean closed = false;
        while (!closed && (System.currentTimeMillis() - startTime) < CLOSE_RETRY_SECONDS * 1000) {
            try {
                for (final Database database : cachedDatabases.values()) {
                    database.close();
                }
                environment.close();
                closed = true;
            } catch (Exception e) {
                LOGGER.error("error while closing environment (will retry for " + CLOSE_RETRY_SECONDS + " seconds): " + e.getMessage());
                Helper.pause(5 * 1000);
            }
        }

        final TimeDuration td = new TimeDuration(System.currentTimeMillis() - startTime);
        LOGGER.info("closed (" + td.asCompactString() + ")");
    }

    public LocalDB.Status getStatus() {
        return status;
    }

    public boolean contains(final DB db, final String key)
            throws LocalDBException {
        preCheck(false);
        try {
            return cachedMaps.get(db).containsKey(key);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during contains check: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }
    }

    public String get(final DB db, final String key)
            throws LocalDBException {
        preCheck(false);
        try {
            return cachedMaps.get(db).get(key);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during contains check: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }
    }

    public void init(final File dbDirectory, final Map<String, String> initParameters, final boolean readOnly)
            throws LocalDBException {
        LOGGER.trace("begin initialization");

        this.readOnly = readOnly;
        try {
            environment = openEnvironment(dbDirectory, initParameters, readOnly);

            for (final DB db : DB.values()) {
                final Database database = openDatabase(db, environment, readOnly);
                cachedDatabases.put(db, database);
                cachedMaps.put(db, openStoredMap(database));
                LOGGER.trace("database '" + db.toString() + "' open");
            }
        } catch (DatabaseException e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }

        status = LocalDB.Status.OPEN;
    }

    public LocalDB.PwmDBIterator<String> iterator(final DB db)
            throws LocalDBException
    {
        preCheck(false);
        try {
            if (dbIterators.size() > ITERATOR_LIMIT) {
                throw new LocalDBException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"over " + ITERATOR_LIMIT + " iterators are outstanding, maximum limit exceeded"));
            }
            final LocalDB.PwmDBIterator<String> iterator = new DbIterator<String>(db);
            dbIterators.add(iterator);
            return iterator;
        } catch (Exception e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }
    }

    public void putAll(final DB db, final Map<String, String> keyValueMap)
            throws LocalDBException {
        preCheck(true);

        try {
            cachedMaps.get(db).putAll(keyValueMap);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during multiple-put: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }
    }

    public boolean put(final DB db, final String key, final String value)
            throws LocalDBException {
        preCheck(true);

        try {
            final StoredMap<String, String> transactionDB = cachedMaps.get(db);
            return null != transactionDB.put(key, value);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during put: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }
    }

    public boolean remove(final DB db, final String key)
            throws LocalDBException {
        preCheck(true);
        try {
            return cachedMaps.get(db).keySet().remove(key);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during remove: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }
    }

    public void removeAll(final DB db, final Collection<String> keys)

            throws LocalDBException {
        preCheck(true);
        try {
            cachedMaps.get(db).keySet().removeAll(keys);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during removeAll: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }
    }

    public int size(final DB db)
            throws LocalDBException {
        preCheck(false);
        try {
            final StoredMap<String, String> dbMap = cachedMaps.get(db);
            assert dbMap != null;
            return dbMap.size();
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during size: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }
    }

    public void truncate(final DB db)
            throws LocalDBException {
        preCheck(true);
        try {
            cachedMaps.remove(db);
            cachedDatabases.remove(db).close();

            environment.truncateDatabase(null, db.toString(), false);

            final Database database = openDatabase(db, environment, readOnly);
            cachedDatabases.put(db, database);
            cachedMaps.put(db, openStoredMap(database));
        } catch (DatabaseException e) {
            LOGGER.error("error during truncate: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.toString()));
        }
    }

// -------------------------- INNER CLASSES --------------------------

    private class DbIterator<K> implements LocalDB.PwmDBIterator<String> {

        private Iterator<String> innerIter;

        private DbIterator(final DB db) throws DatabaseException {
            this.innerIter = cachedMaps.get(db).keySet().iterator();
        }

        public boolean hasNext() {
            return innerIter.hasNext();
        }

        public void close() {
            innerIter = null;
            dbIterators.remove(this);
        }

        public String next() {
            return innerIter.next();
        }

        public void remove() {
            throw new UnsupportedOperationException("Berkeley LocalDB iterator does not support removals");
        }
    }

    public File getFileLocation() {
        if (environment == null) {
            return null;
        }
        return environment.getHome();
    }

    private void preCheck(final boolean write) throws LocalDBException {
        if (status != LocalDB.Status.OPEN) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,"LocalDB is not open, cannot begin a new transaction"));
        }

        if (write && readOnly) {
            throw new IllegalStateException("cannot allow mutation operation; LocalDB is in read-only mode");
        }
    }

}
