package password.pwm.config.stored;

public enum ConfigurationProperty {
    PROPERTY_KEY_CONFIG_IS_EDITABLE("configIsEditable"),
    PROPERTY_KEY_CONFIG_EPOCH("configEpoch"),
    PROPERTY_KEY_TEMPLATE("configTemplate"),
    PROPERTY_KEY_NOTES("notes"),
    PROPERTY_KEY_PASSWORD_HASH("configPasswordHash"),
    PROPERTY_KEY_SAVE_CONFIG_ON_START("saveConfigOnStart"),
    ;

    private final String key;

    private ConfigurationProperty(String key)
    {
        this.key = key;
    }

    public String getKey()
    {
        return key;
    }
}
