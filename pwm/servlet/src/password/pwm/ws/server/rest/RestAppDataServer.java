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
import password.pwm.*;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.*;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditRecord;
import password.pwm.i18n.Config;
import password.pwm.i18n.Display;
import password.pwm.i18n.LocaleHelper;
import password.pwm.i18n.Message;
import password.pwm.servlet.ResourceFileServlet;
import password.pwm.util.*;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.*;

@Path("/app-data")
public class RestAppDataServer {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(RestAppDataServer.class);

    public static class AppData implements Serializable {
        public Map<String,String> PWM_STRINGS;
        public Map<String,Object> PWM_GLOBAL;
    }

    public static class SettingInfo implements Serializable {
        public String key;
        public String label;
        public String description;
        public PwmSetting.Category category;
        public PwmSettingSyntax syntax;
        public boolean hidden;
        public boolean required;
    }

    public static class CategoryInfo implements Serializable {
        public int level;
        public String key;
        public String description;
        public String label;
        public PwmSettingSyntax syntax;
        public PwmSetting.Category.Type type;
        public boolean hidden;
    }

    public static class LocaleInfo implements Serializable {
        public String description;
        public String key;
    }

    public static class TemplateInfo implements Serializable {
        public String description;
        public String key;
    }


    @GET
    @Produces(MediaType.TEXT_HTML)
    public javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        return RestServerHelper.doHtmlRedirect();
    }

    @GET
    @Path("/audit")
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetAppAuditData(
            @Context HttpServletRequest request,
            @QueryParam("maximum") int maximum
    ) throws ChaiUnavailableException, PwmUnrecoverableException {
        maximum = maximum > 0 ? maximum : 10 * 1000;

        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, RestServerHelper.ServiceType.NORMAL, null);
        } catch (PwmUnrecoverableException e) {
            return Response.ok(RestServerHelper.outputJsonErrorResult(e.getErrorInformation(),request)).build();
        }

        if (!Permission.checkPermission(Permission.PWMADMIN, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
            final ErrorInformation errorInfo = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return Response.ok(RestResultBean.fromErrorInformation(errorInfo,restRequestBean.getPwmApplication(),restRequestBean.getPwmSession()).toJson()).build();
        }

        final ArrayList<Map<String,Object>> gridData = new ArrayList<Map<String,Object>>();
        for (Iterator<AuditRecord> iterator = restRequestBean.getPwmApplication().getAuditManager().readLocalDB(); iterator.hasNext() && gridData.size() <= maximum; ) {
            final AuditRecord loopRecord = iterator.next();
            try {
                final Map<String, Object> rowData = new HashMap<String, Object>();
                rowData.put("timestamp", loopRecord.getTimestamp());
                rowData.put("perpID", loopRecord.getPerpetratorID());
                rowData.put("perpDN", loopRecord.getPerpetratorDN());
                rowData.put("event", loopRecord.getEventCode().toString());
                rowData.put("message",loopRecord.getMessage());
                rowData.put("targetID",loopRecord.getTargetID());
                rowData.put("targetDN",loopRecord.getTargetDN());
                rowData.put("srcIP",loopRecord.getSourceAddress());
                rowData.put("srcHost",loopRecord.getSourceHost());
                gridData.add(rowData);
            } catch (IllegalStateException e) { /* ignore */ }
            if (gridData.size() >= maximum) {
                break;
            }
        }

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(gridData);
        return Response.ok(restResultBean.toJson()).build();
    }

    @GET
    @Path("/session")
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetAppSessionData(
            @Context HttpServletRequest request,
            @QueryParam("maximum") int maximum
    ) throws ChaiUnavailableException, PwmUnrecoverableException {
        maximum = maximum > 0 ? maximum : 10 * 1000;

        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, RestServerHelper.ServiceType.NORMAL, null);
        } catch (PwmUnrecoverableException e) {
            return Response.ok(RestServerHelper.outputJsonErrorResult(e.getErrorInformation(),request)).build();
        }

        if (!Permission.checkPermission(Permission.PWMADMIN, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
            final ErrorInformation errorInfo = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return Response.ok(RestResultBean.fromErrorInformation(errorInfo,restRequestBean.getPwmApplication(),restRequestBean.getPwmSession()).toJson()).build();
        }

        final ContextManager theManager = ContextManager.getContextManager(request.getSession().getServletContext());
        final Set<PwmSession> activeSessions = new LinkedHashSet<PwmSession>(theManager.getPwmSessions());
        final ArrayList<Map<String,Object>> gridData = new ArrayList<Map<String,Object>>();
        for (Iterator<PwmSession> iterator = activeSessions.iterator(); iterator.hasNext() && gridData.size() <= maximum;) {
            final PwmSession loopSession = iterator.next();
            try {
                final SessionStateBean loopSsBean = loopSession.getSessionStateBean();
                final UserInfoBean loopUiBean = loopSession.getUserInfoBean();
                final Map<String, Object> rowData = new HashMap<String, Object>();
                rowData.put("label", loopSession.getSessionStateBean().getSessionID());
                rowData.put("createTime", loopSession.getSessionStateBean().getSessionCreationTime());
                rowData.put("lastTime", loopSession.getSessionStateBean().getSessionLastAccessedTime());
                rowData.put("idle", TimeDuration.fromCurrent(loopSession.getLastAccessedTime()).asCompactString());
                rowData.put("locale", loopSsBean.getLocale() == null ? "" : loopSsBean.getLocale().toString());
                rowData.put("userDN", loopSsBean.isAuthenticated() ? loopUiBean.getUserDN() : "");
                rowData.put("userID", loopSsBean.isAuthenticated() ? loopUiBean.getUserID() : "");
                rowData.put("srcAddress", loopSsBean.getSrcAddress());
                rowData.put("srcHost", loopSsBean.getSrcHostname());
                rowData.put("lastUrl", loopSsBean.getLastRequestURL());
                gridData.add(rowData);
            } catch (IllegalStateException e) { /* ignore */ }
        }
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(gridData);
        return Response.ok(restResultBean.toJson()).build();
    }

    @GET
    @Path("/intruder")
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetAppIntruderData(
            @Context HttpServletRequest request,
            @QueryParam("maximum") int maximum
    ) throws ChaiUnavailableException, PwmUnrecoverableException {
        maximum = maximum > 0 ? maximum : 10 * 1000;

        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, RestServerHelper.ServiceType.NORMAL, null);
        } catch (PwmUnrecoverableException e) {
            return Response.ok(RestServerHelper.outputJsonErrorResult(e.getErrorInformation(),request)).build();
        }

        if (!Permission.checkPermission(Permission.PWMADMIN, restRequestBean.getPwmSession(), restRequestBean.getPwmApplication())) {
            final ErrorInformation errorInfo = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return Response.ok(RestResultBean.fromErrorInformation(errorInfo,restRequestBean.getPwmApplication(),restRequestBean.getPwmSession()).toJson()).build();
        }

        final TreeMap<String,Object> returnData = new TreeMap<String,Object>();
        try {
            for (final RecordType recordType : RecordType.values()) {
                returnData.put(recordType.toString(),restRequestBean.getPwmApplication().getIntruderManager().getRecords(recordType, maximum));
            }
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            return Response.ok(RestResultBean.fromErrorInformation(errorInfo,restRequestBean.getPwmApplication(),restRequestBean.getPwmSession()).toJson()).build();
        }

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnData);
        return Response.ok(restResultBean.toJson()).build();
    }

    @GET
    @Path("/client-config")
    @Produces(MediaType.APPLICATION_JSON)
    public Response doGetAppSettingData(
            @Context HttpServletRequest request,
            @Context HttpServletResponse response
    ) throws ChaiUnavailableException, PwmUnrecoverableException {

        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, RestServerHelper.ServiceType.PUBLIC, null);
        } catch (PwmUnrecoverableException e) {
            return Response.ok(RestServerHelper.outputJsonErrorResult(e.getErrorInformation(),request)).build();
        }

        final String eTagValue = ResourceFileServlet.makeNonce(restRequestBean.getPwmApplication());

        // check the incoming header;
        final String ifNoneMatchValue = request.getHeader("If-None-Match");

        if (ifNoneMatchValue != null && ifNoneMatchValue.equals(eTagValue)) {
            return Response.notModified().build();
        }
        response.setHeader("ETag",eTagValue);
        response.setHeader("Cache-Control","private, max-age=0");

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(makeClientConfigData(restRequestBean));
        return Response.ok(restResultBean.toJson()).build();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/client/{etagUri}")
    public Response doGetAppData(
            @Context HttpServletRequest request,
            @Context HttpServletResponse response
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException {
        final int maxCacheAgeSeconds = 60 * 5;
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, RestServerHelper.ServiceType.PUBLIC, null);
        } catch (PwmUnrecoverableException e) {
            return Response.ok(RestServerHelper.outputJsonErrorResult(e.getErrorInformation(),request)).build();
        }

        final String eTagValue = makeClientEtag(request, restRequestBean.getPwmApplication(), restRequestBean.getPwmSession());

        // check the incoming header;
        final String ifNoneMatchValue = request.getHeader("If-None-Match");

        if (ifNoneMatchValue != null && ifNoneMatchValue.equals(eTagValue)) {
            return Response.notModified().build();
        }

        response.setHeader("ETag",eTagValue);
        response.setDateHeader("Expires", System.currentTimeMillis() + (maxCacheAgeSeconds * 1000));
        response.setHeader("Cache-Control","public, max-age=" + maxCacheAgeSeconds);

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
        appData.PWM_GLOBAL = makeClientData(pwmApplication, pwmSession, request, response);
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
            String displayValue = Display.getLocalizedMessage(userLocale, key, config);
            try {
                displayValue = MacroMachine.expandMacros(displayValue, pwmApplication, pwmSession.getUserInfoBean(),pwmSession.getSessionManager().getUserDataReader());
            } catch (Exception e) {
                LOGGER.error(pwmSession,"error expanding macro for display value " + displayValue);
            }
            displayStrings.put(key, displayValue);
        }
        displayStrings.put(Message.SUCCESS_UNKNOWN.toString(), Message.getLocalizedMessage(userLocale, Message.SUCCESS_UNKNOWN, config));
        return displayStrings;
    }

    private static Map<String,Object> makeClientData(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest request,
            final HttpServletResponse response
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final TreeMap<String,Object> settingMap = new TreeMap<String, Object>();
        settingMap.put("client.ajaxTypingTimeout", Integer.parseInt(pwmApplication.readAppProperty(AppProperty.CLIENT_AJAX_TYPING_TIMEOUT)));
        settingMap.put("client.ajaxTypingWait", Integer.parseInt(pwmApplication.readAppProperty(AppProperty.CLIENT_AJAX_TYPING_WAIT)));
        settingMap.put("client.activityMaxEpsRate", Integer.parseInt(pwmApplication.readAppProperty(AppProperty.CLIENT_ACTIVITY_MAX_EPS_RATE)));
        settingMap.put("enableIdleTimeout", pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_IDLE_TIMEOUT));
        settingMap.put("pageLeaveNotice", pwmApplication.getConfig().readSettingAsLong(PwmSetting.SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT));
        settingMap.put("setting-showHidePasswordFields",pwmApplication.getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_SHOW_HIDE_PASSWORD_FIELDS));
        settingMap.put("setting-displayEula",PwmConstants.ENABLE_EULA_DISPLAY);
        settingMap.put("MaxInactiveInterval",request.getSession().getMaxInactiveInterval());
        settingMap.put("paramName.locale", PwmConstants.PARAM_LOCALE);
        settingMap.put("startupTime",pwmApplication.getStartupTime());

        settingMap.put("url-context",request.getContextPath());
        settingMap.put("url-logout",request.getContextPath() + SessionFilter.rewriteURL("/public/Logout?idle=true", request, response));
        settingMap.put("url-command",request.getContextPath() + SessionFilter.rewriteURL("/public/CommandServlet", request, response));
        settingMap.put("url-resources",request.getContextPath() + SessionFilter.rewriteURL("/public/resources" + ResourceFileServlet.makeResourcePathNonce(pwmApplication), request, response));
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

    private static LinkedHashMap<String,Object> makeClientConfigData(final RestRequestBean restRequestBean) {
        final LinkedHashMap<String,Object> returnMap = new LinkedHashMap<String, Object>();
        final Locale locale = restRequestBean.getPwmSession().getSessionStateBean().getLocale();
        {
            final LinkedHashMap<String,Object> settingMap = new LinkedHashMap<String, Object>();
            for (final PwmSetting setting : PwmSetting.values()) {
                final SettingInfo settingInfo = new SettingInfo();
                settingInfo.key = setting.getKey();
                settingInfo.description = setting.getDescription(locale);
                settingInfo.label = setting.getLabel(locale);
                settingInfo.syntax = setting.getSyntax();
                settingInfo.category = setting.getCategory();
                settingInfo.required = setting.isRequired();
                settingInfo.hidden = setting.isHidden();
                settingMap.put(setting.getKey(),settingInfo);
            }
            returnMap.put("settings",settingMap);
        }
        {
            final LinkedHashMap<String,Object> categoryMap = new LinkedHashMap<String, Object>();
            for (final PwmSetting.Category category : PwmSetting.Category.values()) {
                final CategoryInfo categoryInfo = new CategoryInfo();
                categoryInfo.key = category.getKey();
                categoryInfo.level = category.getLevel();
                categoryInfo.description = category.getDescription(locale);
                categoryInfo.label = category.getLabel(locale);
                categoryInfo.type = category.getType();
                categoryInfo.hidden = category.isHidden();
                categoryMap.put(category.getKey(),categoryInfo);
            }
            returnMap.put("categories",categoryMap);
        }
        {
            final LinkedHashMap<String,Object> labelMap = new LinkedHashMap<String, Object>();
            for (final PwmConstants.EDITABLE_LOCALE_BUNDLES localeBundle : PwmConstants.EDITABLE_LOCALE_BUNDLES.values()) {
                final LocaleInfo localeInfo = new LocaleInfo();
                localeInfo.description = localeBundle.getTheClass().getSimpleName();
                localeInfo.key = localeBundle.toString();
                labelMap.put(localeBundle.getTheClass().getSimpleName(),localeInfo);
            }
            returnMap.put("locales",labelMap);
        }
        {
            final LinkedHashMap<String,Object> templateMap = new LinkedHashMap<String, Object>();
            for (final PwmSetting.Template template : PwmSetting.Template.values()) {
                final TemplateInfo templateInfo = new TemplateInfo();
                templateInfo.description = template.getLabel(locale);
                templateInfo.key = template.toString();
                templateMap.put(template.toString(),templateInfo);
            }
            returnMap.put("templates",templateMap);
        }
        {
            final Configuration config = restRequestBean.getPwmApplication().getConfig();
            final TreeMap<String,String> displayStrings = new TreeMap<String, String>();
            final ResourceBundle bundle = ResourceBundle.getBundle(Config.class.getName());
            for (final String key : new TreeSet<String>(Collections.list(bundle.getKeys()))) {
                String displayValue = LocaleHelper.getLocalizedMessage(locale, key, config, Config.class);
                try {
                    displayValue = MacroMachine.expandMacros(
                            displayValue,
                            restRequestBean.getPwmApplication(),
                            restRequestBean.getPwmSession().getUserInfoBean(),
                            restRequestBean.getPwmSession().getSessionManager().getUserDataReader()
                    );
                } catch (Exception e) {
                    LOGGER.error(restRequestBean.getPwmSession(),"error expanding macro for display value " + displayValue);
                }
                displayStrings.put(key, displayValue);
            }
            returnMap.put("display",displayStrings);
        }

        return returnMap;
    }

    public static String makeClientEtag(final HttpServletRequest request, final PwmApplication pwmApplication, final PwmSession pwmSession)
            throws IOException
    {
        if (pwmSession == null || !pwmSession.getSessionStateBean().isAuthenticated()) {
            return ResourceFileServlet.makeNonce(pwmApplication);
        }

        final StringBuilder inputString = new StringBuilder();
        inputString.append(PwmConstants.BUILD_NUMBER);
        inputString.append(ResourceFileServlet.makeNonce(pwmApplication));
        inputString.append(request.getSession().getMaxInactiveInterval());

        if (pwmSession != null) {
            inputString.append(pwmSession.getSessionStateBean().getSessionID());
            if (pwmSession.getSessionStateBean().getLocale() != null) {
                inputString.append(pwmSession.getSessionStateBean().getLocale());
            }
            if (pwmSession.getSessionStateBean().isAuthenticated()) {
                inputString.append(pwmSession.getUserInfoBean().getUserGuid());
                inputString.append(pwmSession.getUserInfoBean().getLocalAuthTime());
            }
        }
        return Helper.md5sum(inputString.toString());
    }
}
