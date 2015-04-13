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

package password.pwm.ws.server.rest;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.Permission;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SelectableContextMode;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.event.AuditRecord;
import password.pwm.event.HelpdeskAuditRecord;
import password.pwm.event.SystemAuditRecord;
import password.pwm.event.UserAuditRecord;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.servlet.ResourceFileServlet;
import password.pwm.i18n.Display;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.SecureHelper;
import password.pwm.util.intruder.RecordType;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.stats.Statistic;
import password.pwm.ws.server.RestRequestBean;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.RestServerHelper;
import password.pwm.ws.server.ServicePermissions;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.util.*;

@Path("/app-data")
public class RestAppDataServer extends AbstractRestServer {

    private static final PwmLogger LOGGER = PwmLogger.forClass(RestAppDataServer.class);

    public static class AppData implements Serializable {
        public Map<String,Object> PWM_GLOBAL;
    }



    @GET
    @Produces(MediaType.TEXT_HTML)
    public javax.ws.rs.core.Response doHtmlRedirect() throws URISyntaxException {
        return RestServerHelper.doHtmlRedirect();
    }

    @GET
    @Path("/audit")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doGetAppAuditData(
            @QueryParam("maximum") int maximum
    ) 
            throws ChaiUnavailableException, PwmUnrecoverableException 
    {
        maximum = maximum > 0 ? maximum : 10 * 1000;

        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(true);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }
        
