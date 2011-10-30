package password.pwm.ws.server.rest;

import com.google.gson.Gson;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.*;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthMonitor;
import password.pwm.health.HealthRecord;
import password.pwm.util.PwmLogger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/health")
public class RestHealthServer {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestHealthServer.class);

    @Context
    HttpServletRequest request;

	// This method is called if TEXT_PLAIN is request
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public String sayPlainTextHello() {
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            return pwmApplication.getHealthMonitor().getMostSevereHealthStatus().toString();
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /health rest service: " + e.getMessage());
        }
        return "";
	}

	// This method is called if HTML is request
    /*
	@GET
	@Produces(MediaType.TEXT_HTML)
	public String sayHtmlHello() {
		return "<html> " + "<title>" + "Hello" + "</title>"
				+ "<body><h1>" + "Hello Jersey" + request.getRequestURI() + "</body></h1>" + "</html> ";
	}
	*/

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String sayJsonHealth(@QueryParam("refreshImmediate") final String requestImmediateParam) {
        final boolean requestImmediate = StringHelper.convertStrToBoolean(requestImmediateParam);
        try {
            final PwmSession pwmSession = PwmSession.getPwmSession(request);
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
            return processGetHealthCheckData(pwmApplication, pwmSession, requestImmediate);
        } catch (Exception e) {
            LOGGER.error("unexpected error building json response for /health rest service: " + e.getMessage());
        }

        return "";
    }

    private static String processGetHealthCheckData(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final boolean refreshImmediate
    )
            throws ChaiUnavailableException, IOException, ServletException, PwmUnrecoverableException {
        final HealthMonitor healthMonitor = pwmApplication.getHealthMonitor();

        boolean doRefresh = false;
        if (refreshImmediate) {
            if (pwmApplication.getConfigMode() == PwmApplication.MODE.CONFIGURATION) {
                LOGGER.trace(pwmSession, "allowing configuration refresh (ConfigurationMode=CONFIGURATION)");
                doRefresh = true;
            } else {
                try {
                    doRefresh = Permission.checkPermission(Permission.PWMADMIN, pwmSession, pwmApplication);
                } catch (Exception e) {
                    LOGGER.warn(pwmSession, "error during authorization check: " + e.getMessage());
                }
            }
        }

        final Collection<HealthRecord> healthRecords = healthMonitor.getHealthRecords(doRefresh);
        final Map<String, Object> returnMap = new LinkedHashMap<String, Object>();
        returnMap.put("date", PwmConstants.PWM_STANDARD_DATE_FORMAT.format(healthMonitor.getLastHealthCheckDate()));
        returnMap.put("timestamp", healthMonitor.getLastHealthCheckDate().getTime());
        returnMap.put("overall", healthMonitor.getMostSevereHealthStatus().toString());
        returnMap.put("data", healthRecords);

        final Gson gson = new Gson();
        return gson.toJson(returnMap);
    }
}