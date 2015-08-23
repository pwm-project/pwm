package password.pwm.config.stored;

import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmUnrecoverableException;

public interface StoredConfiguration {
    void resetSetting(PwmSetting setting, String profileID, UserIdentity userIdentity);

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
}
