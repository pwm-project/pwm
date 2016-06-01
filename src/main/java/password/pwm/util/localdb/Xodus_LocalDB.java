/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

import jetbrains.exodus.ByteIterable;
import jetbrains.exodus.bindings.StringBinding;
import jetbrains.exodus.env.*;
import jetbrains.exodus.management.Statistics;
import org.jetbrains.annotations.NotNull;
import password.pwm.util.JsonUtil;
import password.pwm.util.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class Xodus_LocalDB implements LocalDBProvider {
    private static final PwmLogger LOGGER = PwmLogger.forClass(Xodus_LocalDB.class);

    private Environment environment;
    private Map<LocalDB.DB,Store> stores = new HashMap<>();
    private File fileLocation;

    private LocalDB.Status status = LocalDB.Status.NEW;

    private final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    @Override
    public void init(File dbDirectory, Map<String, String> initParameters, Map<Parameter,String> parameters) throws LocalDBException {
        this.fileLocation = dbDirectory;

        LOGGER.trace("begin environment open");
        final Date startTime = new Date();

        final EnvironmentConfig environmentConfig = new EnvironmentConfig();
        environmentConfig.setLogDurableWrite(true);
        environmentConfig.setGcEnabled(true);

        for (final String key : initParameters.keySet()) {
            final String value = initParameters.get(key);
            LOGGER.trace("setting environment config key=" + key + ", value=" + value);
            environmentConfig.setSetting(key,value);
        }
        environment = Environments.newInstance(dbDirectory.getAbsolutePath() + File.separator + "xodus", environmentConfig);
        LOGGER.trace("environment open (" + TimeDuration.fromCurrent(startTime).asCompactString() + ")");

        for (final LocalDB.DB db : LocalDB.DB.values()) {
            stores.put(db, openStore(db));
        }

        status = LocalDB.Status.OPEN;

        for (final LocalDB.DB db : LocalDB.DB.values()) {
            LOGGER.trace("opened " + db + " with " + this.size(db) + " records");
        }
    }

    @Override
    public void close() throws LocalDBException {
        LOCK.writeLock().lock();
        try {
            environment.close();
            status = LocalDB.Status.CLOSED;
            LOGGER.debug("closed");
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    @Override
    public int size(final LocalDB.DB db) throws LocalDBException {
        LOCK.readLock().lock();
        try {
            return environment.computeInReadonlyTransaction(new TransactionalComputable<Integer>() {
                @Override
                public Integer compute(@NotNull Transaction transaction) {
                    return (int) stores.get(db).count(transaction);
                }
            });
        } finally {
            LOCK.readLock().unlock();
        }
    }
    @Override
    public boolean contains(LocalDB.DB db, String key) throws LocalDBException {
        return get(db, key) != null;
    }

    @Override
    public String get(final LocalDB.DB db, final String key) throws LocalDBException {
        LOCK.readLock().lock();
        try {
            final Store store = stores.get(db);
            return environment.computeInTransaction(new TransactionalComputable<String>() {
                @Override
                public String compute(@NotNull Transaction transaction) {
                    final ByteIterable returnValue = store.get(transaction,StringBinding.stringToEntry(key));
                    if (returnValue != null) {
                        return StringBinding.entryToString(returnValue);
                    }
                    return null;
                }
            });
        } finally {
            LOCK.readLock().unlock();
        }
    }

    @Override
    public LocalDB.LocalDBIterator<String> iterator(final LocalDB.DB db) throws LocalDBException {
        return new InnerIterator(db);
    }

    private class InnerIterator implements LocalDB.LocalDBIterator<String> {
        final private Transaction transaction;
        final private Cursor cursor;

        private boolean closed;
        private String nextValue = "";

        InnerIterator(final LocalDB.DB db) {
            this.transaction = environment.beginReadonlyTransaction();
            this.cursor = stores.get(db).openCursor(transaction);
            doNext();
        }

        private void doNext() {
            try {
                if (closed) {
                    return;
                }

                if (!cursor.getNext()) {
                    close();
                    return;
                }
                final ByteIterable value = cursor.getKey();
                if (value == null || value.getLength() == 0) {
                    close();
                    return;
                }
                final String decodedValue = StringBinding.entryToString(value);
                if (decodedValue == null) {
                    close();
                    return;
                }
                nextValue = decodedValue;
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            cursor.close();
            transaction.abort();
            nextValue = null;
            closed = true;
        }

        @Override
        public boolean hasNext() {
            return !closed && nextValue != null;
        }

        @Override
        public String next() {
            if (closed) {
                return null;
            }
            final String value = nextValue;
            doNext();
            return value;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove not supported");
        }
    }

    @Override
    public void putAll(final LocalDB.DB db, final Map<String, String> keyValueMap) throws LocalDBException {
        LOCK.readLock().lock();
        try {
            final Store store = stores.get(db);
            environment.executeInTransaction(new TransactionalExecutable() {
                @Override
                public void execute(@NotNull Transaction transaction) {
                    for (final String key : keyValueMap.keySet()) {
                        final String value = keyValueMap.get(key);
                        final ByteIterable k = StringBinding.stringToEntry(key);
                        final ByteIterable v = StringBinding.stringToEntry(value);
                        store.put(transaction,k,v);
                    }
                }
            });
        } finally {
            LOCK.readLock().unlock();
        }
    }


    @Override
    public boolean put(final LocalDB.DB db, final String key, final String value) throws LocalDBException {
        LOCK.readLock().lock();
        try {
            final Store store = stores.get(db);
            return environment.computeInTransaction(new TransactionalComputable<Boolean>() {
                @Override
                public Boolean compute(@NotNull Transaction transaction) {
                    final ByteIterable k = StringBinding.stringToEntry(key);
                    final ByteIterable v = StringBinding.stringToEntry(value);
                    return store.put(transaction,k,v);
                }
            });
        } finally {
            LOCK.readLock().unlock();
        }
    }

    @Override
    public boolean remove(final LocalDB.DB db, final String key) throws LocalDBException {
        LOCK.readLock().lock();
        try {
            final Store store = stores.get(db);
            return environment.computeInTransaction(new TransactionalComputable<Boolean>() {
                @Override
                public Boolean compute(@NotNull Transaction transaction) {
                    return store.delete(transaction,StringBinding.stringToEntry(key));
                }
            });
        } finally {
            LOCK.readLock().unlock();
        }
    }

    @Override
    public void removeAll(final LocalDB.DB db, final Collection<String> keys) throws LocalDBException {
        LOCK.readLock().lock();
        try {
            final Store store = stores.get(db);
            environment.executeInTransaction(new TransactionalExecutable() {
                @Override
                public void execute(@NotNull Transaction transaction) {
                    for (final String key : keys) {
                        store.delete(transaction, StringBinding.stringToEntry(key));
                    }
                }
            });
        } finally {
            LOCK.readLock().unlock();
        }
    }


    @Override
    public void truncate(final LocalDB.DB db) throws LocalDBException {
        LOGGER.trace("being truncate of " + db.toString() + ", size=" + this.size(db));
        final Date startDate = new Date();
        LOCK.writeLock().lock();
        try {
            stores.remove(db);

            environment.executeInTransaction(new TransactionalExecutable() {
                @Override
                public void execute(@NotNull Transaction transaction) {
                    environment.removeStore(db.toString(), transaction);
                }
            });

            stores.put(db, openStore(db));
        } finally {
            LOCK.writeLock().unlock();
        }
        LOGGER.trace("completed truncate of " + db.toString()
                + " (" + TimeDuration.fromCurrent(startDate).asCompactString() + ")"
                + ", size=" + this.size(db));
    }

    private Store openStore(final LocalDB.DB db) {
        return environment.computeInTransaction(new TransactionalComputable<Store>() {
            @Override
            public Store compute(@NotNull Transaction txn) {
                return environment.openStore(db.toString(), StoreConfig.WITHOUT_DUPLICATES, txn);
            }
        });
    }

    @Override
    public File getFileLocation() {
        return fileLocation;
    }

    @Override
    public LocalDB.Status getStatus() {
        return status;
    }

    private void outputStats() {
        Statistics statistics = environment.getStatistics();
        final Map<String,String> outputStats = new HashMap<>();
        outputStats.put(EnvironmentStatistics.BYTES_READ, Long.toString(statistics.getStatisticsItem(EnvironmentStatistics.BYTES_READ).getTotal()));
        outputStats.put(EnvironmentStatistics.BYTES_WRITTEN, Long.toString(statistics.getStatisticsItem(EnvironmentStatistics.BYTES_WRITTEN).getTotal()));
        outputStats.put(EnvironmentStatistics.TRANSACTIONS, Long.toString(statistics.getStatisticsItem(EnvironmentStatistics.TRANSACTIONS).getTotal()));
        LOGGER.trace("stats: " + JsonUtil.serializeMap(outputStats));
    }
}
