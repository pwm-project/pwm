package password.pwm.http.servlet.peoplesearch;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import password.pwm.PwmConstants;

@WebServlet(
    name="PeopleSearchResourcesServlet",
    urlPatterns = {
        PwmConstants.URL_PREFIX_PRIVATE + "/peoplesearch/fonts/*",
        PwmConstants.URL_PREFIX_PUBLIC + "/peoplesearch/fonts/*"
    }
)
public class PeopleSearchResourcesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.sendRedirect(String.format(
            // Build a relative URL with place holders:
            "%s%s/resources/app/fonts/%s",

            // Place holder values:
            request.getContextPath(),
            PwmConstants.URL_PREFIX_PUBLIC,
            StringUtils.substringAfterLast(request.getRequestURI(), "/")
        ));
    }
}
