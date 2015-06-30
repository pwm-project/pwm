package password.pwm.util.secure;

import password.pwm.PwmConstants;

public enum PwmBlockAlgorithm {
    AES(      "AES", "AES",         "AES-128"),
    AES_HMAC( "AES", "AES_HMAC",    "AES-128+Hmac256"),
    CONFIG(   "AES", "",            PwmConstants.PWM_APP_NAME + " Configuration AES"),

    ;

    private final String algName;
    private final byte[] prefix;
    private final String label;

    PwmBlockAlgorithm(String algName, String prefix, String label) {
        this.algName = algName;
        this.prefix = prefix.getBytes(PwmConstants.DEFAULT_CHARSET);
        this.label = label;
    }

    public String getAlgName() {
        return algName;
    }

    public byte[] getPrefix() {
        return prefix;
    }

    public String getLabel() {
        return label;
    }
}
