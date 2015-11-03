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

package password.pwm.util.localdb;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.collections.StoredMap;
import com.sleepycat.je.*;
import com.sleepycat.util.RuntimeExceptionWrapper;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.Helper;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static password.pwm.util.localdb.LocalDB.DB;

/**
 * @author Jason D. Rivard
 */
public class Berkeley_LocalDB implements LocalDBProvider {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(Berkeley_LocalDB.class, true);

    private final static boolean IS_TRANSACTIONAL = true;
    private final static int OPEN_RETRY_SECONDS = 60;
    private final static int CLOSE_RETRY_SECONDS = 120;
    private final static int ITERATOR_LIMIT = 100;

    private final static TupleBinding<String> STRING_TUPLE = TupleBinding.getPrimitiveBinding(String.class);

    private Environment environment;
    private final Map<DB, StoredMap<String, String>> cachedMaps = new ConcurrentHashMap<>();
    private final Map<DB, Database> cachedDatabases = new ConcurrentHashMap<>();

    // cache of dbIterators
    private final Set<BerkeleyDbIterator<String>> dbIterators = Collections.newSetFromMap(new ConcurrentHashMap<BerkeleyDbIterator<String>,Boolean>());

    private LocalDB.Status status = LocalDB.Status.NEW;

    // lock used only for structural changes (like truncate);
    private Map<DB, ReadWriteLock> lockMap = new ConcurrentHashMap<>();

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
        final StoredMap<String, String> storedMap = new StoredMap<>(database, STRING_TUPLE, STRING_TUPLE, true);
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

    public void close()
            throws LocalDBException {
        LOGGER.debug("LocalDB closing....");

        try {
            for (final ReadWriteLock readWriteLock : lockMap.values()) {
                readWriteLock.writeLock().lock();
            }

            status = LocalDB.Status.CLOSED;

            for (final BerkeleyDbIterator localDBIterator : dbIterators) {
                LOGGER.trace("closing outstanding iterator for db " + localDBIterator.getDb() + " due to LocalDB.close command");
                try {
                    localDBIterator.close();
                } catch (Throwable e) {
                    LOGGER.error("error closing outstanding iterator for db " + localDBIterator.getDb() + " during close, error: " + e.getMessage());
                }
            }


            for (final DB key : cachedDatabases.keySet()) {
                try {
                    cachedDatabases.get(key).close();
                } catch (Throwable e) {
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
                    if (environment != null) {
                        environment.close();
                    }
                    closed = true;
                } catch (Exception e) {
                    LOGGER.error("error while closing environment (will retry for " + CLOSE_RETRY_SECONDS + " seconds): " + e.getMessage());
                    Helper.pause(5 * 1000);
                }
            }

            final TimeDuration td = new TimeDuration(System.currentTimeMillis() - startTime);
            LOGGER.info("closed (" + td.asCompactString() + ")");
        } finally {
            for (final ReadWriteLock readWriteLock : lockMap.values()) {
                readWriteLock.writeLock().unlock();
            }

        }
    }

    public LocalDB.Status getStatus() {
        return status;
    }

    public boolean contains(final DB db, final String key)
            throws LocalDBException {
        preCheck(false);
        try {
            lockMap.get(db).readLock().lock();
            return cachedMaps.get(db).containsKey(key);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during contains check: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } finally {
            lockMap.get(db).readLock().unlock();
        }
    }

    public String get(final DB db, final String key)
            throws LocalDBException {
        preCheck(false);
        try {
            lockMap.get(db).readLock().lock();
            return cachedMaps.get(db).get(key);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during contains check: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } finally {
            lockMap.get(db).readLock().unlock();
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
                lockMap.put(db, new ReentrantReadWriteLock());
                LOGGER.trace("database '" + db.toString() + "' open");
            }
        } catch (DatabaseException e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        }

