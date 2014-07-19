/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.http;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.Validator;
import password.pwm.bean.SessionLabel;
import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;
import password.pwm.util.ServletHelper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.Locale;

public class PwmRequest implements Serializable {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(PwmRequest.class);

    private final HttpServletRequest httpServletRequest;
    private final HttpServletResponse httpServletResponse;
    private final PwmApplication pwmApplication;
    private final PwmSession pwmSession;

    public static PwmRequest forRequest(
            HttpServletRequest request,
            HttpServletResponse response
    )
            throws PwmUnrecoverableException
    {
        PwmRequest pwmRequest = (PwmRequest)request.getAttribute(PwmConstants.REQUEST_ATTR_PWM_REQUEST);
        if (pwmRequest == null) {
            pwmRequest = new PwmRequest(request, response);
            request.setAttribute(PwmConstants.REQUEST_ATTR_PWM_REQUEST,pwmRequest);
        }
        return pwmRequest;
    }

	private PwmRequest(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse)
            throws PwmUnrecoverableException
    {
		this.httpServletRequest = httpServletRequest;
        this.httpServletResponse = httpServletResponse;
        pwmSession = PwmSession.getPwmSession(httpServletRequest);
        pwmApplication = ContextManager.getPwmApplication(httpServletRequest);
    }

    public PwmApplication getPwmApplication()
    {
        return pwmApplication;
    }

    public PwmSession getPwmSession()
    {
        return pwmSession;
    }

    public SessionLabel getSessionLabel() {
        return pwmSession.getSessionLabel();
    }

    public HttpServletRequest getHttpServletRequest()
    {
        return httpServletRequest;
    }

    public HttpServletResponse getHttpServletResponse()
    {
        return httpServletResponse;
    }

    public Locale getLocale() {
        return pwmSession.getSessionStateBean().getLocale();
    }

    public Configuration getConfig() {
        return pwmApplication.getConfig();
    }

    public String readStringParameter(final String name)
            throws PwmUnrecoverableException
    {
        return Validator.readStringFromRequest(this.getHttpServletRequest(), name);
    }

    public void forwardToJsp(final PwmConstants.JSP_URL jspURL)
            throws ServletException, IOException, PwmUnrecoverableException
    {
        ServletHelper.forwardToJsp(this.getHttpServletRequest(), this.getHttpServletResponse(), jspURL);
    }

    public void forwardToErrorPage()
            throws IOException, ServletException
    {
        forwardToErrorPage(true);
    }

    public void forwardToErrorPage(
            final boolean forceLogout
    )
            throws IOException, ServletException {
        try {
           forwardToJsp(PwmConstants.JSP_URL.ERROR);
            if (forceLogout) {
                pwmSession.unauthenticateUser();
            }
        } catch (PwmUnrecoverableException e) {
            LOGGER.error("unexpected error sending user to error page: " + e.toString());
        }
    }

    public String readRequestBody()
            throws IOException, PwmUnrecoverableException
    {
        final int maxChars = Integer.parseInt(this.getConfig().readAppProperty(AppProperty.HTTP_BODY_MAXREAD_LENGTH));
        return readRequestBody(maxChars);
    }

    public String readRequestBody(final int maxChars) throws IOException {
        final StringBuilder inputData = new StringBuilder();
        String line;
        try {
            final BufferedReader reader = this.getHttpServletRequest().getReader();
            while (((line = reader.readLine()) != null) && inputData.length() < maxChars) {
                inputData.append(line);
            }
        } catch (Exception e) {
            LOGGER.error("error reading request body stream: " + e.getMessage());
        }
        return inputData.toString();
    }

}
