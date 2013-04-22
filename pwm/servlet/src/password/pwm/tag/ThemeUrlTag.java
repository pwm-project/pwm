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
import password.pwm.PwmSession;
import password.pwm.SessionFilter;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.servlet.ResourceFileServlet;
import password.pwm.util.PwmLogger;

import javax.servlet.ServletContext;
import javax.servlet.jsp.JspTagException;

public class ThemeUrlTag extends PwmAbstractTag {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(ThemeUrlTag.class);
    private String type;

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    // ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException {
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(pageContext.getSession());
            final PwmSession pwmSession = PwmSession.getPwmSession(pageContext.getSession());
            final String themeURL = figureThemeURL(pwmApplication, pwmSession, pageContext.getServletContext(), type);
            pageContext.getOut().write(SessionFilter.rewriteURL(themeURL, pageContext.getRequest(), pageContext.getResponse()));
        } catch (PwmUnrecoverableException e) {
            /* pwm app unavailable */
        } catch (Exception e) {
            LOGGER.error("error while executing theme name tag: " + e.getMessage(), e);
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }

    private static String figureThemeName(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
    {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        if (ssBean.getTheme() != null) {
            return ssBean.getTheme();
        }

        if (pwmApplication != null && pwmApplication.getConfig() != null) {
            return pwmApplication.getConfig().readSettingAsString(PwmSetting.INTERFACE_THEME);
        } else {
            return "default";
        }
    }

    private static String figureThemeURL(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ServletContext servletContext,
            final String type
    )
    {
        final String themeName = figureThemeName(pwmApplication, pwmSession);

        final boolean isMobile = type != null && "mobile".equalsIgnoreCase(type);

        final String themeURL;
        if (themeName.equals("custom")) {
            final String configuredURL;
            if (isMobile) {
                configuredURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.DISPLAY_CSS_CUSTOM_MOBILE_STYLE);
            } else {
                configuredURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.DISPLAY_CSS_CUSTOM_STYLE);
            }

            if (configuredURL.startsWith("/")) {
                themeURL = servletContext.getContextPath() + configuredURL;
            } else {
                themeURL = configuredURL;
            }
        } else {
            final String nonce = ResourceFileServlet.makeResourcePathNonce(pwmApplication);
            if (isMobile) {
                themeURL = servletContext.getContextPath() + "/public/resources" + nonce + "/themes/" + themeName + "/mobileStyle.css";
            } else {
                themeURL = servletContext.getContextPath() + "/public/resources" + nonce + "/themes/" + themeName + "/style.css";
            }
        }

        return themeURL;
    }
}

