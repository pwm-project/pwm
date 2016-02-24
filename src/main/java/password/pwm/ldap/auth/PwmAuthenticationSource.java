package password.pwm.ldap.auth;

public enum PwmAuthenticationSource {
    LOGIN_FORM,
    CAS,
    BASIC_AUTH,
    SSO_HEADER,
    OAUTH,

    NEW_USER_REGISTRATION,
    FORGOTTEN_PASSWORD,
    USER_ACTIVATION,
}
