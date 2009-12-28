/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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
import password.pwm.PwmSession;
import password.pwm.config.LocalizedConfiguration;
import password.pwm.config.Message;
import password.pwm.util.PwmLogger;

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

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getKey()
    {
        return key;
    }

    public void setKey(final String key)
    {
        this.key = key;
    }

    public String getValue1()
    {
        return value1;
    }

    public void setValue1(final String value1)
    {
        this.value1 = value1;
    }

    public String getValue2()
    {
        return value2;
    }

    public void setValue2(final String value1)
    {
        this.value2 = value1;
    }

    public boolean isDisplayIfMissing() {
        return displayIfMissing;
    }

    public void setDisplayIfMissing(boolean displayIfMissing) {
        this.displayIfMissing = displayIfMissing;
    }

    // ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        try {
            final HttpServletRequest req = (HttpServletRequest) pageContext.getRequest();
            final Locale locale = PwmSession.getSessionStateBean(req.getSession()).getLocale();

            String displayMessage = "PWM Default Configuration";

            if ("APPLICATION-TITLE".equals(key)) { // special case, this one value is set via configuration, net .properties files setting
                final ContextManager contextManager = PwmSession.getPwmSession(req).getContextManager();
                if (contextManager != null) {
                    final LocalizedConfiguration localizedConfiguration = contextManager.getLocaleConfig(locale);
                    if (localizedConfiguration != null) {
                        final String pwmSettingValue = localizedConfiguration.getApplicationTitle();
                        if (pwmSettingValue != null && pwmSettingValue.length() > 0) {
                            displayMessage = pwmSettingValue;
                        }
                    }
                }
            } else {
                displayMessage = figureDisplayMessage(locale);
            }

            pageContext.getOut().write(displayMessage);
        } catch (Exception e) {
            LOGGER.debug("error while executing jsp display tag: " + e.getMessage(), e);
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }

    private String figureDisplayMessage(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }
        try {
            String displayMessage = Message.getDisplayString(key, locale);

            if (displayMessage != null) {
                if (value1 != null && value1.length() > 0) {
                    displayMessage = displayMessage.replaceAll("%1%", value1);
                }

                if (value2 != null && value2.length() > 0) {
                    displayMessage = displayMessage.replaceAll("%2%", value2);
                }

                return displayMessage;
            } else {
                if (!displayIfMissing) {
                    LOGGER.info("no value for: " + key);
                }
            }
        } catch (MissingResourceException e) {
            if (!displayIfMissing) {
                LOGGER.info("error while executing jsp display tag: " + e.getMessage());
            }
        }

        return displayIfMissing ? key : "";
    }
}

