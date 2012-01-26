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

import password.pwm.PwmSession;
import password.pwm.util.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import java.awt.*;
import java.util.Locale;

public class LocaleOrientationTag extends PwmAbstractTag {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(LocaleOrientationTag.class);

    private String locale;

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    // ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final Locale userLocale;
            if (locale != null && locale.length() > 1) {
                userLocale = new Locale(locale);
            } else {
                userLocale = PwmSession.getPwmSession(req).getSessionStateBean().getLocale();
            }

            if (userLocale != null) {
                final ComponentOrientation orient = ComponentOrientation.getOrientation(userLocale);

                final String outputText = orient != null && !orient.isLeftToRight() ? "rtl" : "ltr";
                pageContext.getOut().write(outputText);
            } else {
                pageContext.getOut().write("ltr");
            }

        } catch (Exception e) {
            LOGGER.error("error while executing jsp locale orientation tag: " + e.getMessage(), e);
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }
}
