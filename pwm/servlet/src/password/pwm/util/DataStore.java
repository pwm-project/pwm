package password.pwm.util;

import password.pwm.PwmApplication;
import password.pwm.error.PwmDataStoreException;
import password.pwm.util.db.DatabaseTable;
import password.pwm.util.localdb.LocalDB;

import java.util.Iterator;

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

    DataStoreIterator<String> iterator()
            throws PwmDataStoreException;

    Status status();

    boolean put(String key, String value)
            throws PwmDataStoreException;

    boolean remove(String key)
            throws PwmDataStoreException;

    int size()
            throws PwmDataStoreException;

    public static interface DataStoreIterator<K> extends Iterator<String> {
        public void close();
    }
}