        final ArrayList<UserAuditRecord> userRecords = new ArrayList<>();
        final ArrayList<HelpdeskAuditRecord> helpdeskRecords = new ArrayList<>();
        final ArrayList<SystemAuditRecord> systemRecords = new ArrayList<>();
        final Iterator<AuditRecord> iterator = restRequestBean.getPwmApplication().getAuditManager().readVault();
        int counter = 0;
        while (iterator.hasNext() && counter <= maximum) {
            final AuditRecord loopRecord = iterator.next();
            counter++;
            if (loopRecord instanceof SystemAuditRecord) {
                systemRecords.add((SystemAuditRecord)loopRecord);
            } else if (loopRecord instanceof HelpdeskAuditRecord) {
                helpdeskRecords.add((HelpdeskAuditRecord)loopRecord);
            } else if (loopRecord instanceof UserAuditRecord) {
                userRecords.add((UserAuditRecord)loopRecord);
            }
        }
        final HashMap<String,List> outputMap = new HashMap<>();
        outputMap.put("user",userRecords);
        outputMap.put("helpdesk",helpdeskRecords);
        outputMap.put("system",systemRecords);

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(outputMap);
        LOGGER.debug(restRequestBean.getPwmSession(),"output " + counter + " audit records.");
        return restResultBean.asJsonResponse();
    }

    @GET
    @Path("/session")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doGetAppSessionData(
            @QueryParam("maximum") int maximum
    ) throws ChaiUnavailableException, PwmUnrecoverableException {
        maximum = maximum > 0 ? maximum : 10 * 1000;

        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(true);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        if (!restRequestBean.getPwmSession().getSessionManager().checkPermission(restRequestBean.getPwmApplication(), Permission.PWMADMIN)) {
            final ErrorInformation errorInfo = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return RestResultBean.fromError(errorInfo, restRequestBean).asJsonResponse();
        }

        final ContextManager theManager = ContextManager.getContextManager(request.getSession().getServletContext());
        final Set<PwmSession> activeSessions = new LinkedHashSet<>(theManager.getPwmSessions().values());
        final ArrayList<Map<String,Object>> gridData = new ArrayList<>();
        for (Iterator<PwmSession> iterator = activeSessions.iterator(); iterator.hasNext() && gridData.size() <= maximum;) {
            final PwmSession loopSession = iterator.next();
            if (loopSession != null) {
                try {
                    final SessionStateBean loopSsBean = loopSession.getSessionStateBean();
                    final UserInfoBean loopUiBean = loopSession.getUserInfoBean();
                    final Map<String, Object> rowData = new HashMap<>();
                    rowData.put("label", loopSession.getSessionStateBean().getSessionID());
                    rowData.put("createTime", loopSession.getSessionStateBean().getSessionCreationTime());
                    rowData.put("lastTime", loopSession.getSessionStateBean().getSessionLastAccessedTime());
                    rowData.put("idle", loopSession.getIdleTime().asCompactString());
                    rowData.put("locale", loopSsBean.getLocale() == null ? "" : loopSsBean.getLocale().toString());
                    rowData.put("ldapProfile", loopSsBean.isAuthenticated() ? loopUiBean.getUserIdentity().getLdapProfileID() : "");
                    rowData.put("userDN", loopSsBean.isAuthenticated() ? loopUiBean.getUserIdentity().getUserDN() : "");
                    rowData.put("userID", loopSsBean.isAuthenticated() ? loopUiBean.getUsername() : "");
                    rowData.put("srcAddress", loopSsBean.getSrcAddress());
                    rowData.put("srcHost", loopSsBean.getSrcHostname());
                    rowData.put("lastUrl", loopSsBean.getLastRequestURL());
                    rowData.put("intruderAttempts", loopSsBean.getIntruderAttempts());
                    gridData.add(rowData);
                } catch (IllegalStateException e) { /* ignore */ }
            }
        }
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(gridData);
        return restResultBean.asJsonResponse();
    }

    @GET
    @Path("/intruder")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    public Response doGetAppIntruderData(
            @QueryParam("maximum") int maximum
    ) throws ChaiUnavailableException, PwmUnrecoverableException {
        maximum = maximum > 0 ? maximum : 10 * 1000;

        final RestRequestBean restRequestBean;
        try {
            final ServicePermissions servicePermissions = new ServicePermissions();
            servicePermissions.setAdminOnly(true);
            servicePermissions.setAuthRequired(true);
            servicePermissions.setBlockExternal(true);
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, servicePermissions, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        if (!restRequestBean.getPwmSession().getSessionManager().checkPermission(restRequestBean.getPwmApplication(), Permission.PWMADMIN)) {
            final ErrorInformation errorInfo = PwmError.ERROR_UNAUTHORIZED.toInfo();
            return RestResultBean.fromError(errorInfo, restRequestBean).asJsonResponse();
        }

        final TreeMap<String,Object> returnData = new TreeMap<>();
        try {
            for (final RecordType recordType : RecordType.values()) {
                returnData.put(recordType.toString(),restRequestBean.getPwmApplication().getIntruderManager().getRecords(recordType, maximum));
            }
        } catch (PwmOperationalException e) {
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_UNKNOWN,e.getMessage());
            return RestResultBean.fromError(errorInfo, restRequestBean).asJsonResponse();
        }

        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(returnData);
        return restResultBean.asJsonResponse();

    }

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Path("/client")
    public Response doGetAppClientData(
            @PathParam(value = "eTagUri") final String eTagUri,
            @Context HttpServletRequest request,
            @Context HttpServletResponse response
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException
    {
        final int maxCacheAgeSeconds = 60 * 5;
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, ServicePermissions.PUBLIC, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        final String eTagValue = makeClientEtag(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), request);

        // check the incoming header;
        final String ifNoneMatchValue = request.getHeader("If-None-Match");

        if (ifNoneMatchValue != null && ifNoneMatchValue.equals(eTagValue) && eTagValue.equals(eTagUri)) {
            return Response.notModified().build();
        }

        response.setHeader("ETag",eTagValue);
        response.setDateHeader("Expires", System.currentTimeMillis() + (maxCacheAgeSeconds * 1000));
        response.setHeader("Cache-Control","public, max-age=" + maxCacheAgeSeconds);

        final AppData appData = makeAppData(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), request, response);
        final RestResultBean restResultBean = new RestResultBean();
        restResultBean.setData(appData);
        return restResultBean.asJsonResponse();
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @Path("/strings/{bundle}")
    public Response doGetStringData(
            @PathParam(value = "bundle") final String bundleName
    )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException
    {
        final int maxCacheAgeSeconds = 60 * 5;
        final RestRequestBean restRequestBean;
        try {
            restRequestBean = RestServerHelper.initializeRestRequest(request, response, ServicePermissions.PUBLIC, null);
        } catch (PwmUnrecoverableException e) {
            return RestResultBean.fromError(e.getErrorInformation()).asJsonResponse();
        }

        final String eTagValue = makeClientEtag(restRequestBean.getPwmApplication(), restRequestBean.getPwmSession(), request);
        response.setHeader("ETag",eTagValue);
        response.setDateHeader("Expires", System.currentTimeMillis() + (maxCacheAgeSeconds * 1000));
        response.setHeader("Cache-Control","public, max-age=" + maxCacheAgeSeconds);

        try {
            final LinkedHashMap<String,String> displayData = new LinkedHashMap<>(makeDisplayData(restRequestBean.getPwmApplication(),
                    restRequestBean.getPwmSession(), bundleName));
            final RestResultBean restResultBean = new RestResultBean();
            restResultBean.setData(displayData);
            return restResultBean.asJsonResponse();
        } catch (Exception e) {
            final String errorMSg = "error during rest /strings call for bundle " + bundleName + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN,errorMSg);
            return RestResultBean.fromError(errorInformation).asJsonResponse();
        }
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
        appData.PWM_GLOBAL = makeClientData(pwmApplication, pwmSession, request, response);
        return appData;
    }

    private Map<String,String> makeDisplayData(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final String bundleName
    )
    {
        Class displayClass = LocaleHelper.classForShortName(bundleName);
        displayClass = displayClass == null ? Display.class : displayClass;

        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final Configuration config = pwmApplication.getConfig();
        final TreeMap<String,String> displayStrings = new TreeMap<>();
        final ResourceBundle bundle = ResourceBundle.getBundle(displayClass.getName());
        try {
            final MacroMachine macroMachine = pwmSession.getSessionManager().getMacroMachine(pwmApplication);
            for (final String key : new TreeSet<>(Collections.list(bundle.getKeys()))) {
                String displayValue = LocaleHelper.getLocalizedMessage(userLocale, key, config, displayClass);
                displayValue = macroMachine.expandMacros(displayValue);
                displayStrings.put(key, displayValue);
            }
        } catch (Exception e) {
            LOGGER.error(pwmSession,"error expanding macro display value: " + e.getMessage());
        }
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
        final Configuration config = pwmApplication.getConfig();
        final TreeMap<String,Object> settingMap = new TreeMap<>();
        settingMap.put("client.ajaxTypingTimeout", Integer.parseInt(config.readAppProperty(AppProperty.CLIENT_AJAX_TYPING_TIMEOUT)));
        settingMap.put("client.ajaxTypingWait", Integer.parseInt(config.readAppProperty(AppProperty.CLIENT_AJAX_TYPING_WAIT)));
        settingMap.put("client.activityMaxEpsRate", Integer.parseInt(config.readAppProperty(AppProperty.CLIENT_ACTIVITY_MAX_EPS_RATE)));
        settingMap.put("client.js.enableHtml5Dialog", Boolean.parseBoolean(config.readAppProperty(AppProperty.CLIENT_JS_ENABLE_HTML5DIALOG)));
        settingMap.put("client.pwShowRevertTimeout", Integer.parseInt(config.readAppProperty(AppProperty.CLIENT_PW_SHOW_REVERT_TIMEOUT)));
        settingMap.put("enableIdleTimeout", config.readSettingAsBoolean(PwmSetting.DISPLAY_IDLE_TIMEOUT));
        settingMap.put("pageLeaveNotice", config.readSettingAsLong(PwmSetting.SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT));
        settingMap.put("setting-showHidePasswordFields",pwmApplication.getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_SHOW_HIDE_PASSWORD_FIELDS));
        settingMap.put("setting-displayEula",PwmConstants.ENABLE_EULA_DISPLAY);
        settingMap.put("setting-showStrengthMeter",config.readSettingAsBoolean(PwmSetting.PASSWORD_SHOW_STRENGTH_METER));

        settingMap.put("MaxInactiveInterval",request.getSession().getMaxInactiveInterval());
        settingMap.put("paramName.locale", config.readAppProperty(AppProperty.HTTP_PARAM_NAME_LOCALE));
        settingMap.put("startupTime",pwmApplication.getStartupTime());
        settingMap.put("applicationMode",pwmApplication.getApplicationMode());

        final String contextPath = request.getContextPath();
        settingMap.put("url-context", contextPath);
        settingMap.put("url-logout", contextPath + "/public/Logout?idle=true");
        settingMap.put("url-command", contextPath + "/public/CommandServlet");
        settingMap.put("url-resources", contextPath + "/public/resources" + ResourceFileServlet.makeResourcePathNonce(pwmApplication));
        settingMap.put("url-restservice", contextPath + "/public/rest");
        settingMap.put("url-setupresponses",contextPath + "/private/SetupResponses");

        {
            String passwordGuideText = pwmApplication.getConfig().readSettingAsLocalizedString(PwmSetting.DISPLAY_PASSWORD_GUIDE_TEXT,pwmSession.getSessionStateBean().getLocale());
            final MacroMachine macroMachine = pwmSession.getSessionManager().getMacroMachine(pwmApplication);
            passwordGuideText = macroMachine.expandMacros(passwordGuideText);
            settingMap.put("passwordGuideText",passwordGuideText);
        }


        {
            final List<String> formTypeOptions = new ArrayList<>();
            for (final FormConfiguration.Type type : FormConfiguration.Type.values()) {
                formTypeOptions.add(type.toString());
            }
            settingMap.put("formTypeOptions",formTypeOptions);
        }

        {
            final List<String> actionTypeOptions = new ArrayList<>();
            for (final ActionConfiguration.Type type : ActionConfiguration.Type.values()) {
                actionTypeOptions.add(type.toString());
            }
            settingMap.put("actionTypeOptions",actionTypeOptions);
        }

        {
            final List<String> epsTypes = new ArrayList<>();
            for (final Statistic.EpsType loopEpsType : Statistic.EpsType.values()) {
                epsTypes.add(loopEpsType.toString());
            }
            settingMap.put("epsTypes",epsTypes);
        }

        {
            final List<String> epsDurations = new ArrayList<>();
            for (final Statistic.EpsDuration loopEpsDuration : Statistic.EpsDuration.values()) {
                epsDurations.add(loopEpsDuration.toString());
            }
            settingMap.put("epsDurations",epsDurations);
        }

        {
            final Map<String,String> localeInfo = new TreeMap<>();
            final Map<String,String> localeDisplayNames = new TreeMap<>();
            final Map<String,String> localeFlags = new TreeMap<>();

            for (final Locale locale : pwmApplication.getConfig().getKnownLocales()) {
                final String flagCode = pwmApplication.getConfig().getKnownLocaleFlagMap().get(locale);
                localeFlags.put(locale.toString(),flagCode);
                localeInfo.put(locale.toString(),locale.getDisplayName() + " - " + locale.getDisplayLanguage(locale));
                localeDisplayNames.put(locale.toString(),locale.getDisplayLanguage());
            }

            settingMap.put("localeInfo",localeInfo);
            settingMap.put("localeDisplayNames",localeDisplayNames);
            settingMap.put("localeFlags",localeFlags);
            settingMap.put("defaultLocale",PwmConstants.DEFAULT_LOCALE.toString());
        }

        if (pwmApplication.getConfig().readSettingAsEnum(PwmSetting.LDAP_SELECTABLE_CONTEXT_MODE, SelectableContextMode.class) != SelectableContextMode.NONE) {
            final Map<String,Map<String,String>> ldapProfiles = new LinkedHashMap<>();
            for (final String ldapProfile : pwmApplication.getConfig().getLdapProfiles().keySet()) {
                final Map<String,String> contexts = pwmApplication.getConfig().getLdapProfiles().get(ldapProfile).getLoginContexts();
                ldapProfiles.put(ldapProfile,contexts);
            }
            settingMap.put("ldapProfiles",ldapProfiles);
        }

        return settingMap;
    }


    public static String makeClientEtag(final PwmRequest pwmRequest)
            throws PwmUnrecoverableException
    {
        return makeClientEtag(pwmRequest.getPwmApplication(), pwmRequest.getPwmSession(), pwmRequest.getHttpServletRequest());
    }

    public static String makeClientEtag(
            final PwmApplication pwmApplication, 
            final PwmSession pwmSession,
            final HttpServletRequest httpServletRequest
    )
            throws PwmUnrecoverableException
    {
        final StringBuilder inputString = new StringBuilder();
        inputString.append(PwmConstants.BUILD_NUMBER);
        inputString.append(pwmApplication.getStartupTime().getTime());
        inputString.append(httpServletRequest.getSession().getMaxInactiveInterval());
        inputString.append(pwmApplication.getInstanceNonce());

        if (pwmSession.getSessionStateBean().getLocale() != null) {
            inputString.append(pwmSession.getSessionStateBean().getLocale());
        }

        inputString.append(pwmSession.getSessionStateBean().getSessionID());
        if (pwmSession.getSessionStateBean().isAuthenticated()) {
            inputString.append(pwmSession.getUserInfoBean().getUserGuid());
            inputString.append(pwmSession.getLoginInfoBean().getLocalAuthTime());
        }

        return SecureHelper.hash(inputString.toString(), SecureHelper.HashAlgorithm.SHA1).toLowerCase();
    }
}
