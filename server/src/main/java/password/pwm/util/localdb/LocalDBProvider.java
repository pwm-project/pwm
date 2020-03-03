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

package password.pwm.util.localdb;

import java.io.File;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface LocalDBProvider
{

    enum Flag
    {
        SlowSizeOperations,
    }

    enum Parameter
    {
        readOnly,
        aggressiveCompact,
    }

    @LocalDB.WriteOperation
    void close( )
            throws LocalDBException;

    @LocalDB.ReadOperation
    boolean contains( LocalDB.DB db, String key )
            throws LocalDBException;

    @LocalDB.ReadOperation
    String get( LocalDB.DB db, String key )
            throws LocalDBException;

    @LocalDB.WriteOperation
    void init( File dbDirectory, Map<String, String> initParameters, Map<Parameter, String> parameters )
            throws LocalDBException;

    LocalDB.LocalDBIterator<Map.Entry<String, String>> iterator( LocalDB.DB db )
            throws LocalDBException;

    @LocalDB.WriteOperation
    void putAll( LocalDB.DB db, Map<String, String> keyValueMap )
            throws LocalDBException;

    @LocalDB.WriteOperation
    boolean put( LocalDB.DB db, String key, String value )
            throws LocalDBException;

    @LocalDB.WriteOperation
    boolean putIfAbsent( LocalDB.DB db, String key, String value )
            throws LocalDBException;

    @LocalDB.WriteOperation
    boolean remove( LocalDB.DB db, String key )
            throws LocalDBException;

    @LocalDB.WriteOperation
    void removeAll( LocalDB.DB db, Collection<String> key )
            throws LocalDBException;

    @LocalDB.ReadOperation
    long size( LocalDB.DB db )
            throws LocalDBException;

    @LocalDB.WriteOperation
    void truncate( LocalDB.DB db )
            throws LocalDBException;

    File getFileLocation( );

    LocalDB.Status getStatus( );

    Map<String, Serializable> debugInfo( );

    Set<Flag> flags( );
}
