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

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Jason D. Rivard
 */
public class Memory_PwmDb implements PwmDB {
// ------------------------------ FIELDS ------------------------------

    private static final long MIN_FREE_MEMORY = 100 * 1024;  // 100kb
    private STATE state = STATE.NEW;
    private final Map<DB, Map<String, String>> maps = new HashMap<DB, Map<String, String>>();

// -------------------------- STATIC METHODS --------------------------

    private static void checkFreeMem() throws PwmDBException {
        final long currentFreeMem = Runtime.getRuntime().freeMemory();
        if (currentFreeMem < MIN_FREE_MEMORY) {
            System.gc();
            Helper.pause(100);
            System.gc();
            if (currentFreeMem < MIN_FREE_MEMORY) {
                throw new PwmDBException("out of memory, unable to add new records");
            }
        }
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public Memory_PwmDb()
    {
        for (final DB db : PwmDB.DB.values()) {
            final Map<String, String> newMap = new HashMap<String, String>();
            maps.put(db, newMap);
        }
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface PwmDB ---------------------

    @WriteOperation
    public void close()
            throws PwmDBException
    {
        state = STATE.CLOSED;
    }

    public boolean contains(final DB db, final String key)
            throws PwmDBException
    {
        if (state != STATE.OPEN) {
            throw new IllegalStateException("db is not open");
        }
        final Map<String, String> map = maps.get(db);
        return map.containsKey(key);
    }

    public String get(final DB db, final String key)
            throws PwmDBException
    {
        if (state != STATE.OPEN) {
            throw new IllegalStateException("db is not open");
        }
        final Map<String, String> map = maps.get(db);
        return map.get(key);
    }

    @WriteOperation
    public void init(final File dbDirectory, final String initString)
            throws PwmDBException
    {
        if (state == STATE.OPEN) {
            throw new IllegalStateException("cannot init db more than one time");
        }
        if (state == STATE.CLOSED) {
            throw new IllegalStateException("db is closed");
        }
        state = STATE.OPEN;
    }

    public Iterator<TransactionItem> iterator(final DB db) throws PwmDBException {
        return new DbIterator(db);
    }

    @WriteOperation
    public void putAll(final DB db, final Map<String,String> keyValueMap)
            throws PwmDBException
    {
        if (state != STATE.OPEN) {
            throw new IllegalStateException("db is not open");
        }

        checkFreeMem();

        if (keyValueMap != null) {
            final Map<String, String> map = maps.get(db);
            map.putAll(keyValueMap);
        }
    }

    @WriteOperation
    public boolean put(final DB db, final String key, final String value)
            throws PwmDBException
    {
        if (state != STATE.OPEN) {
            throw new IllegalStateException("db is not open");
        }

        checkFreeMem();

        final Map<String, String> map = maps.get(db);

        final boolean preExistingKey = map.containsKey(key);
        map.put(key, value);

        return preExistingKey;
    }

    @WriteOperation
    public boolean remove(final DB db, final String key)
            throws PwmDBException
    {
        if (state != STATE.OPEN) {
            throw new IllegalStateException("db is not open");
        }
        final Map<String, String> map = maps.get(db);
        map.remove(key);
        return true;
    }

    public void returnIterator(final DB db) throws PwmDBException {
    }

    public int size(final DB db)
            throws PwmDBException
    {
        if (state != STATE.OPEN) {
            throw new IllegalStateException("db is not open");
        }
        final Map<String, String> map = maps.get(db);
        return map.size();
    }

    @WriteOperation
    public void truncate(final DB db)
            throws PwmDBException
    {
        if (state != STATE.OPEN) {
            throw new IllegalStateException("db is not open");
        }
        final Map<String, String> map = maps.get(db);
        map.clear();
    }

// -------------------------- ENUMERATIONS --------------------------

    private enum STATE {
        NEW, OPEN, CLOSED
    }

// -------------------------- INNER CLASSES --------------------------

    private class DbIterator implements Iterator<TransactionItem> {
        private final DB db;
        private final Iterator<String> iterator;

        private DbIterator(final DB db) {
            this.db = db;
            iterator = maps.get(db).keySet().iterator();
        }

        public boolean hasNext() {
            return iterator.hasNext();
        }

        public TransactionItem next() {
            final String key = iterator.next();
            if (key != null) {
                final String value;
                try {
                    value = get(db, key);
                    return new TransactionItem(db, key, value);
                } catch (PwmDBException e) {
                    throw new IllegalStateException("unexpected get error",e);
                }
            }
            return null;
        }

        public void remove() {
            iterator.remove();
        }
    }

    public long diskSpaceUsed() {
        return 0;
    }

    public void removeAll(final DB db, final Collection<String> keys) throws PwmDBException {
        maps.get(db).keySet().removeAll(keys);
    }
}
