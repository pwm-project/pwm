package password.pwm.util.stats;

import java.util.*;

public enum Statistic {
    AUTHENTICATIONS(Type.INCREMENTOR, "Authentications"),
    AUTHENTICATION_FAILURES(Type.INCREMENTOR, "AuthenticationFailures"),
    AUTHENTICATION_EXPIRED(Type.INCREMENTOR, "Authentications_Expired"),
    AUTHENTICATION_PRE_EXPIRED(Type.INCREMENTOR, "Authentications_PreExpired"),
    AUTHENTICATION_EXPIRED_WARNING(Type.INCREMENTOR, "Authentications_ExpiredWarning"),
    PWM_STARTUPS(Type.INCREMENTOR, "PWM_Startups"),
    PWM_UNKNOWN_ERRORS(Type.INCREMENTOR, "PWM_UnknownErrors"),
    PASSWORD_CHANGES(Type.INCREMENTOR, "PasswordChanges"),
    RECOVERY_SUCCESSES(Type.INCREMENTOR, "RecoverySuccesses"),
    RECOVERY_FAILURES(Type.INCREMENTOR, "RecoveryFailures"),
    EMAIL_SEND_SUCCESSES(Type.INCREMENTOR, "EmailSendSuccesses"),
    EMAIL_SEND_FAILURES(Type.INCREMENTOR, "EmailSendFailures"),
    PASSWORD_RULE_CHECKS(Type.INCREMENTOR, "PasswordRuleChecks"),
    HTTP_REQUESTS(Type.INCREMENTOR, "HttpRequests"),
    HTTP_SESSIONS(Type.INCREMENTOR, "HttpSessions"),
    ACTIVATED_USERS(Type.INCREMENTOR, "ActivatedUsers"),
    NEW_USERS(Type.INCREMENTOR, "NewUsers"),
    LOCKED_USERS(Type.INCREMENTOR, "LockedUsers"),
    LOCKED_ADDRESSES(Type.INCREMENTOR, "LockedAddresses"),
    CAPTCHA_SUCCESSES(Type.INCREMENTOR, "CaptchaSuccessess"),
    CAPTCHA_FAILURES(Type.INCREMENTOR, "CaptchaFailures"),
    LDAP_UNAVAILABLE_COUNT(Type.INCREMENTOR, "LdapUnavailableCount"),
    SETUP_RESPONSES(Type.INCREMENTOR, "SetupResponses"),
    UPDATE_ATTRIBUTES(Type.INCREMENTOR, "UpdateAttributes"),
    SHORTCUTS_SELECTED(Type.INCREMENTOR, "ShortcutsSelected"),

    AVG_PASSWORD_SYNC_TIME(Type.AVERAGE, "AvgPasswordSyncTime"),
    AVG_WORDLIST_CHECK_TIME(Type.AVERAGE, "AvgWordlistCheckTime");

    private final Type type;
    private final String key;

    Statistic(final Type type, final String key) {
        this.type = type;
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public Type getType() {
        return type;
    }

    public static SortedSet<Statistic> sortedValues(final Locale locale) {
        final Comparator<Statistic> comparator = new Comparator<Statistic>() {
            public int compare(final Statistic o1, final Statistic o2) {
                return o1.getLabel(locale).compareTo(o2.getLabel(locale));
            }
        };
        final TreeSet<Statistic> set = new TreeSet<Statistic>(comparator);
        set.addAll(Arrays.asList(values()));
        return set;
    }

    public enum Type {
        INCREMENTOR,
        AVERAGE,
    }

    public String getLabel(final Locale locale) {
        return readProps(this.getKey(), locale);
    }

    private static String readProps(final String key, final Locale locale) {
        try {
            final ResourceBundle bundle = ResourceBundle.getBundle(Statistic.class.getName(), locale);
            return bundle.getString(key);
        } catch (Exception e) {
            return "--RESOURCE MISSING--";
        }
    }

}
