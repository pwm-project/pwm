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

import password.pwm.ContextManager;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.MacroMachine;
import password.pwm.util.PwmLogger;
import password.pwm.util.operations.UserDataReader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.jsp.JspTagException;
import java.util.Locale;
import java.util.MissingResourceException;

/**
 * @author Jason D. Rivard
 */
public class DisplayTag extends PwmAbstractTag {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(DisplayTag.class);

    private String key;
    private String value1;
    private String value2;
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
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final Locale locale = PwmSession.getPwmSession(req).getSessionStateBean().getLocale();
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(req);
            final PwmSession pwmSession = PwmSession.getPwmSession(req);
            final UserDataReader userDataReader;
            if (pwmSession.getSessionStateBean().isAuthenticated()) {
                userDataReader = pwmSession.getSessionManager().getUserDataReader();
            } else {
                userDataReader = null;
            }
            final UserInfoBean uiBean = PwmSession.getPwmSession(req).getUserInfoBean();

            final Class bundle = readBundle();
            final String displayMessage = figureDisplayMessage(locale, pwmApplication.getConfig(), bundle);
            final String expandedMessage = MacroMachine.expandMacros(displayMessage, pwmApplication, uiBean, userDataReader);

            pageContext.getOut().write(expandedMessage);
        } catch (PwmUnrecoverableException e) { {
            LOGGER.debug("error while executing jsp display tag: " + e.getMessage(), e);
            return EVAL_PAGE;
        }
        } catch (Exception e) {
            LOGGER.debug("error while executing jsp display tag: " + e.getMessage(), e);
            throw new JspTagException(e.getMessage());
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
            return LocaleHelper.getLocalizedMessage(locale, key, config, bundleClass, new String[]{value1, value2});
        } catch (MissingResourceException e) {
            if (!displayIfMissing) {
                LOGGER.info("error while executing jsp display tag: " + e.getMessage());
            }
        }

        return displayIfMissing ? key : "";
    }
}

