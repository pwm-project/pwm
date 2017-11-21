package password.pwm.svc.pwnotify;

import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PasswordExpireNotificationService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(PasswordExpireNotificationService.class);

    private ScheduledExecutorService executorService;
    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private PasswordExpireNotificationEngine engine;

    @Override
    public STATUS status() {
        return status;
    }

    @Override
    public void init(final PwmApplication pwmApplication) throws PwmException {
        this.pwmApplication = pwmApplication;

        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.PW_EXPY_NOTIFY_ENABLE)) {
            status = STATUS.CLOSED;
            LOGGER.trace("will remain closed, pw notify feature is not enabled");
            return;
        }

        engine = new PasswordExpireNotificationEngine(pwmApplication);

        executorService = Executors.newSingleThreadScheduledExecutor(
                JavaHelper.makePwmThreadFactory(
                        JavaHelper.makeThreadName(pwmApplication,this.getClass()) + "-",
                        true
                ));

        executorService.schedule(new DailyJobRunning(), 24, TimeUnit.HOURS);
    }

    @Override
    public void close() {
        JavaHelper.closeAndWaitExecutor(executorService, new TimeDuration(5, TimeUnit.SECONDS));
    }

    @Override
    public List<HealthRecord> healthCheck() {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo() {
        return null;
    }

    class DailyJobRunning implements Runnable {
        @Override
        public void run() {

        }
    }


}
