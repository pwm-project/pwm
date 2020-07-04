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

public class H2MVLocalDB
{
}


// No longer used, commented in case it may be resurrected some day.
/*

import java.io.File;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class H2MVLocalDB {} implements LocalDBProvider {
    private static final PwmLogger LOGGER = PwmLogger.forClass(H2MVLocalDB.class);

    private MVStore mvStore;
    private Map<LocalDB.DB,Map<String,String>> dbMaps = new HashMap<>();
    private File fileLocation;
    private volatile int writeTicks = 0;
    private ScheduledExecutorService executorService;


    @Override
    public void init(File dbDirectory, Map<String, String> initParameters, Map<Parameter,String> parameters) throws LocalDBException {
        LOGGER.trace("begin db open");
        mvStore = new MVStore.Builder()
                .fileName(dbDirectory + File.separator + "h2mv-localdb")
                .open();
        LOGGER.trace("open");

        for (final LocalDB.DB db : LocalDB.DB.values()) {
            LOGGER.trace("opening db " + db);
            final Map<String,String> dbMap = mvStore.openMap(db.toString());
            dbMaps.put(db,dbMap);
        }

        executorService = Executors.newSingleThreadScheduledExecutor(
                Helper.makePwmThreadFactory(
                        Helper.makeThreadName(null, this.getClass()) + "-",
                        true
                ));

        compact(true);

        this.fileLocation = dbDirectory;
    }

    @Override
    public void close() throws LocalDBException {
        mvStore.close();
    }

    @Override
    public int size(LocalDB.DB db) throws LocalDBException {
        return dbMaps.get(db).size();
    }
    @Override
    public boolean contains(LocalDB.DB db, String key) throws LocalDBException {
        return dbMaps.get(db).containsKey(key);
    }

    @Override
    public String get(LocalDB.DB db, String key) throws LocalDBException {
        return dbMaps.get(db).get(key);
    }


    @Override
    public LocalDB.LocalDBIterator<String> iterator(final LocalDB.DB db) throws LocalDBException {
        return new LocalDB.LocalDBIterator<String>() {

            private Iterator<String> stringIterator = dbMaps.get(db).keySet().iterator();
            @Override
            public void close() {

            }

            @Override
            public boolean hasNext() {
                return stringIterator.hasNext();
            }

            @Override
            public String next() {
                return stringIterator.next();
            }

            @Override
            public void remove() {
                stringIterator.remove();
            }
        };
    }

    @Override
    public void putAll(LocalDB.DB db, Map<String, String> keyValueMap) throws LocalDBException {
        dbMaps.get(db).putAll(keyValueMap);
        writeTicks++;
        postWriteActivities();
        mvStore.commit();
    }

    @Override
    public boolean put(LocalDB.DB db, String key, String value) throws LocalDBException {
        final String oldValue = dbMaps.get(db).put(key,value);
        writeTicks++;
        postWriteActivities();
        return oldValue != null;
    }

    @Override
    public boolean remove(LocalDB.DB db, String key) throws LocalDBException {
        final String oldValue = dbMaps.get(db).remove(key);
        writeTicks++;
        postWriteActivities();
        return oldValue != null;
    }

    @Override
    public void removeAll(LocalDB.DB db, Collection<String> keys) throws LocalDBException {
        dbMaps.get(db).keySet().removeAll(keys);
        writeTicks++;
        postWriteActivities();
    }


    @Override
    public void truncate(LocalDB.DB db) throws LocalDBException {
        dbMaps.get(db).clear();
        writeTicks++;
        compact(true);
        postWriteActivities();
    }

    @Override
    public File getFileLocation() {
        return fileLocation;
    }

    @Override
    public LocalDB.Status getStatus() {
        return LocalDB.Status.OPEN;
    }

    private void postWriteActivities() {
        mvStore.commit();
        if (writeTicks > 10000) {
            writeTicks = 0;
            compact(false);
        }
    }

    @Override
    public Map<String, Serializable> debugInfo() {
        return Collections.emptyMap();
    }

    private void compact(boolean full) {
        if (full) {
            executorService.schedule(new Runnable() {
                @Override
                public void run() {
                    LOGGER.trace("begin full compact");
                    final Date startDate = new Date();
                    mvStore.compactMoveChunks();
                    LOGGER.trace("end full compact " + TimeDuration.fromCurrent(startDate).asCompactString());
                }
            }, 0, TimeUnit.MILLISECONDS);

        } else {
            executorService.schedule(new Runnable() {
                @Override
                public void run() {
                    final int compactSize = 1024 * 1024 * 1024;
                    final int compactPercent = 70;
                    LOGGER.trace("begin compact");
                    final Date startDate = new Date();
                    mvStore.compact(compactPercent, compactSize);
                    LOGGER.trace("end compact " + TimeDuration.fromCurrent(startDate).asCompactString());
                }
            }, 0, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public Set<Flag> flags() {
        return Collections.emptySet();
    }
}
*/
