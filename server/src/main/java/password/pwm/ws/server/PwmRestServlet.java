package password.pwm.ws.server;

import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.PwmConstants;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Set;

public abstract class PwmRestServlet extends HttpServlet{

    protected void service(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException
    {

        try {
            resp.setHeader(HttpHeader.Content_Type.getHttpName(), PwmConstants.ContentTypeValue.json.toString());
            resp.setHeader(HttpHeader.Server.getHttpName(), PwmConstants.PWM_APP_NAME);
            checkMethods(req);
            final StandaloneRestRequestBean restRequestBean = StandaloneRestHelper.initialize(req);
            final RestResultBean restResultBean = invokeWebService(restRequestBean);
            try (PrintWriter pw = resp.getWriter()) {
                pw.write(restResultBean.toJson());
            }
        } catch (PwmUnrecoverableException e) {
            final RestResultBean restResultBean = RestResultBean.fromError(e.getErrorInformation());
            try (PrintWriter pw = resp.getWriter()) {
                pw.write(restResultBean.toJson());
            }
            resp.sendError(500, e.getMessage());
        } catch (Throwable e) {
            resp.sendError(500, e.getMessage());
        }
    }

    private void checkMethods(final HttpServletRequest req)
            throws PwmUnrecoverableException
    {
        final Set<HttpMethod> methods = getServiceInfo().getMethods();
        final HttpMethod methodUsed = HttpMethod.fromString(req.getMethod());
        if (!methods.contains(methodUsed)) {
            throw new PwmUnrecoverableException(PwmError.ERROR_REST_INVOCATION_ERROR, "method not supported");
        }
    }

    public abstract RestResultBean invokeWebService(StandaloneRestRequestBean standaloneRestRequestBean);

    public abstract ServiceInfo getServiceInfo();

    @Getter
    @AllArgsConstructor
    public static class ServiceInfo {
        private Set<HttpMethod> methods;
    }
}
