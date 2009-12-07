
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

import com.novell.ldapchai.util.StringHelper;
import password.pwm.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.collections.StoredMap;
import com.sleepycat.je.*;
import com.sleepycat.util.RuntimeExceptionWrapper;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jason D. Rivard
 */
public class Berkeley_PwmDb implements PwmDB {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Berkeley_PwmDb.class);

    private final static String DEFAULT_CACHE_BYTES =               String.valueOf(10 * 1000 * 1000);    // 10mb
    private final static String DEFAULT_LOG_FILE_SIZE =             String.valueOf(10 * 1000 * 1000);    // 10mb
    private final static String DEFAULT_LOG_UTILIZATION_PERCENT =   String.valueOf(80);                  // 80%
    private final static boolean IS_TRANSACTIONAL =                 true;

    private final static TupleBinding<String> STRING_TUPLE = TupleBinding.getPrimitiveBinding(String.class);

    private Environment environment;
    private final Map<DB, StoredMap<String,String>> cachedMaps = new ConcurrentHashMap<DB, StoredMap<String,String>>();
    private final Map<DB, Database> cachedDatabases = new ConcurrentHashMap<DB, Database>();

    // cache of dbIterators
    private final Map<DB, DbIterator> dbIterators = Collections.synchronizedMap(new HashMap<DB, DbIterator>());

    private volatile boolean open = false;

    private Thread cleanupThread;

// -------------------------- STATIC METHODS --------------------------

    private static Database openDatabase(final DB db, final Environment environment)
            throws DatabaseException
    {
        final DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(IS_TRANSACTIONAL);

        return environment.openDatabase(null, db.toString(), dbConfig);
    }

    private static StoredMap<String,String> openStoredMap(final Database database)
            throws DatabaseException
    {
        final StoredMap<String,String> storedMap = new StoredMap<String,String>(database, STRING_TUPLE, STRING_TUPLE, true);
        storedMap.getClass();
        return storedMap;
    }

    private static Environment openEnvironment(final File databaseDirectory, final Map<String,String> initProps)
            throws DatabaseException
    {
        //noinspection ResultOfMethodCallIgnored
        databaseDirectory.mkdir();

        LOGGER.trace("beginning open of db environment (" + JEVersion.CURRENT_VERSION.getVersionString() + ")");

        final EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setAllowCreate(true);
        environmentConfig.setTransactional(IS_TRANSACTIONAL);
        environmentConfig.setConfigParam(EnvironmentConfig.MAX_MEMORY, DEFAULT_CACHE_BYTES);
        environmentConfig.setConfigParam(EnvironmentConfig.LOG_FILE_MAX, DEFAULT_LOG_FILE_SIZE);
        environmentConfig.setConfigParam(EnvironmentConfig.CLEANER_MIN_UTILIZATION, DEFAULT_LOG_UTILIZATION_PERCENT);

        for (final String key : initProps.keySet()) {
            environmentConfig.setConfigParam(key,initProps.get(key));
        }

        LOGGER.trace("opening environment with config: " + environmentConfig.toString());
        final Environment environment = new Environment(databaseDirectory, environmentConfig);
        LOGGER.trace("db environment open");
        return environment;
    }

