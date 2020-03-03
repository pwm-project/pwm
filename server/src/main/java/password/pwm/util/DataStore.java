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

package password.pwm.util;

import password.pwm.error.PwmDataStoreException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.ClosableIterator;

import java.util.Map;

public interface DataStore
{
    enum Status
    {
        NEW, OPEN, CLOSED
    }

    void close( )
            throws PwmDataStoreException;

    boolean contains( String key )
            throws PwmDataStoreException, PwmUnrecoverableException;

    String get( String key )
            throws PwmDataStoreException, PwmUnrecoverableException;

    ClosableIterator<Map.Entry<String, String>> iterator( )
            throws PwmDataStoreException, PwmUnrecoverableException;

    Status status( );

    boolean put( String key, String value )
            throws PwmDataStoreException, PwmUnrecoverableException;

    boolean putIfAbsent( String key, String value )
            throws PwmDataStoreException, PwmUnrecoverableException;

    void remove( String key )
            throws PwmDataStoreException, PwmUnrecoverableException;

    long size( )
            throws PwmDataStoreException, PwmUnrecoverableException;
}
