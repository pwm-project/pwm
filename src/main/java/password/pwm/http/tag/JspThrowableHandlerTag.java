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
 *
 */

package password.pwm.http.tag;

import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Display;
import password.pwm.util.Helper;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.tagext.TagSupport;
import java.io.IOException;
import java.util.Locale;

public class JspThrowableHandlerTag extends TagSupport {
    private static final PwmLogger LOGGER = PwmLogger.forClass(JspThrowableHandlerTag.class);

    @Override
    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        if (pageContext.getErrorData() == null || pageContext.getErrorData().getThrowable() == null) {
            return EVAL_PAGE;
        }


        try {
            final Throwable jspThrowable = pageContext.getErrorData().getThrowable();
            final String exceptionStr = Helper.throwableToString(jspThrowable);
            final String errorHash = SecureEngine.hash(exceptionStr, PwmHashAlgorithm.SHA1);

            LOGGER.error("jsp error reference " + errorHash,jspThrowable);

            final String jspOutout = jspOutput(errorHash);
            pageContext.getOut().write(jspOutout);
        } catch (Exception e) {
            try {
                pageContext.getOut().write("");
            } catch (IOException e1) {
                /* ignore */
            }
            LOGGER.error("error during pwmFormIDTag output of pwmFormID: " + e.getMessage());
        }
        return EVAL_PAGE;
    }

    private String jspOutput(final String errorReference) {
        Locale userLocale = PwmConstants.DEFAULT_LOCALE;
        Configuration configuration = null;
        try {
            PwmRequest pwmRequest = PwmRequest.forRequest((HttpServletRequest)pageContext.getRequest(), (HttpServletResponse)pageContext.getResponse());
            userLocale = pwmRequest.getLocale();
            configuration = pwmRequest.getConfig();
        } catch (Exception e) {
            /* system isn't working enough, so default values will suffice */
        }
        final String[] strArgs = new String[] {errorReference};
        return LocaleHelper.getLocalizedMessage(userLocale, Display.Display_ErrorReference, configuration, strArgs);

    }
}
