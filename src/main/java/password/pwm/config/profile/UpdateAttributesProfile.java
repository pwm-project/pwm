package password.pwm.config.profile;

import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfiguration;

import java.util.Locale;
import java.util.Map;

public class UpdateAttributesProfile extends AbstractProfile implements Profile {

    private static final ProfileType PROFILE_TYPE = ProfileType.UpdateAttributes;

    protected UpdateAttributesProfile(String identifier, Map<PwmSetting, StoredValue> storedValueMap) {
        super(identifier, storedValueMap);
    }

    public static UpdateAttributesProfile makeFromStoredConfiguration(final StoredConfiguration storedConfiguration, final String identifier) {
        final Map<PwmSetting,StoredValue> valueMap = makeValueMap(storedConfiguration, identifier, PROFILE_TYPE.getCategory());
        return new UpdateAttributesProfile(identifier, valueMap);

    }

    @Override
    public String getDisplayName(Locale locale)
    {
        return this.getIdentifier();
    }

    @Override
    public ProfileType profileType() {
        return PROFILE_TYPE;
    }
}
