package password.pwm.util.stats;

import java.util.*;

public enum Statistic {
    PWM_AUTHENTICATIONS(Type.INCREMENTOR,"Authentications"),
    PWM_STARTUPS(Type.INCREMENTOR, "PWM Startups"),
    PASSWORD_CHANGES(Type.INCREMENTOR, "Password Changes"),
    FAILED_LOGIN_ATTEMPTS(Type.INCREMENTOR, "Authentication Failures"),
    RECOVERY_SUCCESSES(Type.INCREMENTOR, "Recovery Successes"),
    RECOVERY_ATTEMPTS(Type.INCREMENTOR, "Recovery Attempts"),
    EMAIL_SEND_SUCCESSES(Type.INCREMENTOR, "Email Send Successes"),
    EMAIL_SEND_FAILURES(Type.INCREMENTOR, "Email Send Failures"),
    PASSWORD_RULE_CHECKS(Type.INCREMENTOR, "Password Rule Checks"),
    HTTP_REQUESTS(Type.INCREMENTOR, "HTTP Requests"),
    ACTIVATED_USERS(Type.INCREMENTOR, "Activated Users"),
    NEW_USERS(Type.INCREMENTOR, "New Users"),
    LOCKED_USERS(Type.INCREMENTOR, "Locked Users"),
    LOCKED_ADDRESSES(Type.INCREMENTOR, "Locked Addresses"),
    CAPTCHA_SUCCESSES(Type.INCREMENTOR, "Captcha Successes"),
    CAPTCHA_FAILURES(Type.INCREMENTOR, "Captcha Failures"),
    LDAP_UNAVAILABLE_COUNT(Type.INCREMENTOR, "LDAP Unavailable Count"),
    HTTP_SESSIONS(Type.INCREMENTOR, "HTTP Sessions"),

    AVG_PASSWORD_SYNC_TIME(Type.AVERAGE, "Average Password Sync Time"),
    AVG_WORDLIST_CHECK_TIME(Type.AVERAGE, "Average Wordlist Check Time");

    private final Type type;
    private final String label;

    Statistic(final Type type, final String label) {
        this.type = type;
        this.label = label;
    }

    public String getLabel(final Locale locale) {
        return label;
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
}
