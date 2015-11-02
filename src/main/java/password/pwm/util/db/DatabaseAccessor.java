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

package password.pwm.util.db;

import password.pwm.util.ClosableIterator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public interface DatabaseAccessor {
    /**
     * Indicates if the method is actually performing an DB operation.
     */
    public
    @Retention(RetentionPolicy.RUNTIME)
    @interface DbOperation {
    }

    /**
     * Indicates if the method may cause a modification of the database.
     */
    public
    @Retention(RetentionPolicy.RUNTIME)
    @interface DbModifyOperation {
    }


    @DbOperation
    @DbModifyOperation
    boolean put(
            DatabaseTable table,
            String key,
            String value
    )
            throws DatabaseException;

    @DbOperation
    boolean contains(
            DatabaseTable table,
            String key
    )
            throws DatabaseException;

    @DbOperation
    String get(
            DatabaseTable table,
            String key
    )
            throws DatabaseException;

    ClosableIterator<String> iterator(DatabaseTable table)
            throws DatabaseException;

    @DbOperation
    @DbModifyOperation
    boolean remove(
            DatabaseTable table,
            String key
    )
            throws DatabaseException;

    @DbOperation
    int size(DatabaseTable table) throws
            DatabaseException;
}
