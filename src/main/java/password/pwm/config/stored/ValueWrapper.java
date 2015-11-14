package password.pwm.config.stored;

import password.pwm.config.StoredValue;

class ValueWrapper {
    private final StoredValue storedValue;
    private final ValueMetaData metaData;

    public ValueWrapper(StoredValue storedValue, ValueMetaData metaData) {
        this.storedValue = storedValue;
        this.metaData = metaData;
    }

    public StoredValue getStoredValue() {
        return storedValue;
    }

    public ValueMetaData getMetaData() {
        return metaData;
    }
}
