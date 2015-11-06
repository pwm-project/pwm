package password.pwm.http.filter;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.ErrorInformation;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class ApplicationStatusFilter implements Filter {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ApplicationStatusFilter.class);

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        ErrorInformation startupError = null;
        try {
            final ServletContext servletContext = servletRequest.getServletContext();
            final ContextManager contextManager = (ContextManager) servletContext.getAttribute(PwmConstants.CONTEXT_ATTR_CONTEXT_MANAGER);
            if (contextManager != null) {
                startupError = contextManager.getStartupErrorInformation();

                PwmApplication pwmApplication = contextManager.getPwmApplication();
                if (pwmApplication != null) {
                    filterChain.doFilter(servletRequest, servletResponse);
                    return;
                }
            }
        } catch (Exception e) {
            final PwmURL pwmURL = new PwmURL((HttpServletRequest)servletRequest);
            if (pwmURL.isResourceURL()) {
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }

            LOGGER.error("error while trying to detect application status: " + e.getMessage());
        }

        LOGGER.error("unable to satisfy incoming request, application is not available");
        servletRequest.setAttribute(PwmConstants.REQUEST_ATTR.PwmErrorInfo.toString(), startupError);
        ((HttpServletResponse) servletResponse).setStatus(500);
        final String url = PwmConstants.JSP_URL.APP_UNAVAILABLE.getPath();
        servletRequest.getServletContext().getRequestDispatcher(url).forward(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }
}
