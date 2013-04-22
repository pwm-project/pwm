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

import java.io.File;
import java.util.Collection;
import java.util.Map;

public interface LocalDBProvider {
    @LocalDB.WriteOperation
    void close()
            throws LocalDBException;

    @LocalDB.ReadOperation
    boolean contains(LocalDB.DB db, String key)
            throws LocalDBException;

    @LocalDB.ReadOperation
    String get(LocalDB.DB db, String key)
            throws LocalDBException;

    @LocalDB.WriteOperation
    void init(File dbDirectory, Map<String, String> initParameters, boolean readOnly)
            throws LocalDBException;

    LocalDB.PwmDBIterator<String> iterator(LocalDB.DB db)
            throws LocalDBException;

    @LocalDB.WriteOperation
    void putAll(LocalDB.DB db, Map<String, String> keyValueMap)
            throws LocalDBException;

    @LocalDB.WriteOperation
    boolean put(LocalDB.DB db, String key, String value)
            throws LocalDBException;

    @LocalDB.WriteOperation
    boolean remove(LocalDB.DB db, String key)
            throws LocalDBException;

    @LocalDB.WriteOperation
    void removeAll(LocalDB.DB db, Collection<String> key)
            throws LocalDBException;

    @LocalDB.ReadOperation
    int size(LocalDB.DB db)
            throws LocalDBException;

    @LocalDB.WriteOperation
    void truncate(LocalDB.DB db)
            throws LocalDBException;

    File getFileLocation();

    LocalDB.Status getStatus();


}
