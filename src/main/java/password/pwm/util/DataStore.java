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

package password.pwm.util;

import password.pwm.error.PwmDataStoreException;

public interface DataStore {
    public static enum Status {
        NEW, OPEN, CLOSED
    }

    void close()
            throws PwmDataStoreException;

    boolean contains(String key)
            throws PwmDataStoreException;

    String get(String key)
            throws PwmDataStoreException;

    ClosableIterator<String> iterator()
            throws PwmDataStoreException;

    Status status();

    boolean put(String key, String value)
            throws PwmDataStoreException;

    boolean remove(String key)
            throws PwmDataStoreException;

    int size()
            throws PwmDataStoreException;
}
