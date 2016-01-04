package password.pwm.http.state;

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.bean.PwmSessionBean;

interface SessionBeanProvider {
    <E extends PwmSessionBean> E getSessionBean(PwmRequest pwmRequest, Class<E> theClass) throws PwmUnrecoverableException;

    void clearSessionBean(PwmRequest pwmRequest, final Class<? extends PwmSessionBean> userBeanClass) throws PwmUnrecoverableException;

}
