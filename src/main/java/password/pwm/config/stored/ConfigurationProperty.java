package password.pwm.config.stored;

public enum ConfigurationProperty {
    CONFIG_IS_EDITABLE("configIsEditable"),
    CONFIG_EPOCH("configEpoch"),
    LDAP_TEMPLATE("configTemplate"),
    STORAGE_TEMPALTE("storageTemplate"),
    NOTES("notes"),
    PASSWORD_HASH("configPasswordHash"),
    CONFIG_ON_START("saveConfigOnStart"),
    MODIFIFICATION_TIMESTAMP("modificationTimestamp"),

    ;

    private final String key;

    ConfigurationProperty(String key)
    {
        this.key = key;
    }

    public String getKey()
    {
        return key;
    }
}
