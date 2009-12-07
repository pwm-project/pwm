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

import password.pwm.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.Sleeper;
import password.pwm.util.TimeDuration;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;
import jdbm.helper.FastIterator;
import jdbm.htree.HTree;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Jason D. Rivard
 */
public class JDBM_PwmDb implements PwmDB {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(JDBM_PwmDb.class);
    private static final String FILE_NAME = "jdbm";

    private RecordManager recman;
    private final Map<String, HTree> treeMap = new HashMap<String, HTree>();
    private File dbDirectory;

    // cache of dbIterators
    private final Map<DB, DbIterator> dbIterators = new ConcurrentHashMap<DB, DbIterator>();

// --------------------------- CONSTRUCTORS ---------------------------

    JDBM_PwmDb() {
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    public void close()
            throws PwmDBException {
        try {
            recman.commit();
            recman.close();
        } catch (IOException e) {
            throw new PwmDBException(e);
        }
    }

    public boolean contains(final PwmDB.DB db, final String key)
            throws PwmDBException {
        return get(db,key) != null;
    }

    public String get(final PwmDB.DB db, final String key)
            throws PwmDBException {
        try {
            final HTree tree = getHTree(db);
            final Object value = tree.get(key);
            return value == null ? null : value.toString();
        } catch (IOException e) {
            throw new PwmDBException(e);
        }
    }

    public void init(final File dbDirectory, final String initStrng)
            throws PwmDBException {
        try {
            this.dbDirectory = dbDirectory;
            final String dbFileName = dbDirectory.getAbsolutePath() + File.separator + FILE_NAME;
            recman = RecordManagerFactory.createRecordManager(dbFileName, new Properties());
            recman.commit();

            LOGGER.debug("counting db records...");
            for (final DB db : DB.values()) {
                final long startTime = System.currentTimeMillis();
                final int size = size(db);
                LOGGER.debug("found " + size + " records in " + db.toString() + " in " + new TimeDuration(System.currentTimeMillis(),startTime).asCompactString());
            }
        } catch (IOException e) {
            throw new PwmDBException(e);
        }
    }

    public Iterator<TransactionItem> iterator(final DB db) throws PwmDBException {
        try {
            if (dbIterators.containsKey(db)) {
                throw new IllegalArgumentException("multiple iterators per DB are not permitted");
            }

            final DbIterator iterator = new DbIterator(db);
            dbIterators.put(db,iterator);
            return iterator;
        } catch (Exception e) {
            throw new PwmDBException(e);
        }
    }

    public void putAll(final DB db, final Map<String,String> keyValueMap)
            throws PwmDBException
    {
        try {
            final HTree tree = getHTree(db);
            for (final String loopKey : keyValueMap.keySet()) {
                tree.put(loopKey, keyValueMap.get(loopKey));
            }
            recman.commit();
        } catch (IOException e) {
            try {
                recman.rollback();
            } catch (IOException e2) {
                throw new PwmDBException(e2);
            }
        }
    }

    public boolean put(final PwmDB.DB db, final String key, final String value)
            throws PwmDBException
    {
        final boolean preExists;
        try {
            preExists = remove(db, key);
            final HTree tree = getHTree(db);
            tree.put(key, value);
            recman.commit();
        } catch (IOException e) {
            throw new PwmDBException(e);
        }

        return preExists;
    }

    public boolean remove(final PwmDB.DB db, final String key)
            throws PwmDBException
    {
        try {
            final HTree tree = getHTree(db);
            if (contains(db, key)) {
                tree.remove(key);
                recman.commit();
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            throw new PwmDBException(e);
        }
    }

    public void returnIterator(final DB db) throws PwmDBException {

    }

    public int size(final PwmDB.DB db)
            throws PwmDBException {
        try {
            int size = 0;
            for (FastIterator fastIter = getHTree(db).keys(); fastIter.next() != null;) {
                size++;
            }
            return size;
        } catch (IOException e) {
            throw new PwmDBException(e);
        }
    }

    public void truncate(final PwmDB.DB db)
            throws PwmDBException {
        final int reportInterval = 60 * 1000;

        final Sleeper sleeper = new Sleeper(50);
        final int startSize = size(db);
        final long startTime = System.currentTimeMillis();
        long lastReportTime = System.currentTimeMillis();

        try {
            while (size(db) > 0) {
                final Object nextKey = getHTree(db).keys().next();
                remove(db,nextKey.toString());

                sleeper.sleep();

                // output status once a minute.
                if (System.currentTimeMillis() - lastReportTime > reportInterval) {
                    LOGGER.info("truncating " + db.toString() + ", " + size(db) + " remaining...");
                    lastReportTime = System.currentTimeMillis();
                }
            }
        } catch (IOException e) {
            throw new PwmDBException(e);
        }

        LOGGER.debug("truncate complete of " + db.toString() + ", " + startSize + " records in " + new TimeDuration(System.currentTimeMillis(),startTime).asCompactString());
    }

// -------------------------- OTHER METHODS --------------------------

    private HTree getHTree(final DB keyName)
            throws IOException {
        HTree tree = treeMap.get(keyName.toString());
        if (tree == null) {
            tree = openHTree(keyName.toString(), recman);
            treeMap.put(keyName.toString(), tree);
        }
        return tree;
    }

    private static HTree openHTree(
            final String name,
            final RecordManager recman
    )
            throws IOException {
        final long recid = recman.getNamedObject(name);
        final HTree tree;

        if (recid != 0) {
            tree = HTree.load(recman, recid);
        } else {
            // createSharedHistoryManager a new B+Tree data structure and use a StringComparator
            // to order the records based on people's name.

            tree = HTree.createInstance(recman);
            recman.setNamedObject(name, tree.getRecid());
            LOGGER.debug("created a new empty " + name);
        }

        return tree;
    }

// -------------------------- INNER CLASSES --------------------------

    private class DbIterator implements Iterator<TransactionItem> {
        private final DB db;
        private FastIterator fastIter;

        private TransactionItem currentItem;
        private TransactionItem nextItem;


        private DbIterator(final DB db) throws IOException, PwmDBException {
            this.db = db;
            this.fastIter = getHTree(db).keys();
            fetchNext();
        }

        private void fetchNext() throws PwmDBException {
            if (fastIter == null) {
                close();
                return;
            }

            final Object nextKey = fastIter.next();
            if (nextKey == null) {
                close();
                return;
            }

            final Object value = get(db,nextKey.toString());
            nextItem = new TransactionItem(db, nextKey.toString(),value == null ? null : value.toString());
        }

        public boolean hasNext() {
            return nextItem != null;
        }

        public void close() {
            fastIter = null;
            nextItem = null;
            dbIterators.remove(db);
        }

        public TransactionItem next()  {
            currentItem = nextItem;
            try {
                fetchNext();
            } catch (PwmDBException e) {
                throw new IllegalStateException("unexpected pwmDB error: " + e.getMessage());
            }
            return currentItem;
        }

        public void remove()
        {
            if (currentItem != null) {
                try {
                    JDBM_PwmDb.this.remove(db, currentItem.getKey());
                } catch (PwmDBException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        protected void finalize() throws Throwable {
            super.finalize();
            close();
        }
    }

    public long diskSpaceUsed() {
        try {
            return Helper.getFileDirectorySize(dbDirectory);
        } catch (Exception e) {
            LOGGER.error("error trying to compute db directory size: " + e.getMessage());
        }
        return 0;
    }

    public void removeAll(final DB db, final Collection<String> keys) throws PwmDBException {
        for (final String key : keys) {
            remove(db, key); //@todo improve this
        }
    }
}
