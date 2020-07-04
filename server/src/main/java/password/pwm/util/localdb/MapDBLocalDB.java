/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.util.localdb;

/**
 * @author Jason D. Rivard
 */
public class MapDBLocalDB
{
}

// No longer used, commented in case it may be resurrected some day.
/*
import org.mapdb.DBMaker;
import org.mapdb.Serializer;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static password.pwm.util.localdb.LocalDB.DB;

public class MapDBLocalDB implements LocalDBProvider {

    private static final PwmLogger LOGGER = PwmLogger.forClass(MapDBLocalDB.class, true);
    private static final String FILE_NAME = "mapdb";

    private org.mapdb.DB recman;
    private final Map<LocalDB.DB, Map<String, String>> treeMap = new HashMap<>();
    private File dbDirectory;

    // operation lock
    private final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    private LocalDB.Status status = LocalDB.Status.NEW;

    MapDBLocalDB() {
    }

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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.readLock().unlock();
        }
    }

    public void init(final File dbDirectory, final Map<String, String> initParameters, final Map<Parameter,String> parameters)
            throws LocalDBException {

        if (LocalDBUtility.hasBooleanParameter(Parameter.readOnly, parameters)) {
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
            recman = DBMaker.fileDB(dbFile.getAbsolutePath()).make();

            LOGGER.debug("beginning DB compact");
            recman.getStore().compact();

            LOGGER.info("LocalDB opened in " + TimeDuration.fromCurrent(startTime).asCompactString());
            status = LocalDB.Status.OPEN;
        } catch (Exception e) {
            LOGGER.error("error while opening localDB: " + e.getMessage(), e);
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    public LocalDB.LocalDBIterator<String> iterator(final DB db)
            throws LocalDBException {
        try {
            return new MapDBIterator<String>(db);
        } catch (Exception e) {
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
        }

        LOGGER.debug("truncate complete of " + db.toString() + ", " + startSize + " records in "
        + new TimeDuration(System.currentTimeMillis(), startTime).asCompactString() + ", " + size(db) + " records in database");
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
            throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,e.getMessage()));
        } finally {
            LOCK.writeLock().unlock();
        }
    }

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
    ) {
        return recman.hashMap(name)
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.STRING)
                .open();
    }

    @Override
    public Map<String, Serializable> debugInfo() {
        return Collections.emptyMap();
    }

    private class MapDBIterator<K> implements LocalDB.LocalDBIterator<String> {
        private Iterator<String> theIterator;

        private MapDBIterator(final DB db) throws IOException, LocalDBException {
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

    @Override
    public Set<Flag> flags() {
        return Collections.emptySet();
    }
}
*/
