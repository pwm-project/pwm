/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

import java.io.File;
import java.io.Serializable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * A lightweight interface for DB interaction.  Implementations may be backed by an embedded database, an RDBMS or
 * even a simple hashmap in memory.
 * <p/>
 * Implementations are required to implement a simplistic locking policy, where any method marked with {@link password.pwm.util.db.PwmDB.WriteOperation}
 * must block until any outstanding write or read methods are completed.  That is, concurrency is allowed for reads, but
 * writes are gaurenteed to be single threaded.
 *
 *
 * @author Jason D. Rivard
 */
public interface PwmDB {
// -------------------------- OTHER METHODS --------------------------

    public static final int MAX_KEY_LENGTH = 128;
    public static final int MAX_VALUE_LENGTH = 1024 * 10;

    @WriteOperation
    void close()
            throws PwmDBException;

    boolean contains(DB db, String key)
            throws PwmDBException;

    String get(DB db, String key)
            throws PwmDBException;

    @WriteOperation
    void init(File dbDirectory, String initString)
            throws PwmDBException;

    Iterator<TransactionItem> iterator(DB db)
            throws PwmDBException;

    @WriteOperation
    void putAll(DB db, Map<String,String> keyValueMap)
            throws PwmDBException;

    /**
     * Put a key/value into a database.  This operation inserts a new key/value pair
     * into the specified database.  If the key already exists in the database, then
     * the value is replaced.
     *
     * @param db database to perform the operation on
     * @param key key value
     * @param value string value
     * @return true if the key previously existed
     * @throws PwmDBException if there is an error writing to the store
     * @throws NullPointerException if the db, key or value is null
     * @throws IllegalArgumentException if the key is zero length, the key is larger than {@link #MAX_KEY_LENGTH} or the value is larger than {@link #MAX_VALUE_LENGTH}
     */
    @WriteOperation
    boolean put(DB db, String key, String value)
            throws PwmDBException;
   
    @WriteOperation
    boolean remove(DB db, String key)
            throws PwmDBException;

    @WriteOperation
    void removeAll(DB db, Collection<String> key)
            throws PwmDBException;

    void returnIterator(DB db)
            throws PwmDBException;

    int size(DB db)
            throws PwmDBException;

    @WriteOperation
    void truncate(DB db)
            throws PwmDBException;

    long diskSpaceUsed();

// -------------------------- ENUMERATIONS --------------------------

    enum DB {
        /** Used for various pwm operational data */
        PWM_META, 
        SHAREDHISTORY_META,
        SHAREDHISTORY_WORDS,
        WORDLIST_META,
        WORDLIST_WORDS,
        SEEDLIST_META,
        SEEDLIST_WORDS,
        PWM_STATS,
        EVENTLOG_META,
        EVENTLOG_EVENTS,
    }

// -------------------------- INNER CLASSES --------------------------

    public
    @Retention(RetentionPolicy.RUNTIME)
            @interface WriteOperation {
    }

    public static class TransactionItem implements Serializable, Comparable {
        private final DB db;
        private final String key;
        private final String value;

        public TransactionItem(final DB db, final String key, final String value)
        {
            if (key == null || value == null || db == null) {
                throw new IllegalArgumentException("db, key or value can not be null");
            }

            this.db = db;
            this.key = key;
            this.value = value;
        }

        public DB getDb()
        {
            return db;
        }

        public String getKey()
        {
            return key;
        }

        public String getValue()
        {
            return value;
        }

        public String toString()
        {
            return "db=" + db + ", key=" + key + ", value=" + value;
        }

        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final TransactionItem that = (TransactionItem) o;

            return db == that.db && key.equals(that.key) && value.equals(that.value);
        }

        public int hashCode() {
            int result;
            result = db.hashCode();
            result = 31 * result + key.hashCode();
            result = 31 * result + value.hashCode();
            return result;
        }

        public int compareTo(final Object o) {
            if (!(o instanceof TransactionItem)) {
                throw new IllegalArgumentException("can only compare same object type");
            }

            int result = db.compareTo(db);

            if (result == 0) {
                result = getKey().compareTo(((TransactionItem)o).getKey());

                if (result == 0) {
                    result = getValue().compareTo(((TransactionItem)o).getValue());
                }
            }

            return result;
        }
    }
}
