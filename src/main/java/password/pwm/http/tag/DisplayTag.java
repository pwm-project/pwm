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

import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Display;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import java.util.Locale;
import java.util.MissingResourceException;

/**
 * @author Jason D. Rivard
 */
public class DisplayTag extends PwmAbstractTag {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.forClass(DisplayTag.class);

    private String key;
    private String value1;
    private String value2;
    private String value3;
    private boolean displayIfMissing;
    private String bundle;

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getKey() {
        return key;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    public String getValue1() {
        return value1;
    }

    public void setValue1(final String value1) {
        this.value1 = value1;
    }

    public String getValue2() {
        return value2;
    }

    public void setValue2(final String value1) {
        this.value2 = value1;
    }

    public String getValue3()
    {
        return value3;
    }

    public void setValue3(String value3)
    {
        this.value3 = value3;
    }

    public boolean isDisplayIfMissing() {
        return displayIfMissing;
    }

    public void setDisplayIfMissing(boolean displayIfMissing) {
        this.displayIfMissing = displayIfMissing;
    }

    public String getBundle() {
        return bundle;
    }

    public void setBundle(String bundle) {
        this.bundle = bundle;
    }

    // ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException {
        try {
            PwmRequest pwmRequest = null;
            try {
                pwmRequest = PwmRequest.forRequest((HttpServletRequest) pageContext.getRequest(), (HttpServletResponse) pageContext.getResponse());
            } catch (PwmException e) { /* noop */ }
            
            final Locale locale = pwmRequest == null ? PwmConstants.DEFAULT_LOCALE : pwmRequest.getLocale();

            final Class bundle = readBundle();
            String displayMessage = figureDisplayMessage(locale, pwmRequest == null ? null : pwmRequest.getConfig(), bundle);

            if (pwmRequest != null) {
                final MacroMachine macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine(pwmRequest.getPwmApplication());
                displayMessage = macroMachine.expandMacros(displayMessage);
            }

            pageContext.getOut().write(displayMessage);
        } catch (PwmUnrecoverableException e) { {
            LOGGER.debug("error while executing jsp display tag: " + e.getMessage());
            return EVAL_PAGE;
        }
        } catch (Exception e) {
            LOGGER.debug("error while executing jsp display tag: " + e.getMessage(),e);
            throw new JspTagException(e.getMessage(),e);
        }
        return EVAL_PAGE;
    }

    private Class readBundle() {
        if (bundle == null || bundle.length() < 1) {
            return Display.class;
        }

        try {
            return Class.forName(bundle);
        } catch (ClassNotFoundException e) { /* no op */ }

        try {
            return Class.forName(Display.class.getPackage().getName() + "." + bundle);
        } catch (ClassNotFoundException e) { /* no op */ }

        return Display.class;
    }

    private String figureDisplayMessage(Locale locale, final Configuration config, final Class bundleClass) {
        if (locale == null) {
            locale = PwmConstants.DEFAULT_LOCALE;
        }
        try {
            return LocaleHelper.getLocalizedMessage(locale, key, config, bundleClass, new String[]{value1, value2, value3});
        } catch (MissingResourceException e) {
            if (!displayIfMissing) {
                LOGGER.info("error while executing jsp display tag: " + e.getMessage());
            }
        }

        return displayIfMissing ? key : "";
    }
}

