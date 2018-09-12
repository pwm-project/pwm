/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

import password.pwm.util.java.ClosableIterator;

import java.io.File;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.Map;

/**
 * <p>A lightweight interface for DB interaction.  Implementations may be backed by an embedded database, an RDBMS or
 * even a simple hashmap in memory.</p>
 *
 * <p>Implementations are required to implement a simplistic locking policy, where any method marked with {@link LocalDB.WriteOperation}
 * must block until any outstanding write or read methods are completed.  That is, concurrency is allowed for reads, but
 * writes are guaranteed to be single threaded.</p>
 *
 * @author Jason D. Rivard
 */
public interface LocalDB
{
    int MAX_KEY_LENGTH = 256;
    int MAX_VALUE_LENGTH = 1024 * 100;

    enum Status
    {
        NEW, OPEN, CLOSED
    }

    @WriteOperation
    void close( )
            throws LocalDBException;

    @ReadOperation
    boolean contains( DB db, String key )
            throws LocalDBException;

    @ReadOperation
    String get( DB db, String key )
            throws LocalDBException;

    LocalDBIterator<String> iterator( DB db )
            throws LocalDBException;

    @WriteOperation
    void putAll( DB db, Map<String, String> keyValueMap )
            throws LocalDBException;

    Status status( );

    /**
     * Put a key/value into a database.  This operation inserts a new key/value pair
     * into the specified database.  If the key already exists in the database, then
     * the value is replaced.
     *
     * @param db    database to perform the operation on
     * @param key   key value
     * @param value string value
     * @return true if the key previously existed
     * @throws LocalDBException         if there is an error writing to the store
     * @throws NullPointerException     if the db, key or value is null
     * @throws IllegalArgumentException if the key is zero length, the key is larger than {@link #MAX_KEY_LENGTH} or the value is larger than {@link #MAX_VALUE_LENGTH}
     */
    @WriteOperation
    boolean put( DB db, String key, String value )
            throws LocalDBException;

    @WriteOperation
    boolean putIfAbsent( DB db, String key, String value )
            throws LocalDBException;

    @WriteOperation
    boolean remove( DB db, String key )
            throws LocalDBException;

    @WriteOperation
    void removeAll( DB db, Collection<String> key )
            throws LocalDBException;

    @ReadOperation
    int size( DB db )
            throws LocalDBException;

    @WriteOperation
    void truncate( DB db )
            throws LocalDBException;

    File getFileLocation( );

    Map<String, Serializable> debugInfo( );

    enum DB
    {
        /**
         * Used for various pwm operational data.
         */
        PWM_META( true ),
        SHAREDHISTORY_META( true ),
        SHAREDHISTORY_WORDS( true ),
        // WORDLIST_META(true), // @deprecated
        WORDLIST_WORDS( true ),
        // SEEDLIST_META(true), // @deprecated
        SEEDLIST_WORDS( true ),
        PWM_STATS( true ),
        EVENTLOG_EVENTS( true ),
        EMAIL_QUEUE( true ),
        SMS_QUEUE( true ),
        RESPONSE_STORAGE( true ),
        OTP_SECRET( true ),
        TOKENS( true ),
        INTRUDER( true ),
        AUDIT_QUEUE( true ),
        AUDIT_EVENTS( true ),
        USER_CACHE( true ),
        TEMP( false ),
        SYSLOG_QUEUE( true ),
        CACHE( false ),

        REPORT_QUEUE( false ),;

        private final boolean backup;

        DB( final boolean backup )
        {
            this.backup = backup;
        }

        public boolean isBackup( )
        {
            return backup;
        }
    }


    @Retention( RetentionPolicy.RUNTIME )
    @interface
    ReadOperation
    {
    }

    @Retention( RetentionPolicy.RUNTIME )
    @interface
    WriteOperation
    {
    }


    interface LocalDBIterator<K> extends ClosableIterator<String>
    {
    }
}
