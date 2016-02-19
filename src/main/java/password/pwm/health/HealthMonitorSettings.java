package password.pwm.health;

import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.util.TimeDuration;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

class HealthMonitorSettings implements Serializable {
    private TimeDuration nominalCheckInterval = new TimeDuration(1, TimeUnit.MINUTES);
    private TimeDuration minimumCheckInterval = new TimeDuration(30, TimeUnit.SECONDS);
    private TimeDuration maximumRecordAge = new TimeDuration(5, TimeUnit.MINUTES);
    private TimeDuration maximumForceCheckWait = new TimeDuration(30, TimeUnit.SECONDS);

    public TimeDuration getMaximumRecordAge() {
        return maximumRecordAge;
    }

    public TimeDuration getMinimumCheckInterval() {
        return minimumCheckInterval;
    }

    public TimeDuration getNominalCheckInterval() {
        return nominalCheckInterval;
    }

    public TimeDuration getMaximumForceCheckWait() {
        return maximumForceCheckWait;
    }

    public static HealthMonitorSettings fromConfiguration(final Configuration config) {
        final HealthMonitorSettings settings = new HealthMonitorSettings();
        settings.nominalCheckInterval = new TimeDuration(Long.parseLong(config.readAppProperty(AppProperty.HEALTHCHECK_NOMINAL_CHECK_INTERVAL)), TimeUnit.SECONDS);
        settings.minimumCheckInterval = new TimeDuration(Long.parseLong(config.readAppProperty(AppProperty.HEALTHCHECK_MIN_CHECK_INTERVAL)), TimeUnit.SECONDS);
        settings.maximumRecordAge = new TimeDuration(Long.parseLong(config.readAppProperty(AppProperty.HEALTHCHECK_MAX_RECORD_AGE)), TimeUnit.SECONDS);
        settings.maximumForceCheckWait = new TimeDuration(Long.parseLong(config.readAppProperty(AppProperty.HEALTHCHECK_MAX_FORCE_WAIT)), TimeUnit.SECONDS);
        return settings;
    }
}
