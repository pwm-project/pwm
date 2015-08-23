package password.pwm.util.secure;

enum HmacAlgorithm {
    HMAC_SHA_256("HmacSHA256", PwmSecurityKey.Type.HMAC_256, 32),
    HMAC_SHA_512("HmacSHA512", PwmSecurityKey.Type.HMAC_512, 64),;

    private final String algorithmName;
    private final PwmSecurityKey.Type keyType;
    private final int length;

    HmacAlgorithm(String algorithmName, PwmSecurityKey.Type keyType, int length) {
        this.algorithmName = algorithmName;
        this.keyType = keyType;
        this.length = length;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }

    public PwmSecurityKey.Type getKeyType() {
        return keyType;
    }

    public int getLength() {
        return length;
    }
}
