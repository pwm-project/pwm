/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.util.db;

import password.pwm.util.java.ClosableIterator;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

public interface DatabaseAccessor
{
    /**
     * Indicates if the method is actually performing an DB operation.
     */
    @Retention( RetentionPolicy.RUNTIME )
    @interface DbOperation
    {
    }

    /**
     * Indicates if the method may cause a modification of the database.
     */
    @Retention( RetentionPolicy.RUNTIME )
    @interface DbModifyOperation
    {
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
    @DbModifyOperation
    boolean putIfAbsent(
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

    ClosableIterator<Map.Entry<String, String>> iterator( DatabaseTable table )
            throws DatabaseException;

    @DbOperation
    @DbModifyOperation
    void remove(
            DatabaseTable table,
            String key
    )
            throws DatabaseException;

    @DbOperation
    int size( DatabaseTable table ) throws
            DatabaseException;

    boolean isConnected( );
}
