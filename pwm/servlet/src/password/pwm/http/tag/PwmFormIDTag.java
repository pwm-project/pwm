/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.tag;

import password.pwm.bean.SessionStateBean;
import password.pwm.http.JspUtility;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.util.Helper;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;

public class PwmFormIDTag extends TagSupport {
// --------------------- Interface Tag ---------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(PwmFormIDTag.class);

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        try {
            final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext);
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final SessionStateBean ssBean = pwmSession.getSessionStateBean();
            final String pwmFormID = Helper.buildPwmFormID(ssBean);

            pageContext.getOut().write(pwmFormID);
        } catch (Exception e) {
            try {
                pageContext.getOut().write("errorGeneratingPwmFormID");
            } catch (IOException e1) {
                /* ignore */
            }
            LOGGER.error("error during pwmFormIDTag output of pwmFormID: " + e.getMessage());
        }
        return EVAL_PAGE;
    }

}