// --------------------------- CONSTRUCTORS ---------------------------

    Berkeley_PwmDb()
            throws Exception
    {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    public void close()
            throws PwmDBException
    {
        open = false;
        try {
            LOGGER.debug("pwmDB closing....");
            final long startTime = System.currentTimeMillis();

            for (final DB db : cachedMaps.keySet()) {
                if (dbIterators.containsKey(db)) {
                    dbIterators.get(db).close();
                }

                final Database database = cachedDatabases.get(db);
                database.close();
            }

            cachedDatabases.clear();
            cachedMaps.clear();

            environment.close();
            final TimeDuration td = new TimeDuration(System.currentTimeMillis() - startTime);
            LOGGER.info("closed (" + td.asCompactString() + ")");
        } catch (DatabaseException e) {
            throw new PwmDBException(e);
        }
    }

    public boolean contains(final DB db, final String key)
            throws PwmDBException
    {
        preCheck();
        try {
            return cachedMaps.get(db).containsKey(key);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during contains check: " + e.toString() );
            throw new PwmDBException(e.getCause());
        }
    }

    public String get(final DB db, final String key)
            throws PwmDBException
    {
        preCheck();
        try {
            return cachedMaps.get(db).get(key);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during contains check: " + e.toString() );
            throw new PwmDBException(e.getCause());
        }
    }

    public void init(final File dbDirectory, final String initString)
            throws PwmDBException
    {
        LOGGER.trace("begin init, init string is: '" + initString + "'");

        final Map<String,String> initParams = new HashMap<String,String>();
        try {
            initParams.putAll(StringHelper.tokenizeString(initString,";;;","="));
        } catch (Exception e) {
            LOGGER.trace("error while parsing initString: " + e.getMessage());
        }

        try {
            environment = openEnvironment(dbDirectory, initParams);

            for (final DB db : DB.values()) {
                final Database database = openDatabase(db, environment);
                cachedDatabases.put(db, database);
                cachedMaps.put(db,openStoredMap(database));
                LOGGER.trace("database '" + db.toString() + "' open");
            }
        } catch (DatabaseException e) {
            throw new PwmDBException(e);
        }

        triggerCleanup();

        open = true;
    }

    public synchronized Iterator<TransactionItem> iterator(final DB db)
            throws PwmDBException
    {
        preCheck();
        try {
            if (dbIterators.containsKey(db)) {
                throw new IllegalArgumentException("multiple outstanding iterators per DB are not permitted");
            }

            final DbIterator iterator = new DbIterator(db);
            dbIterators.put(db,iterator);
            return iterator;
        } catch (Exception e) {
            throw new PwmDBException(e);
        }
    }

    public void putAll(final DB db, final Map<String, String> keyValueMap)
            throws PwmDBException
    {
        preCheck();

        try {
            cachedMaps.get(db).putAll(keyValueMap);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during multiple-put: " + e.toString() );
            throw new PwmDBException(e.getCause());
        }
    }

    public boolean put(final DB db, final String key, final String value)
            throws PwmDBException
    {
        preCheck();

        try {
            final StoredMap<String, String> transactionDB = cachedMaps.get(db);
            return null != transactionDB.put(key, value);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during put: " + e.toString() );
            throw new PwmDBException(e.getCause());
        }
    }

    public boolean remove(final DB db, final String key)
            throws PwmDBException
    {
        preCheck();
        try {
            return cachedMaps.get(db).keySet().remove(key);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during remove: " + e.toString() );
            throw new PwmDBException(e.getCause());
        }
    }

    public void removeAll(final DB db, final Collection<String> keys)

            throws PwmDBException
    {
        preCheck();
        try {
            cachedMaps.get(db).keySet().removeAll(keys);
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during removeAll: " + e.toString() );
            throw new PwmDBException(e.getCause());
        }
    }

    public synchronized void returnIterator(final DB db)
            throws PwmDBException
    {
        try {
            if (dbIterators.containsKey(db)) {
                final DbIterator oldIterator = dbIterators.remove(db);
                if (oldIterator != null) {
                    oldIterator.close();
                }
            }
        } catch (Exception e) {
            throw new PwmDBException(e);
        }
    }

    public int size(final DB db)
            throws PwmDBException
    {
        preCheck();
        try {
            final StoredMap<String,String> dbMap = cachedMaps.get(db);
            assert dbMap != null;
            return dbMap.size();
        } catch (RuntimeExceptionWrapper e) {
            LOGGER.error("error during size: " + e.toString() );
            throw new PwmDBException(e.getCause());
        }
    }

    public void truncate(final DB db)
            throws PwmDBException
    {
        preCheck();
        try {
            cachedMaps.remove(db);
            cachedDatabases.remove(db).close();
                      
            environment.truncateDatabase(null, db.toString(), false);

            final Database database = openDatabase(db, environment);
            cachedDatabases.put(db, database);
            cachedMaps.put(db,openStoredMap(database));
        } catch (DatabaseException e) {
            LOGGER.error("error during truncate: " + e.toString() );
            throw new PwmDBException(e.getCause());
        }
        triggerCleanup();
    }

    private void triggerCleanup()
    {
        if (cleanupThread != null) {
            return;
        }

        cleanupThread = new Thread() {
            public void run() {
                final long startTime = System.currentTimeMillis();
                LOGGER.debug("starting cleanup");
                try {
                    for (int i = 0; i < 2; i++) {
                        environment.compress();
                        environment.checkpoint(null);
                        environment.cleanLog();
                    }
                } catch (Exception e) {
                    LOGGER.error("unexpected error during cleanup: " + e.getMessage());
                }
                cleanupThread = null;
                LOGGER.debug("cleanup completed (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");
            }
        };
        cleanupThread.setDaemon(true);
        cleanupThread.setName("pwm-Berkeley_Pwm_Db cleanup thread");
        cleanupThread.start();
    }

// -------------------------- INNER CLASSES --------------------------

    private class DbIterator implements Iterator<TransactionItem> {
        private Iterator<String> innerIter;
        final private DB db;

        private DbIterator(final DB db) throws DatabaseException {
            this.db = db;
            this.innerIter = cachedMaps.get(db).keySet().iterator();
        }

        public boolean hasNext() {
            return innerIter.hasNext();
        }

        public void close() {
            innerIter = null;
            dbIterators.remove(db);
        }

        public TransactionItem next() {
            try {
                final String key = innerIter.next();
                final String value = get(db,key);
                return new TransactionItem(db,key,value);
            } catch (PwmDBException e) {
                throw new RuntimeException(e);
            }
        }

        public void remove() {
            innerIter.remove();
        }
    }

    public long diskSpaceUsed() {
        try {
            return Helper.getFileDirectorySize(environment.getHome());
        } catch (Exception e) {
            LOGGER.error("error trying to compute db directory size: " + e.getMessage());
        }
        return 0;
    }

    private void preCheck() throws PwmDBException {
        if (!open) {
            throw new PwmDBException("pwmDB is closed, cannot begin a new transaction");
        }
    }
}
