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

package password.pwm.ws.server.rest;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmSession;
import password.pwm.SessionFilter;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.i18n.Message;
import password.pwm.util.Helper;
import password.pwm.util.MacroMachine;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.*;

@Path("/app-data")
public class RestAppDataServer {

    public static class AppData implements Serializable {
        public Map<String,String> PWM_STRINGS;
        public Map<String,Object> PWM_GLOBAL;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        return RestServerHelper.doHtmlRedirect();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetAppData(
            @Context HttpServletRequest request,
            @Context HttpServletResponse response
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException {
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, false, null);
        } catch (PwmUnrecoverableException e) {
            return Response.ok(RestServerHelper.outputJsonErrorResult(e.getErrorInformation(),request)).build();
        }

        final String eTagValue = Helper.makePwmVariableJsNonce(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession());

        // check the incoming header;
        /*
        final String ifNoneMatchValue = request.getHeader("If-None-Match");

        if (ifNoneMatchValue != null && ifNoneMatchValue.equals(eTagValue)) {
            return Response.notModified().build();
        }
        response.setHeader("ETag",eTagValue);
        response.setHeader("Cache-Control","private");
        */
        response.setHeader("Cache-Control","private, max-age=3600");

        final AppData appData = makeAppData(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), request, response);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(appData);
        return Response.ok(restResultBean.toJson()).build();
    }

    private AppData makeAppData(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final AppData appData = new AppData();
        appData.PWM_STRINGS = makeDisplayData(pwmApplication, pwmSession);
        appData.PWM_GLOBAL = makeSettingData(pwmApplication, pwmSession, request, response);
        return appData;
    }

    private Map<String,String> makeDisplayData(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    )
    {
        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmApplication.getConfig();
        final TreeMap<String,String> displayStrings = new TreeMap<String, String>();
        final ResourceBundle bundle = ResourceBundle.getBundle(Display.class.getName());
        for (final String key : new TreeSet<String>(Collections.list(bundle.getKeys()))) {
            displayStrings.put(key, Display.getLocalizedMessage(userLocale, key, config));
        }
        displayStrings.put(Message.SUCCESS_UNKNOWN.toString(), Message.getLocalizedMessage(userLocale, Message.SUCCESS_UNKNOWN, config));
        return displayStrings;
    }

    private static Map<String,Object> makeSettingData(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final TreeMap<String,Object> settingMap = new TreeMap<String, Object>();
        settingMap.put("client.ajaxTypingTimeout", PwmConstants.CLIENT_AJAX_TYPING_TIMEOUT);
        settingMap.put("client.ajaxTypingWait", PwmConstants.CLIENT_AJAX_TYPING_WAIT);
        settingMap.put("client.activityMaxEpsRate", PwmConstants.CLIENT_ACTIVITY_MAX_EPS_RATE);
        settingMap.put("enableIdleTimeout", pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_IDLE_TIMEOUT));
        settingMap.put("pageLeaveNotice", pwmApplication.getConfig().readSettingAsLong(PwmSetting.SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT));
        settingMap.put("setting-showHidePasswordFields",pwmApplication.getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_SHOW_HIDE_PASSWORD_FIELDS));
        settingMap.put("setting-displayEula",PwmConstants.ENABLE_EULA_DISPLAY);
        settingMap.put("MaxInactiveInterval",request.getSession().getMaxInactiveInterval());

        settingMap.put("url-context",request.getContextPath());
        settingMap.put("url-logout",request.getContextPath() + SessionFilter.rewriteURL("/public/Logout?idle=true", request, response));
        settingMap.put("url-command",request.getContextPath() + SessionFilter.rewriteURL("/public/CommandServlet", request, response));
        settingMap.put("url-resources",request.getContextPath() + SessionFilter.rewriteURL("/public/resources", request, response));
        settingMap.put("url-restservice",request.getContextPath() + SessionFilter.rewriteURL("/public/rest", request, response));
        settingMap.put("url-setupresponses",request.getContextPath() + SessionFilter.rewriteURL("/private/SetupResponses", request, response));

        {
            String passwordGuideText = pwmApplication.getConfig().readSettingAsLocalizedString(PwmSetting.DISPLAY_PASSWORD_GUIDE_TEXT,pwmSession.getSessionStateBean().getLocale());
            passwordGuideText = MacroMachine.expandMacros(passwordGuideText, pwmApplication, pwmSession.getUserInfoBean(), pwmSession.getSessionStateBean().isAuthenticated() ? pwmSession.getSessionManager().getUserDataReader() : null);
            settingMap.put("passwordGuideText",passwordGuideText);
        }


        {
            final List<String> formTypeOptions = new ArrayList<String>();
            for (final FormConfiguration.Type type : FormConfiguration.Type.values()) {
                formTypeOptions.add(type.toString());
            }
            settingMap.put("formTypeOptions",formTypeOptions);
        }

        {
            final List<String> actionTypeOptions = new ArrayList<String>();
            for (final ActionConfiguration.Type type : ActionConfiguration.Type.values()) {
                actionTypeOptions.add(type.toString());
            }
            settingMap.put("actionTypeOptions",actionTypeOptions);
        }

        {
            final List<String> epsTypes = new ArrayList<String>();
            for (final Statistic.EpsType loopEpsType : Statistic.EpsType.values()) {
                epsTypes.add(loopEpsType.toString());
            }
            settingMap.put("epsTypes",epsTypes);
        }

        {
            final List<String> epsDurations = new ArrayList<String>();
            for (final Statistic.EpsDuration loopEpsDuration : Statistic.EpsDuration.values()) {
                epsDurations.add(loopEpsDuration.toString());
            }
            settingMap.put("epsDurations",epsDurations);
        }

        {
            final Map<String,String> localeInfo = new TreeMap<String,String>();
            final Map<String,String> localeDisplayNames = new TreeMap<String,String>();
            final Map<String,String> localeFlags = new TreeMap<String,String>();

            for (final Locale locale : pwmApplication.getConfig().getKnownLocales()) {
                final String flagCode = pwmApplication.getConfig().getKnownLocaleFlagMap().get(locale);
                localeFlags.put(locale.toString(),flagCode);
                localeInfo.put(locale.toString(),locale.getDisplayLanguage() + " - " + locale.getDisplayLanguage(locale));
                localeDisplayNames.put(locale.toString(),locale.getDisplayLanguage());
            }

            settingMap.put("localeInfo",localeInfo);
            settingMap.put("localeDisplayNames",localeDisplayNames);
            settingMap.put("localeFlags",localeFlags);
            settingMap.put("defaultLocale",PwmConstants.DEFAULT_LOCALE.toString());
        }

        return settingMap;
    }
}
