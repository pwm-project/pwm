/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.http.tag.url;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.LocalSessionStateBean;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestFlag;
import password.pwm.http.servlet.resource.ResourceFileServlet;
import password.pwm.http.tag.PwmAbstractTag;
import password.pwm.util.StringUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspTagException;
import javax.servlet.jsp.PageContext;

public class PwmUrlTag extends PwmAbstractTag {

    private String url;
    private boolean addContext;

    private static final String RESOURCE_URL = "/resources";

    public void setUrl(final String url)
    {
        this.url = url;
    }

    public void setAddContext(boolean addContext) {
        this.addContext = addContext;
    }

    public int doEndTag()
            throws javax.servlet.jsp.JspTagException
    {
        String outputURL = url;
        PwmRequest pwmRequest = null;
        try {
            pwmRequest = PwmRequest.forRequest((HttpServletRequest)pageContext.getRequest(), (HttpServletResponse)pageContext.getResponse());
        } catch (PwmException e) { /* noop */ }

        String workingUrl = url;

        for (final PwmThemeURL theme_url : PwmThemeURL.values()) {
            if (theme_url.token().equals(url)) {
                workingUrl = figureThemeURL(pwmRequest, theme_url);
                workingUrl = insertContext(pageContext, workingUrl);
            }
        }

        if (addContext) {
            workingUrl = insertContext(pageContext, workingUrl);
        }
        if (pwmRequest != null) {
            workingUrl = insertResourceNonce(pwmRequest.getPwmApplication(), workingUrl);
        }

        outputURL = workingUrl;

        try {
            pageContext.getOut().write(outputURL);
        } catch (Exception e) {
            throw new JspTagException(e.getMessage());
        }

        return EVAL_PAGE;
    }


    public static String insertContext(final PageContext pageContext, final String urlString) {
        final String contextPath = pageContext.getServletContext().getContextPath();
        if (!urlString.startsWith("/")) {
            return urlString;
        }

        if (
                urlString.toLowerCase().startsWith("http://")
                        || urlString.toLowerCase().startsWith("https://")
                        || urlString.startsWith("//")
                )
        {
            return urlString;
        }

        if (urlString.startsWith(contextPath)) {
            return urlString;
        }

        return contextPath + urlString;

    }

    public static String insertResourceNonce(final PwmApplication pwmApplication, final String urlString) {
        if (pwmApplication != null && urlString.contains(RESOURCE_URL)) {
            final String nonce = pwmApplication.getResourceServletService().getResourceNonce();
            if (nonce != null && nonce.length() > 0) {
                return urlString.replaceFirst(RESOURCE_URL, RESOURCE_URL + nonce);
            }

        }
        return urlString;
    }

    private static String figureThemeName(
            final PwmRequest pwmRequest
    )
    {
        if (pwmRequest.isFlag(PwmRequestFlag.INCLUDE_CONFIG_CSS)) {
            return pwmRequest.getConfig().readAppProperty(AppProperty.CONFIG_THEME);
        }

        final LocalSessionStateBean ssBean = pwmRequest.getPwmSession().getSessionStateBean();
        if (ssBean.getTheme() != null) {
            return ssBean.getTheme();
        }

        if (pwmRequest.getConfig() != null) {
            return pwmRequest.getConfig().readSettingAsString(PwmSetting.INTERFACE_THEME);
        } else {
            return "default";
        }
    }

    private static String figureThemeURL(
            final PwmRequest pwmRequest,
            final PwmThemeURL theme_url
    )
    {
        String themeURL = null;
        String themeName = AppProperty.CONFIG_THEME.getDefaultValue();

        if (pwmRequest != null) {
            final PwmApplication pwmApplication = pwmRequest.getPwmApplication();

            themeName = figureThemeName(pwmRequest);

            if (themeName.equals("custom")) {
                if (theme_url == PwmThemeURL.MOBILE_THEME_URL) {
                    themeURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.DISPLAY_CSS_CUSTOM_MOBILE_STYLE);
                } else {
                    themeURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.DISPLAY_CSS_CUSTOM_STYLE);
                }
            }
        }

        if (themeURL == null || themeURL.length() < 1) {
            themeURL = ResourceFileServlet.RESOURCE_PATH + theme_url.getCssName();
            themeURL = themeURL.replace(ResourceFileServlet.TOKEN_THEME, StringUtil.escapeHtml(themeName));
        }

        return themeURL;
    }

}
