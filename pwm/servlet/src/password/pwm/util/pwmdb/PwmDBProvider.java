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

package password.pwm.util.pwmdb;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public interface PwmDBProvider {
    @PwmDB.WriteOperation
    void close()
            throws PwmDBException;

    @PwmDB.ReadOperation
    boolean contains(PwmDB.DB db, String key)
            throws PwmDBException;

    @PwmDB.ReadOperation
    String get(PwmDB.DB db, String key)
            throws PwmDBException;

    @PwmDB.WriteOperation
    void init(File dbDirectory, Map<String, String> initParameters, boolean readOnly)
            throws PwmDBException;

    Iterator<String> iterator(PwmDB.DB db)
            throws PwmDBException;

    @PwmDB.WriteOperation
    void putAll(PwmDB.DB db, Map<String, String> keyValueMap)
            throws PwmDBException;

    @PwmDB.WriteOperation
    boolean put(PwmDB.DB db, String key, String value)
            throws PwmDBException;

    @PwmDB.WriteOperation
    boolean remove(PwmDB.DB db, String key)
            throws PwmDBException;

    @PwmDB.WriteOperation
    void removeAll(PwmDB.DB db, Collection<String> key)
            throws PwmDBException;

    void returnIterator(PwmDB.DB db)
            throws PwmDBException;

    @PwmDB.ReadOperation
    int size(PwmDB.DB db)
            throws PwmDBException;

    @PwmDB.WriteOperation
    void truncate(PwmDB.DB db)
            throws PwmDBException;

    File getFileLocation();

    PwmDB.Status getStatus();


}
