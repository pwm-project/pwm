package password.pwm.svc.stats;

import password.pwm.i18n.Admin;
import password.pwm.util.LocaleHelper;

import java.util.Locale;

public enum EpsStatistic {
    REQUESTS(null),
    SESSIONS(null),
    PASSWORD_CHANGES(Statistic.PASSWORD_CHANGES),
    AUTHENTICATION(Statistic.AUTHENTICATIONS),
    INTRUDER_ATTEMPTS(Statistic.INTRUDER_ATTEMPTS),
    PWMDB_WRITES(null),
    PWMDB_READS(null),
    DB_WRITES(null),
    DB_READS(null),
    ;

    private Statistic relatedStatistic;

    EpsStatistic(final Statistic relatedStatistic) {
        this.relatedStatistic = relatedStatistic;
    }

    public Statistic getRelatedStatistic() {
        return relatedStatistic;
    }

    public String getLabel(final Locale locale) {
        final String keyName = Admin.EPS_STATISTICS_LABEL_PREFIX + this.name();
        return LocaleHelper.getLocalizedMessage(locale, keyName, null, Admin.class);
    }
}
