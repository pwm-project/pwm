package password.pwm.http.state;

import password.pwm.PwmApplication;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.svc.PwmService;
import password.pwm.util.logging.PwmLogger;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionBeanService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.forClass(SessionBeanService.class);

    private SessionBeanProvider sessionCookieProvider = new CryptoRequestCookieService();
    private final SessionBeanProvider httpSessionProvider = new HttpSessionService();
    private final Map<Class<? extends PwmSessionBean>,PwmSessionBean> beanInstanceCache = new HashMap<>();

    @Override
    public STATUS status() {
        return STATUS.OPEN;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        final SessionBeanMode sessionBeanMode = pwmApplication.getConfig().readSettingAsEnum(PwmSetting.SECURITY_SESSION_BEAN_MODE, SessionBeanMode.class);
        if (sessionBeanMode != null) {
            switch (sessionBeanMode) {
                case SESSION:
                    sessionCookieProvider = new HttpSessionService();
                    break;

                case CRYPTCOOKIE:
                    sessionCookieProvider = new CryptoRequestCookieService();
                    break;

                default:
                    throw new IllegalStateException("unhandled session bean state: " + sessionBeanMode);
            }
        }
        LOGGER.trace("initialized " + sessionCookieProvider.getClass().getName() + " provider");
    }

    @Override
    public void close() {

    }

    @Override
    public List<HealthRecord> healthCheck() {
        return null;
    }

    @Override
    public ServiceInfo serviceInfo() {
        return null;
    }


    private boolean beanClassHasFlag(Class<? extends PwmSessionBean> theClass, PwmSessionBean.Flag flag) throws PwmUnrecoverableException {
        if (theClass == null) {
            return false;
        }
        if (!beanInstanceCache.containsKey(theClass)) {
            beanInstanceCache.put(theClass, newBean(null,theClass));
        }
        try {
            return theClass.newInstance().getFlags().contains(flag);
        } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    public <E extends PwmSessionBean> E getBean(PwmRequest pwmRequest, Class<E> theClass) throws PwmUnrecoverableException {
        if (beanClassHasFlag(theClass, PwmSessionBean.Flag.ProhibitCookieSession)) {
            return httpSessionProvider.getSessionBean(pwmRequest, theClass);
        }
        return sessionCookieProvider.getSessionBean(pwmRequest, theClass);
    }

    public void clearBean(final PwmRequest pwmRequest, final Class<? extends PwmSessionBean> theClass) throws PwmUnrecoverableException {
        if (beanClassHasFlag(theClass, PwmSessionBean.Flag.ProhibitCookieSession)) {
            httpSessionProvider.clearSessionBean(pwmRequest, theClass);
            return;
        }
        sessionCookieProvider.clearSessionBean(pwmRequest, theClass);
    }

    static <E extends PwmSessionBean> E newBean(final String sessionGuid, Class<E> theClass) throws PwmUnrecoverableException {
        try {
            final E newBean = theClass.newInstance();
            newBean.setGuid(sessionGuid);
            newBean.setTimestamp(new Date());
            return newBean;
        } catch (Exception e) {
            final String errorMsg = "unexpected error trying to instantiate bean class " + theClass.getName() + ": " + e.getMessage();
            LOGGER.error(errorMsg, e);
            throw PwmUnrecoverableException.newException(PwmError.ERROR_UNKNOWN, errorMsg);
        }
    }
}