        status = LocalDB.Status.OPEN;
    }

    public LocalDB.LocalDBIterator<String> iterator(final DB db)
            throws LocalDBException
    {
        preCheck(false);
        try {
            lockMap.get(db).readLock().lock();
            if (dbIterators.size() > ITERATOR_LIMIT) {
                throw new LocalDBException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"over " + ITERATOR_LIMIT + " iterators are outstanding, maximum limit exceeded"));
            }
            final BerkeleyDbIterator<String> iterator = new BerkeleyDbIterator<>(db);
            dbIterators.add(iterator);
            LOGGER.trace(this.getClass().getSimpleName() + " issued iterator for " + db.toString() + ", outstanding iterators: " + dbIterators.size());
            return iterator;
        } catch (Exception e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } finally {
            lockMap.get(db).readLock().unlock();
        }
    }

    public void putAll(final DB db, final Map<String, String> keyValueMap)
            throws LocalDBException {
        preCheck(true);

        try {
            lockMap.get(db).readLock().lock();
            cachedMaps.get(db).putAll(keyValueMap);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during multiple-put: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } finally {
            lockMap.get(db).readLock().unlock();
        }
    }

    public boolean put(final DB db, final String key, final String value)
            throws LocalDBException {
        preCheck(true);

        try {
            lockMap.get(db).readLock().lock();
            final StoredMap<String, String> transactionDB = cachedMaps.get(db);
            return null != transactionDB.put(key, value);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during put: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } finally {
            lockMap.get(db).readLock().unlock();
        }
    }

    public boolean remove(final DB db, final String key)
            throws LocalDBException
    {
        preCheck(true);
        try {
            lockMap.get(db).readLock().lock();
            return cachedMaps.get(db).keySet().remove(key);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during remove: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } finally {
            lockMap.get(db).readLock().unlock();
        }
    }

    public void removeAll(final DB db, final Collection<String> keys)
            throws LocalDBException
    {
        preCheck(true);
        try {
            lockMap.get(db).readLock().lock();
            cachedMaps.get(db).keySet().removeAll(keys);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during removeAll: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } finally {
            lockMap.get(db).readLock().unlock();
        }
    }

    public int size(final DB db)
            throws LocalDBException {
        preCheck(false);
        try {
            lockMap.get(db).readLock().lock();
            final StoredMap<String, String> dbMap = cachedMaps.get(db);
            assert dbMap != null;
            return dbMap.size();
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during size: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } finally {
            lockMap.get(db).readLock().unlock();
        }
    }

    public void truncate(final DB db)
            throws LocalDBException {
        preCheck(true);
        LOGGER.trace("beginning truncate of db " + db.toString());
        final Date startTime = new Date();
        try {
            for (final LocalDB.LocalDBIterator localDBIterator : dbIterators) {
                if (((BerkeleyDbIterator)localDBIterator).getDb() == db) {
                    LOGGER.trace("closing outstanding iterator for db " + db + " due to truncate command");
                    localDBIterator.close();
                }
            }

            lockMap.get(db).writeLock().lock();
            cachedMaps.remove(db);
            cachedDatabases.remove(db).close();

            environment.truncateDatabase(null, db.toString(), false);

            final Database database = openDatabase(db, environment, readOnly);
            cachedDatabases.put(db, database);
            cachedMaps.put(db, openStoredMap(database));
            LOGGER.trace("completed truncate of db " + db.toString() + " in " + TimeDuration.fromCurrent(startTime).asCompactString());
        } catch (DatabaseException e) {
            LOGGER.error("error during truncate: " + e.toString());
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } catch (Exception e) {
            LOGGER.error("unexpected error during truncate: " + e.toString(),e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.toString()));
        } finally {
            lockMap.get(db).writeLock().unlock();
        }
    }

// -------------------------- INNER CLASSES --------------------------

    private class BerkeleyDbIterator<K> implements LocalDB.LocalDBIterator<String> {
        private DB db;
        private Iterator<String> innerIter;

        private BerkeleyDbIterator(final DB db) throws DatabaseException {
            this.db = db;
            this.innerIter = cachedMaps.get(db).keySet().<K>iterator();
        }

        public boolean hasNext() {
            return innerIter != null && innerIter.hasNext();
        }

        public void close() {
            Iterator copiedIterator = innerIter;
            innerIter = null;
            if (copiedIterator != null) {
                final Date startTime = new Date();
                int cycleCount = 0;
                while (copiedIterator.hasNext()) {
                    cycleCount++;
                    copiedIterator.next();
                }
                LOGGER.trace("closed iterator for " + this.getDb() + " with " + cycleCount + " unused cycles in " + TimeDuration.fromCurrent(startTime).asCompactString());
            }
            dbIterators.remove(this);
            LOGGER.trace(this.getClass().getSimpleName() + " closed iterator for " + db.toString() + ", outstanding iterators: " + dbIterators.size());
        }

        public String next() {
            return innerIter != null ? innerIter.next() : null;
        }

        public void remove() {
            throw new UnsupportedOperationException("Berkeley LocalDB iterator does not support removals");
        }

        public DB getDb() {
            return db;
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,"LocalDB is not open, cannot begin a new transaction"));
        }

        if (write && readOnly) {
            throw new IllegalStateException("cannot allow mutation operation; LocalDB is in read-only mode");
        }
    }

}
