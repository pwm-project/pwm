package password.pwm.http.state;

import password.pwm.PwmConstants;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.PwmSessionBean;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

class HttpSessionService implements SessionBeanProvider {

    private final static PwmLogger LOGGER = PwmLogger.forClass(HttpSessionService.class);

    @Override
    public <E extends PwmSessionBean> E getSessionBean(final PwmRequest pwmRequest, final Class<E> theClass) {
        final Map<Class<? extends PwmSessionBean>,PwmSessionBean> sessionBeans = getSessionBeanMap(pwmRequest);

        if (!sessionBeans.containsKey(theClass)) {
            try {
                final Object newBean = SessionBeanService.newBean(null, theClass);
                sessionBeans.put(theClass,(PwmSessionBean)newBean);
            } catch (Exception e) {
                LOGGER.error("unexpected error trying to instantiate bean class " + theClass.getName() + ": " + e.getMessage(),e);
            }

        }
        return (E)sessionBeans.get(theClass);
    }

    @Override
    public void clearSessionBean(PwmRequest pwmRequest, Class<? extends PwmSessionBean> userBeanClass) {
        final Map<Class<? extends PwmSessionBean>,PwmSessionBean> sessionBeans = getSessionBeanMap(pwmRequest);
        sessionBeans.remove(userBeanClass);
    }

    private Map<Class<? extends PwmSessionBean>,PwmSessionBean> getSessionBeanMap(final PwmRequest pwmRequest) {
        final String attributeName = "SessionBeans";
        final HttpSession httpSession = pwmRequest.getHttpServletRequest().getSession();
        Map<Class<? extends PwmSessionBean>,PwmSessionBean> sessionBeans = (Map<Class<? extends PwmSessionBean>,PwmSessionBean>)httpSession.getAttribute(PwmConstants.SESSION_ATTR_BEANS);
        if (sessionBeans == null) {
            sessionBeans = new HashMap<>();
            httpSession.setAttribute(attributeName, sessionBeans);
        }
        return sessionBeans;
    }
}
