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

import org.mapdb.*;

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static password.pwm.util.localdb.LocalDB.DB;

/**
 * @author Jason D. Rivard
 */
public class MapDB_LocalDB implements LocalDBProvider {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(MapDB_LocalDB.class, true);
    private static final String FILE_NAME = "mapdb";

    private org.mapdb.DB recman;
    private final Map<LocalDB.DB, Map<String, String>> treeMap = new HashMap<LocalDB.DB, Map<String, String>>();
    private File dbDirectory;

    // operation lock
    private final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    private LocalDB.Status status = LocalDB.Status.NEW;

// --------------------------- CONSTRUCTORS ---------------------------

    MapDB_LocalDB() {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    public void close()
            throws LocalDBException {
        status = LocalDB.Status.CLOSED;

        if (recman == null) {
            return;
        }

        try {
            LOCK.writeLock().lock();
            final long startTime = System.currentTimeMillis();
            LOGGER.debug("closing pwmDB");
            recman.commit();
            recman.close();
            recman = null;
            LOGGER.info("LocalDB closed in " + TimeDuration.fromCurrent(startTime).asCompactString());
        } catch (Exception e) {
            LOGGER.error("error while closing LocalDB: " + e.getMessage(), e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public LocalDB.Status getStatus() {
        return status;
    }

    public boolean contains(final LocalDB.DB db, final String key)
            throws LocalDBException {
        try {
            LOCK.readLock().lock();
            return get(db, key) != null;
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public String get(final LocalDB.DB db, final String key)
            throws LocalDBException {
        try {
            LOCK.readLock().lock();
            final Map<String, String> tree = getHTree(db);
            final Object value = tree.get(key);
            return value == null ? null : value.toString();
        } catch (IOException e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public void init(final File dbDirectory, final Map<String, String> initParameters, final boolean readOnly)
            throws LocalDBException {
        if (readOnly) {
            throw new UnsupportedOperationException("readOnly not supported");
        }

        if (status != LocalDB.Status.NEW) {
            throw new IllegalStateException("already initialized");
        }

        final long startTime = System.currentTimeMillis();
        try {
            LOCK.writeLock().lock();
            this.dbDirectory = dbDirectory;
            final File dbFile = new File(dbDirectory.getAbsolutePath() + File.separator + FILE_NAME);
            recman = DBMaker.newFileDB(dbFile).make();

            LOGGER.info("LocalDB opened in " + TimeDuration.fromCurrent(startTime).asCompactString());
            status = LocalDB.Status.OPEN;
        } catch (Exception e) {
            LOGGER.error("error while opening localDB: " + e.getMessage(), e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public LocalDB.PwmDBIterator<String> iterator(final DB db)
            throws LocalDBException {
        try {
            return new DbIterator<String>(db);
        } catch (Exception e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        }
    }

    public void putAll(final DB db, final Map<String, String> keyValueMap)
            throws LocalDBException {
        try {
            LOCK.writeLock().lock();
            final Map<String, String> tree = getHTree(db);
            tree.putAll(keyValueMap);
            recman.commit();
        } catch (IOException e) {
            recman.rollback();
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public boolean put(final LocalDB.DB db, final String key, final String value)
            throws LocalDBException {
        final boolean preExists;
        try {
            LOCK.writeLock().lock();
            preExists = remove(db, key);
            final Map<String, String> tree = getHTree(db);
            tree.put(key, value);
            recman.commit();
        } catch (IOException e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
        }

        return preExists;
    }


    public boolean remove(final LocalDB.DB db, final String key)
            throws LocalDBException {
        try {
            LOCK.writeLock().lock();
            final Map<String, String> tree = getHTree(db);
            final String removedValue = tree.remove(key);
            recman.commit();
            return removedValue != null;
        } catch (IOException e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public int size(final LocalDB.DB db)
            throws LocalDBException {
        try {
            LOCK.readLock().lock();
            return getHTree(db).size();
        } catch (IOException e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public void truncate(final LocalDB.DB db)
            throws LocalDBException {
        final int startSize = size(db);

        LOGGER.info("beginning truncate of " + startSize + " records in " + db.toString() + " database, this may take a while...");

        final long startTime = System.currentTimeMillis();

        try {
            LOCK.writeLock().lock();
            final Map<String, String> tree = getHTree(db);
            tree.keySet().clear();
            recman.commit();
        } catch (IOException e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
        }

        LOGGER.debug("truncate complete of " + db.toString() + ", " + startSize + " records in " + new TimeDuration(System.currentTimeMillis(), startTime).asCompactString() + ", " + size(db) + " records in database");
    }

    public File getFileLocation() {
        return dbDirectory;
    }

    public void removeAll(final LocalDB.DB db, final Collection<String> keys)
            throws LocalDBException {
        try {
            LOCK.writeLock().lock();
            final Map<String, String> tree = getHTree(db);
            tree.keySet().removeAll(keys);
            recman.commit();
        } catch (IOException e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_PWMDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
        }
    }

// -------------------------- OTHER METHODS --------------------------

    private Map<String, String> getHTree(final DB keyName)
            throws IOException {
        Map<String, String> tree = treeMap.get(keyName);
        if (tree == null) {
            tree = openHTree(keyName.toString(), recman);
            treeMap.put(keyName, tree);
        }
        return tree;
    }

    private static Map<String, String> openHTree(
            final String name,
            final org.mapdb.DB recman
    )
            throws IOException {
        return recman.getTreeMap(name);
    }

// -------------------------- INNER CLASSES --------------------------

    private class DbIterator<K> implements LocalDB.PwmDBIterator<String> {
        private Iterator<String> theIterator;

        private DbIterator(final DB db) throws IOException, LocalDBException {
            this.theIterator = getHTree(db).keySet().iterator();
        }

        public boolean hasNext() {
            final boolean hasNext = theIterator.hasNext();
            if (!hasNext) {
                close();
            }
            return hasNext;
        }

        public void close() {
            theIterator = null;
        }

        public String next() {
            return theIterator.next();
        }

        public void remove() {
            theIterator.remove();
        }

        protected void finalize() throws Throwable {
            super.finalize();
            close();
        }
    }

}
