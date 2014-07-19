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

package password.pwm.http.tag;

import password.pwm.PwmApplication;
import password.pwm.bean.SessionStateBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmSession;
import password.pwm.http.filter.SessionFilter;
import password.pwm.http.servlet.ResourceFileServlet;

import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;

public class URLRewriter extends PwmAbstractTag {

    private String url;
    private boolean addContext;

    public static final String THEME_URL = "%THEME_URL%";
    public static final String MOBILE_THEME_URL = "%MOBILE_THEME_URL%";

    private static final String RESOURCE_URL = "/resources";
// --------------------- GETTER / SETTER METHODS ---------------------

    public void setUrl(final String url)
    {
        this.url = url;
    }

    public void setAddContext(boolean addContext) {
        this.addContext = addContext;
    }

// ------------------------ INTERFACE METHODS ------------------------


// --------------------- Interface Tag ---------------------

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        try {
            final PwmApplication pwmApplication = ContextManager.getPwmApplication(pageContext.getSession());
            final PwmSession pwmSession = PwmSession.getPwmSession(pageContext.getSession());
            String workingUrl;
            if (THEME_URL.equals(url)) {
                workingUrl = figureThemeURL(pwmApplication, pwmSession, false);
                workingUrl = insertContext(pageContext, workingUrl);
            } else if (MOBILE_THEME_URL.equals(url)) {
                workingUrl = figureThemeURL(pwmApplication, pwmSession, true);
                workingUrl = insertContext(pageContext, workingUrl);
            } else {
                workingUrl = url;
            }

            workingUrl = SessionFilter.rewriteURL(workingUrl, pageContext.getRequest(), pageContext.getResponse());
            if (addContext) {
                workingUrl = insertContext(pageContext, workingUrl);
            }
            workingUrl = insertResourceNonce(pwmApplication, workingUrl);
            pageContext.getOut().write(workingUrl);
        } catch (PwmUnrecoverableException e) {
            /* application unavailable */
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }
        return EVAL_PAGE;
    }

    private static String insertContext(final PageContext pageContext, final String urlString) {
        final String contextPath = pageContext.getServletContext().getContextPath();
        if (!urlString.startsWith("/")) {
            return urlString;
        }

        if (urlString.startsWith(contextPath)) {
            return urlString;
        }

        return contextPath + urlString;

    }

    private static String insertResourceNonce(final PwmApplication pwmApplication, final String urlString) {
        if (urlString.contains(RESOURCE_URL)) {
            final String nonce = ResourceFileServlet.makeResourcePathNonce(pwmApplication);
            if (nonce != null && nonce.length() > 0) {
                return urlString.replaceFirst(RESOURCE_URL, RESOURCE_URL + nonce);
            }

        }
        return urlString;
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
            final boolean mobile
    )
    {
        final String themeName = figureThemeName(pwmApplication, pwmSession);

        String themeURL = null;
        if (themeName.equals("custom")) {
            if (mobile) {
                themeURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.DISPLAY_CSS_CUSTOM_MOBILE_STYLE);
            } else {
                themeURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.DISPLAY_CSS_CUSTOM_STYLE);
            }
        }

        if (themeURL == null || themeURL.length() < 1) {
            if (mobile) {
                themeURL = "/public/resources/themes/" + themeName + "/mobileStyle.css";
            } else {
                themeURL = "/public/resources/themes/" + themeName + "/style.css";
            }
        }

        return themeURL;
    }

}
