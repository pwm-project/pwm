package password.pwm.config.stored;

import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.logging.PwmLogger;

import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class NGStoredConfiguration {
    final static private PwmLogger LOGGER = PwmLogger.forClass(NGStoredConfiguration.class);

    final private Engine engine = new Engine();

    NGStoredConfiguration(Map<StoredConfigReference, ValueWrapper> values) {
        engine.values.putAll(values);
    }

    private String readProperty(final ConfigurationProperty configurationProperty) {
        StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.PROPERTY,
                configurationProperty.getKey(),
                null
        );
        final ValueWrapper valueWrapper = engine.read(storedConfigReference);
        if (valueWrapper != null) {
            final StoredValue storedValue = valueWrapper.getStoredValue();
            if (storedValue == null | !(storedValue instanceof StringValue)) {
                return null;
            }
            return (String) storedValue.toNativeObject();
        }
        return null;
    }

    private void writeProperty(final ConfigurationProperty configurationProperty, final String value, final UserIdentity userIdentity)
            throws PwmOperationalException
    {
        StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.PROPERTY,
                configurationProperty.getKey(),
                null
        );
        final StoredValue storedValue = new StringValue(value);
        engine.write(storedConfigReference, storedValue, userIdentity);
    }

    void resetSetting(PwmSetting setting, String profileID, UserIdentity userIdentity) {
        final StoredConfigReference storedConfigReference = new StoredConfigReferenceBean(
                StoredConfigReference.RecordType.SETTING,
                setting.getKey(),
                profileID
        );
        engine.reset(storedConfigReference, userIdentity);
    }

    /*
    boolean isDefaultValue(PwmSetting setting);

    boolean isDefaultValue(PwmSetting setting, String profileID);

    StoredValue readSetting(PwmSetting setting);

    StoredValue readSetting(PwmSetting setting, String profileID);

    void copyProfileID(PwmSettingCategory category, String sourceID, String destinationID, UserIdentity userIdentity)
            throws PwmUnrecoverableException;

    void writeSetting(
            PwmSetting setting,
            StoredValue value,
            UserIdentity userIdentity
    ) throws PwmUnrecoverableException;

    void writeSetting(
            PwmSetting setting,
            String profileID,
            StoredValue value,
            UserIdentity userIdentity
    ) throws PwmUnrecoverableException;
       */

    public boolean isWriteLocked() {
        return engine.isWriteLocked();
    }

    ;

    public void writeLock() {
        engine.writeLock();
    }


    private class Engine {
        private final Map<StoredConfigReference, ValueWrapper> values = new TreeMap<>();
        private final ReentrantReadWriteLock bigLock = new ReentrantReadWriteLock();
        private boolean writeLocked = false;

        public ValueWrapper read(StoredConfigReference storedConfigReference) {
            bigLock.readLock().lock();
            try {
                return values.get(storedConfigReference);
            } finally {
                bigLock.readLock().unlock();
            }
        }

        public void write(StoredConfigReference storedConfigReference, StoredValue value, UserIdentity userIdentity)
        {
            checkWriteLock();
            bigLock.writeLock().lock();
            try {
                ValueWrapper valueWrapper = new ValueWrapper(value, new ValueMetaData(new Date(), userIdentity));
                if (values.containsKey(storedConfigReference)) {
                    values.put(storedConfigReference, valueWrapper);
                }

            } finally {
                bigLock.writeLock().unlock();
            }
        }

        public void reset(StoredConfigReference storedConfigReference, UserIdentity userIdentity)
        {
            checkWriteLock();
            bigLock.writeLock().lock();
            try {
                if (values.containsKey(storedConfigReference)) {
                    ValueWrapper emptyValue = new ValueWrapper(null, new ValueMetaData(new Date(), userIdentity));
                    values.put(storedConfigReference, emptyValue);
                }
            } finally {
                bigLock.writeLock().unlock();
            }
        }

        private void checkWriteLock()
        {
            if (writeLocked) {
                throw new IllegalStateException("attempt to modify writeLock configuration");
            }
        }

        public boolean isWriteLocked() {
            return writeLocked;
        }

        ;

        public void writeLock() {
            writeLocked = true;
        }
    }
}
