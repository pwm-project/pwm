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

package password.pwm.svc.wordlist;

import password.pwm.AppAttribute;
import password.pwm.PwmApplication;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.FileSystemUtility;
import password.pwm.util.localdb.LocalDB;
import password.pwm.util.localdb.LocalDBException;

import java.util.Map;

class LocalDBWordlistBucket extends AbstractWordlistBucket implements WordlistBucket
{
    private final LocalDB.DB db;
    private final LocalDB localDB;

    LocalDBWordlistBucket(
            final PwmApplication pwmApplication,
            final WordlistConfiguration wordlistConfiguration,
            final WordlistType type
    )
    {
        super( pwmApplication, wordlistConfiguration, type );
        this.localDB = pwmApplication.getLocalDB();
        this.db = wordlistConfiguration.getDb();
    }

    @Override
    void putValues( final Map<String, String> values )
            throws PwmUnrecoverableException
    {
        try
        {
            localDB.putAll( db, values );
        }
        catch ( final LocalDBException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_LOCALDB_UNAVAILABLE, "error while writing words to wordlist: " + e.getMessage() );
        }
    }

    @Override
    String getValue( final String key )
            throws PwmUnrecoverableException
    {
        try
        {
            return pwmApplication.getLocalDB().get( db, key );
        }
        catch ( final Exception e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "error while generating random word: " + e.getMessage() );
        }
    }

    @Override
    boolean containsKey( final String key )
            throws PwmUnrecoverableException
    {
        try
        {
            return pwmApplication.getLocalDB().contains( db, key );
        }
        catch ( final LocalDBException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_LOCALDB_UNAVAILABLE, e.getMessage() );
        }
    }

    @Override
    public long size() throws PwmUnrecoverableException
    {
        try
        {
            return localDB.size( db );
        }
        catch ( final LocalDBException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_LOCALDB_UNAVAILABLE, e.getMessage() );
        }
    }


    @Override
    public void clear() throws PwmUnrecoverableException
    {
        try
        {
            localDB.truncate( db );
        }
        catch ( final LocalDBException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_LOCALDB_UNAVAILABLE, e.getMessage() );
        }
    }

    @Override
    public WordlistStatus readWordlistStatus()
    {
        final AppAttribute appAttribute = wordlistConfiguration.getMetaDataAppAttribute();
        final WordlistStatus storedValue = pwmApplication.readAppAttribute( appAttribute, WordlistStatus.class );
        if ( storedValue != null )
        {
            return storedValue;
        }
        return WordlistStatus.builder().build();
    }

    @Override
    public void writeWordlistStatus( final WordlistStatus wordlistStatus )
    {
        final AppAttribute appAttribute = wordlistConfiguration.getMetaDataAppAttribute();
        pwmApplication.writeAppAttribute( appAttribute, wordlistStatus );
    }

    @Override
    public long spaceRemaining()
    {
        return FileSystemUtility.diskSpaceRemaining( localDB.getFileLocation() );
    }
}
