package password.pwm;

import password.pwm.http.ContextManager;

import javax.servlet.http.HttpServletRequest;

public enum PwmApplicationMode {
    NEW,
    CONFIGURATION,
    RUNNING,
    READ_ONLY,
    ERROR;

    public static PwmApplicationMode determineMode(final HttpServletRequest httpServletRequest) {
        final ContextManager contextManager;
        try {
            contextManager = ContextManager.getContextManager(httpServletRequest.getServletContext());
        } catch (Throwable t) {
            return ERROR;
        }

        final PwmApplication pwmApplication;
        try {
            pwmApplication = contextManager.getPwmApplication();
        } catch (Throwable t) {
            return ERROR;
        }

        return pwmApplication.getApplicationMode();
    }
}
