package password.pwm.util.secure;

public enum PwmHashAlgorithm {
    MD5("MD5"),
    SHA1("SHA1"),
    SHA256("SHA-256"),
    SHA512("SHA-512"),;

    private final String algName;

    PwmHashAlgorithm(String algName) {
        this.algName = algName;
    }

    public String getAlgName() {
        return algName;
    }
}
