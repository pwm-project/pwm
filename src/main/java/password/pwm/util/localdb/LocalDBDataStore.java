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

import password.pwm.error.PwmDataStoreException;
import password.pwm.util.ClosableIterator;
import password.pwm.util.DataStore;

import java.util.Map;

public class LocalDBDataStore implements DataStore {
    private final LocalDB localDB;
    private final LocalDB.DB db;

    public LocalDBDataStore(final LocalDB localDB, final LocalDB.DB db) {
        this.localDB = localDB;
        this.db = db;
    }

    public void close() throws PwmDataStoreException {
        localDB.close();
    }

    public boolean contains(String key) throws PwmDataStoreException {
        return localDB.contains(db, key);
    }

    public String get(String key) throws PwmDataStoreException {
        return localDB.get(db, key);
    }

    public ClosableIterator<String> iterator() throws PwmDataStoreException {
        return localDB.iterator(db);
    }

    public void putAll(Map<String, String> keyValueMap) throws PwmDataStoreException {
        localDB.putAll(db, keyValueMap);
    }

    public Status status() {
        final LocalDB.Status dbStatus = localDB.status();
        if (dbStatus == null) {
            return null;
        }
        switch (dbStatus) {
            case OPEN:
                return Status.OPEN;

            case CLOSED:
                return Status.CLOSED;

            case NEW:
                return Status.NEW;

            default:
                throw new IllegalStateException("unknown localDB state");
        }
    }

    public boolean put(String key, String value) throws PwmDataStoreException {
        return localDB.put(db, key, value);
    }

    public boolean remove(String key) throws PwmDataStoreException {
        return localDB.remove(db, key);
    }

    public int size() throws PwmDataStoreException {
        return localDB.size(db);
    }
}
