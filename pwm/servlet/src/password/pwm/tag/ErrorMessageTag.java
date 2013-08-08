/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

package password.pwm.tag;

import org.apache.commons.lang.StringEscapeUtils;
import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;

/**
 * @author Jason D. Rivard
 */
public class ErrorMessageTag extends PwmAbstractTag {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ErrorMessageTag.class);

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();


        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final ErrorInformation error = pwmSession.getSessionStateBean().getSessionError();

            if (error != null) {

                boolean showErrorDetail = true;
                if (pwmApplication.getApplicationMode() == PwmApplication.MODE.RUNNING) {
                    if (pwmApplication.getConfig() != null) {
                        if (!pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_SHOW_DETAILED_ERRORS)) {
                            showErrorDetail = false;
                        }
                    }
                }

                String errorMsg;
                if (showErrorDetail) {
                    final String errorDetail = error.toDebugStr() == null ? "" : " { " + error.toDebugStr() + " }";
                    errorMsg = error.toUserStr(pwmSession, pwmApplication) + errorDetail;
                }  else {
                    errorMsg = error.toUserStr(pwmSession, pwmApplication);
                }
                if (!PwmConstants.ALLOW_HTML_IN_ERROR_MESSAGES) {
                    errorMsg = StringEscapeUtils.escapeHtml(errorMsg);
                }
                pageContext.getOut().write(errorMsg);
            }
        } catch (PwmUnrecoverableException e) {
            /* app not running */
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}