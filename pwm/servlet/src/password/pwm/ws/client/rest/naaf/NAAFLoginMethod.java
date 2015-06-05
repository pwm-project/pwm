package password.pwm.ws.client.rest.naaf;

public enum NAAFLoginMethod {
    PASSWORD("PASSWORD:1",NAAFMethods.NAAFPasswordMethodHandler.class),
    LDAP_PASSWORD("LDAP_PASSWORD:1",NAAFMethods.NAAFLdapPasswordMethodHandler.class),
    SECURITY_QUESTIONS("SECQUEST:1",NAAFMethods.NAAFSecurityQuestionsMethodHandler.class),
    EMAIL_OTP("EMAIL_OTP:1",NAAFMethods.NAAFEmailOTPMethodHandler.class),
    SMS_OTP("SMS_OTP:1",NAAFMethods.NAAFSMSOTPMethodHandler.class),
    SMARTPHONE("SMARTPHONE:1",NAAFMethods.NAAFSmartphoneMethodHandler.class),
    RADIUS("RADIUS:1",NAAFMethods.NAAFSmartphoneMethodHandler.class),
    TOTP("TOTP:1",NAAFMethods.NAAFTOTPMethodHandler.class),
    HOTP("HOTP:1",NAAFMethods.NAAFHOTPMethodHandler.class),

    ;

    private final String naafName;
    private final Class<? extends NAAFMethodHandler> naafMethodHandler;

    NAAFLoginMethod(String naafName, Class<? extends NAAFMethodHandler> naafMethodHandler) {
        this.naafName = naafName;
        this.naafMethodHandler = naafMethodHandler;
    }

    public String getNaafName() {
        return naafName;
    }

    public Class<? extends NAAFMethodHandler> getNaafMethodHandler() {
        return naafMethodHandler;
    }

}
