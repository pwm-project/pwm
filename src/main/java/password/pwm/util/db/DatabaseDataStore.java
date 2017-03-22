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

package password.pwm.util.db;

import password.pwm.error.PwmDataStoreException;
import password.pwm.util.DataStore;
import password.pwm.util.java.ClosableIterator;

public class DatabaseDataStore implements DataStore {
    private final DatabaseAccessor databaseAccessor;
    private final DatabaseTable table;

    public DatabaseDataStore(final DatabaseAccessor databaseAccessor, final DatabaseTable table) {
        this.databaseAccessor = databaseAccessor;
        this.table = table;
    }

    public void close() throws PwmDataStoreException {
    }

    public boolean contains(final String key) throws PwmDataStoreException {
        return databaseAccessor.contains(table, key);
    }

    public String get(final String key) throws PwmDataStoreException {
        return databaseAccessor.get(table,key);
    }

    public ClosableIterator<String> iterator() throws PwmDataStoreException {
        return databaseAccessor.iterator(table);
    }

    public Status status() {
        if (databaseAccessor == null) {
            return null;
        }

        return Status.OPEN;
    }

    public boolean put(final String key, final String value) throws PwmDataStoreException {
        return databaseAccessor.put(table, key, value);
    }

    public boolean remove(final String key) throws PwmDataStoreException {
        return databaseAccessor.remove(table, key);
    }

    public int size() throws PwmDataStoreException {
        return databaseAccessor.size(table);
    }
}
