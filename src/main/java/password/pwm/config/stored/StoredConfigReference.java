package password.pwm.config.stored;

import java.io.Serializable;

public interface StoredConfigReference extends Serializable, Comparable {
    public RecordType getRecordType();

    public String getRecordID();

    public String getProfileID();

    enum RecordType {
        SETTING,
        LOCALE_BUNDLE,
        PROPERTY,
    }
}
