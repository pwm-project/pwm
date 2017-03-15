/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.util.java.JavaHelper;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Jason D. Rivard
 */
public class Memory_LocalDB implements LocalDBProvider {
// ------------------------------ FIELDS ------------------------------

    private static final long MIN_FREE_MEMORY = 1024 * 1024;  // 1mb
    private LocalDB.Status state = LocalDB.Status.NEW;
    private Map<LocalDB.DB, Map<String, String>> maps = new HashMap<>();

// -------------------------- STATIC METHODS --------------------------

    private static void checkFreeMem() throws LocalDBException {
        final long currentFreeMem = Runtime.getRuntime().freeMemory();
        if (currentFreeMem < MIN_FREE_MEMORY) {
            System.gc();
            JavaHelper.pause(100);
            System.gc();
            if (currentFreeMem < MIN_FREE_MEMORY) {
                throw new LocalDBException(new ErrorInformation(PwmError.ERROR_LOCALDB_UNAVAILABLE,"out of memory, unable to add new records"));
            }
        }
    }

    private void opertationPreCheck() throws LocalDBException {
        if (state != LocalDB.Status.OPEN) {
            throw new IllegalStateException("db is not open");
        }
        checkFreeMem();
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public Memory_LocalDB() {
        for (final LocalDB.DB db : LocalDB.DB.values()) {
            final Map<String, String> newMap = new ConcurrentHashMap<>();
            maps.put(db, newMap);
        }
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmLocalDB.DB ---------------------

    @LocalDB.WriteOperation
    public void close()
            throws LocalDBException {
        state = LocalDB.Status.CLOSED;
        for (final LocalDB.DB db : LocalDB.DB.values()) {
            maps.get(db).clear();
        }
    }

    public boolean contains(final LocalDB.DB db, final String key)
            throws LocalDBException {
        opertationPreCheck();
        final Map<String, String> map = maps.get(db);
        return map.containsKey(key);
    }

    public String get(final LocalDB.DB db, final String key)
            throws LocalDBException {
        opertationPreCheck();
        final Map<String, String> map = maps.get(db);
        return map.get(key);
    }

    @LocalDB.WriteOperation
    public void init(final File dbDirectory, final Map<String, String> initParameters, final Map<LocalDBProvider.Parameter,String> parameters)
            throws LocalDBException {
        final boolean readOnly = LocalDBUtility.hasBooleanParameter(Parameter.readOnly, parameters);
        if (readOnly) {
            maps = Collections.unmodifiableMap(maps);
        }
        if (state == LocalDB.Status.OPEN) {
            throw new IllegalStateException("cannot init db more than one time");
        }
        if (state == LocalDB.Status.CLOSED) {
            throw new IllegalStateException("db is closed");
        }
        state = LocalDB.Status.OPEN;
    }

    public LocalDB.LocalDBIterator<String> iterator(final LocalDB.DB db) throws LocalDBException {
        return new DbIterator(db);
    }

    @LocalDB.WriteOperation
    public void putAll(final LocalDB.DB db, final Map<String, String> keyValueMap)
            throws LocalDBException {
        opertationPreCheck();

        if (keyValueMap != null) {
            final Map<String, String> map = maps.get(db);
            map.putAll(keyValueMap);
        }
    }

    @LocalDB.WriteOperation
    public boolean put(final LocalDB.DB db, final String key, final String value)
            throws LocalDBException {
        opertationPreCheck();

        final Map<String, String> map = maps.get(db);
        return null != map.put(key, value);
    }

    @LocalDB.WriteOperation
    public boolean remove(final LocalDB.DB db, final String key)
            throws LocalDBException {
        opertationPreCheck();

        final Map<String, String> map = maps.get(db);
        return null != map.remove(key);
    }

    public void returnIterator(final LocalDB.DB db) throws LocalDBException {
    }

    public int size(final LocalDB.DB db)
            throws LocalDBException {
        opertationPreCheck();

        final Map<String, String> map = maps.get(db);
        return map.size();
    }

    @LocalDB.WriteOperation
    public void truncate(final LocalDB.DB db)
            throws LocalDBException {
        opertationPreCheck();

        final Map<String, String> map = maps.get(db);
        map.clear();
    }

    public void removeAll(final LocalDB.DB db, final Collection<String> keys) throws LocalDBException {
        opertationPreCheck();

        maps.get(db).keySet().removeAll(keys);
    }

    public LocalDB.Status getStatus() {
        return state;
    }

    @Override
    public Map<String, Serializable> debugInfo() {
        return Collections.emptyMap();
    }


    private class DbIterator<K> implements LocalDB.LocalDBIterator<String> {
        private final Iterator<String> iterator;

        private DbIterator(final LocalDB.DB db) {
            iterator = maps.get(db).keySet().iterator();
        }

        public boolean hasNext() {
            return iterator.hasNext();
        }

        public String next() {
            return iterator.next();
        }

        public void remove() {
            iterator.remove();
        }

        public void close() {
        }
    }

    public File getFileLocation() {
        return null;
    }

    @Override
    public Set<Flag> flags() {
        return Collections.emptySet();
    }
}
