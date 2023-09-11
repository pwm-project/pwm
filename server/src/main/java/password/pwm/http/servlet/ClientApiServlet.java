/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package password.pwm.http.servlet;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Data;
import password.pwm.AppProperty;
import password.pwm.DomainProperty;
import password.pwm.Permission;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.ProfileID;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.SelectableContextMode;
import password.pwm.config.option.WebServiceUsage;
import password.pwm.config.profile.ChangePasswordProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpHeader;
import password.pwm.http.HttpMethod;
import password.pwm.http.IdleTimeoutCalculator;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.PwmURL;
import password.pwm.i18n.Display;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.svc.stats.EpsStatistic;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsService;
import password.pwm.user.UserInfo;
import password.pwm.util.i18n.LocaleComparators;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;
import password.pwm.ws.server.RestResultBean;
import password.pwm.ws.server.rest.RestHealthServer;
import password.pwm.ws.server.rest.RestStatisticsServer;
import password.pwm.ws.server.rest.bean.PublicHealthData;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@WebServlet(
        name = "ClientApiServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PUBLIC + "/api",
        }
)
public class ClientApiServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ClientApiServlet.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    @Data
    public static class AppData
    {
        @SuppressWarnings( "checkstyle:MemberName" )
        public Map<String, Object> PWM_GLOBAL;
    }

    @Data
    public static class PingResponse
    {
        private Instant time;
        private String runtimeNonce;
    }

    public enum ClientApiAction implements AbstractPwmServlet.ProcessAction
    {
        clientData( HttpMethod.GET ),
        strings( HttpMethod.GET ),
        health( HttpMethod.GET ),
        ping( HttpMethod.GET ),
        statistics( HttpMethod.GET ),;

        private final HttpMethod method;

        ClientApiAction( final HttpMethod method )
        {
            this.method = method;
        }

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    public Optional<Class<? extends ProcessAction>> getProcessActionsClass( )
    {
        return Optional.of( ClientApiAction.class );
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        // no mvc pattern in this servlet
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        return ProcessStatus.Continue;
    }


    @ActionHandler( action = "clientData" )
    public ProcessStatus processRestClientData( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException
    {
        final String pageUrl = pwmRequest.readParameterAsString( "pageUrl", PwmHttpRequestWrapper.Flag.BypassValidation );
        final String etagParam = pwmRequest.readParameterAsString( "etag", PwmHttpRequestWrapper.Flag.BypassValidation );

        final int maxCacheAgeSeconds = 60 * 5;

        final String eTagValue = makeClientEtag( pwmRequest );

        // check the incoming header;
        final String ifNoneMatchValue = pwmRequest.readHeaderValueAsString( HttpHeader.If_None_Match );

        if ( ifNoneMatchValue != null && ifNoneMatchValue.equals( eTagValue ) && eTagValue.equals( etagParam ) )
        {
            pwmRequest.getPwmResponse().setStatus( 304 );
            return ProcessStatus.Halt;
        }

        pwmRequest.getPwmResponse().setHeader( HttpHeader.ETag, eTagValue );
        pwmRequest.getPwmResponse().setHeader( HttpHeader.Expires, String.valueOf( System.currentTimeMillis() + ( maxCacheAgeSeconds * 1000 ) ) );
        pwmRequest.getPwmResponse().setHeader( HttpHeader.CacheControl, "public, max-age=" + maxCacheAgeSeconds );

        final AppData appData = makeAppData(
                pwmRequest.getPwmDomain(),
                pwmRequest,
                pageUrl );

        final RestResultBean<AppData> restResultBean = RestResultBean.withData( appData, AppData.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "strings" )
    public ProcessStatus doGetStringsData( final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final String bundleName = pwmRequest.readParameterAsString( "bundle" );
        final int maxCacheAgeSeconds = 60 * 5;

        final String eTagValue = makeClientEtag( pwmRequest );

        pwmRequest.getPwmResponse().setHeader( HttpHeader.ETag, eTagValue );
        pwmRequest.getPwmResponse().setHeader( HttpHeader.Expires, String.valueOf( System.currentTimeMillis() + ( maxCacheAgeSeconds * 1000 ) ) );
        pwmRequest.getPwmResponse().setHeader( HttpHeader.CacheControl, "public, max-age=" + maxCacheAgeSeconds );

        try
        {
            final Map<String, String> displayData = new LinkedHashMap<>( makeDisplayData( pwmRequest.getPwmDomain(),
                    pwmRequest, bundleName ) );
            final RestResultBean<Map> restResultBean = RestResultBean.withData( displayData, Map.class );
            pwmRequest.outputJsonResult( restResultBean );
        }
        catch ( final Exception e )
        {
            final String errorMSg = "error during rest /strings call for bundle " + bundleName + ", error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMSg );
            LOGGER.debug( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "health" )
    public ProcessStatus restHealthProcessor( final PwmRequest pwmRequest )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        precheckPublicHealthAndStats( pwmRequest );

        try
        {
            final PublicHealthData jsonOutput = RestHealthServer.processGetHealthCheckData(
                    pwmRequest.getPwmDomain(),
                    pwmRequest.getLocale() );
            final RestResultBean restResultBean = RestResultBean.withData( jsonOutput, PublicHealthData.class );
            pwmRequest.outputJsonResult( restResultBean );
        }
        catch ( final Exception e )
        {
            final String errorMessage = "unexpected error executing web service: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMessage );
            LOGGER.debug( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation );
        }
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "ping" )
    public ProcessStatus processPingRequest( final PwmRequest pwmRequest )
            throws IOException
    {
        final PingResponse pingResponse = new PingResponse();
        pingResponse.setTime( Instant.now() );
        pingResponse.setRuntimeNonce( pwmRequest.getPwmApplication().getRuntimeNonce() );
        pwmRequest.outputJsonResult( RestResultBean.withData( pingResponse, PingResponse.class ) );
        return ProcessStatus.Halt;
    }

    public static String makeClientEtag( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        final StringBuilder inputString = new StringBuilder();
        inputString.append( PwmConstants.BUILD_NUMBER );
        inputString.append( pwmDomain.getPwmApplication().getStartupTime().toEpochMilli() );
        inputString.append( pwmDomain.getDomainID().toString() );
        inputString.append( pwmDomain.getPwmApplication().getRuntimeNonce() );
        inputString.append( pwmRequest.getHttpServletRequest().getSession().getMaxInactiveInterval() );

        inputString.append( pwmRequest.getLocale().toString() );

        if ( pwmRequest.isAuthenticated() )
        {
            final UserInfo userInfo = pwmRequest.getPwmSession().getUserInfo();
            inputString.append( userInfo.getUserGuid() );
            inputString.append( userInfo.getUserIdentity().toDisplayString() );
        }

        return SecureEngine.hash( inputString.toString(), PwmHashAlgorithm.SHA1 ).toLowerCase();
    }

    private AppData makeAppData(
            final PwmDomain pwmDomain,
            final PwmRequest pwmRequest,
            final String pageUrl
    )
            throws PwmUnrecoverableException
    {
        final AppData appData = new AppData();
        appData.PWM_GLOBAL = makeClientData( pwmDomain, pwmRequest, pageUrl );
        return appData;
    }

    private static Map<String, Object> makeClientData(
            final PwmDomain pwmDomain,
            final PwmRequest pwmRequest,
            final String pageUrl
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Locale userLocale = pwmRequest.getLocale();

        final DomainConfig config = pwmDomain.getConfig();
        final TreeMap<String, Object> settingMap = new TreeMap<>();

        settingMap.put( "client.ajaxTypingTimeout", Integer.parseInt( config.readDomainProperty( DomainProperty.CLIENT_AJAX_TYPING_TIMEOUT ) ) );
        settingMap.put( "client.ajaxTypingWait", Integer.parseInt( config.readDomainProperty( DomainProperty.CLIENT_AJAX_TYPING_WAIT ) ) );
        settingMap.put( "client.activityMaxEpsRate", Integer.parseInt( config.readAppProperty( AppProperty.CLIENT_ACTIVITY_MAX_EPS_RATE ) ) );
        settingMap.put( "client.locale", LocaleHelper.getBrowserLocaleString( pwmSession.getSessionStateBean().getLocale() ) );
        settingMap.put( "client.pwShowRevertTimeout", Integer.parseInt( config.readAppProperty( AppProperty.CLIENT_PW_SHOW_REVERT_TIMEOUT ) ) );
        settingMap.put( "enableIdleTimeout", config.readSettingAsBoolean( PwmSetting.DISPLAY_IDLE_TIMEOUT ) );
        settingMap.put( "pageLeaveNotice", config.getAppConfig().readSettingAsLong( PwmSetting.SECURITY_PAGE_LEAVE_NOTICE_TIMEOUT ) );
        settingMap.put( "setting-showHidePasswordFields", pwmDomain.getConfig().readSettingAsBoolean( password.pwm.config.PwmSetting.DISPLAY_SHOW_HIDE_PASSWORD_FIELDS ) );
        settingMap.put( "setting-displayEula", PwmConstants.ENABLE_EULA_DISPLAY );
        settingMap.put( "setting-showStrengthMeter", config.readSettingAsBoolean( PwmSetting.PASSWORD_SHOW_STRENGTH_METER ) );
        settingMap.put( "paramName.locale", config.readAppProperty( AppProperty.HTTP_PARAM_NAME_LOCALE ) );
        settingMap.put( "runtimeNonce", pwmDomain.getPwmApplication().getRuntimeNonce() );
        settingMap.put( "applicationMode", pwmDomain.getApplicationMode() );

        settingMap.putAll( makeIdleTimeoutClientData( pwmRequest, config, pageUrl ) );

        {
            final String contextPath = pwmRequest.getBasePath();
            settingMap.put( "url-context", contextPath );
            settingMap.put( "url-logout", contextPath + PwmServletDefinition.Logout.servletUrl() );
            settingMap.put( "url-command", contextPath + PwmServletDefinition.PublicCommand.servletUrl() );
            settingMap.put( "url-resources", contextPath + "/public/resources" + pwmDomain.getResourceServletService().getResourceNonce() );
            settingMap.put( "url-restservice", contextPath + "/public/rest" );
        }

        if ( pwmRequest.isAuthenticated() )
        {
            final ProfileID profileID = pwmSession.getUserInfo().getProfileIDs().get( ProfileDefinition.ChangePassword );
            if ( profileID != null )
            {
                final ChangePasswordProfile changePasswordProfile = pwmRequest.getDomainConfig().getChangePasswordProfile().get( profileID );
                final String configuredGuideText = changePasswordProfile.readSettingAsLocalizedString(
                        PwmSetting.DISPLAY_PASSWORD_GUIDE_TEXT,
                        pwmSession.getSessionStateBean().getLocale()
                );
                if ( StringUtil.notEmpty( configuredGuideText ) )
                {
                    final MacroRequest macroRequest = pwmRequest.getMacroMachine();
                    final String expandedText = macroRequest.expandMacros( configuredGuideText );
                    settingMap.put( "passwordGuideText", expandedText );
                }
            }
        }

        settingMap.put( "epsTypes", EnumUtil.enumStream( EpsStatistic.class )
                .map( EpsStatistic::toString )
                .collect( Collectors.toList() ) );

        settingMap.put( "epsDurations", EnumUtil.enumStream( Statistic.EpsDuration.class )
                .map( Statistic.EpsDuration::toString )
                .collect( Collectors.toList() ) );

        {
            final List<Locale> knownLocales = new ArrayList<>( pwmRequest.getAppConfig().getKnownLocales() );
            knownLocales.sort( LocaleComparators.localeComparator( ) );

            final Map<String, String> localeInfo = new LinkedHashMap<>( knownLocales.size() );
            final Map<String, String> localeDisplayNames = new LinkedHashMap<>( knownLocales.size() );
            final Map<String, String> localeFlags = new LinkedHashMap<>( knownLocales.size() );

            for ( final Locale locale : knownLocales )
            {
                final String flagCode = pwmRequest.getAppConfig().getKnownLocaleFlagMap().get( locale );
                localeFlags.put( locale.toString(), flagCode );
                localeInfo.put( locale.toString(), locale.getDisplayName( PwmConstants.DEFAULT_LOCALE ) + " - " + locale.getDisplayLanguage( userLocale ) );
                localeDisplayNames.put( locale.toString(), locale.getDisplayLanguage() );
            }

            settingMap.put( "localeInfo", localeInfo );
            settingMap.put( "localeDisplayNames", localeDisplayNames );
            settingMap.put( "localeFlags", localeFlags );
            settingMap.put( "defaultLocale", PwmConstants.DEFAULT_LOCALE.toString() );
        }

        if ( pwmDomain.getConfig().readSettingAsEnum( PwmSetting.LDAP_SELECTABLE_CONTEXT_MODE, SelectableContextMode.class ) != SelectableContextMode.NONE )
        {
            final Map<ProfileID, LdapProfile> configuredProfiles = pwmDomain.getConfig().getLdapProfiles();

            final Map<ProfileID, Map<String, String>> ldapProfiles = new LinkedHashMap<>( configuredProfiles.size() );
            for ( final Map.Entry<ProfileID, LdapProfile> entry : configuredProfiles.entrySet() )
            {
                final ProfileID ldapProfile = entry.getKey();
                final Map<String, String> contexts = entry.getValue().getSelectableContexts( pwmRequest.getLabel(), pwmDomain );
                ldapProfiles.put( ldapProfile, contexts );
            }
            settingMap.put( "ldapProfiles", ldapProfiles );
        }

        return settingMap;
    }


    private Map<String, String> makeDisplayData(
            final PwmDomain pwmDomain,
            final PwmRequest pwmRequest,
            final String bundleName
    )
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final Class<? extends PwmDisplayBundle> displayClass = LocaleHelper.classForShortName( bundleName ).orElse( Display.class );

        final Locale userLocale = pwmSession.getSessionStateBean().getLocale();
        final DomainConfig config = pwmDomain.getConfig();
        final TreeMap<String, String> displayStrings = new TreeMap<>();
        final ResourceBundle bundle = ResourceBundle.getBundle( displayClass.getName() );
        try
        {
            final MacroRequest macroRequest = pwmRequest.getMacroMachine();
            for ( final String key : new TreeSet<>( Collections.list( bundle.getKeys() ) ) )
            {
                String displayValue = LocaleHelper.getLocalizedMessage( userLocale, key, config, displayClass );
                displayValue = macroRequest.expandMacros( displayValue );
                displayStrings.put( key, displayValue );
            }
        }
        catch ( final Exception e )
        {
            LOGGER.error( pwmRequest, () -> "error expanding macro display value: " + e.getMessage() );
        }
        return displayStrings;
    }

    @ActionHandler( action = "statistics" )
    public ProcessStatus restStatisticsHandler( final PwmRequest pwmRequest )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException
    {
        precheckPublicHealthAndStats( pwmRequest );

        final String statKey = pwmRequest.readParameterAsString( "statKey" );
        final String statName = pwmRequest.readParameterAsString( "statName" );
        final String days = pwmRequest.readParameterAsString( "days" );

        final StatisticsService statisticsManager = pwmRequest.getPwmDomain().getStatisticsService();
        final RestStatisticsServer.OutputVersion1.JsonOutput jsonOutput = new RestStatisticsServer.OutputVersion1.JsonOutput();
        jsonOutput.EPS = RestStatisticsServer.OutputVersion1.addEpsStats( statisticsManager );

        if ( !StringUtil.isEmpty( statName ) )
        {
            jsonOutput.nameData = RestStatisticsServer.OutputVersion1.doNameStat(
                    pwmRequest.getPwmRequestContext(),
                    statisticsManager,
                    statName,
                    days );
        }
        else
        {
            jsonOutput.keyData = RestStatisticsServer.OutputVersion1.doKeyStat( statisticsManager, statKey );
        }

        final RestResultBean<RestStatisticsServer.OutputVersion1.JsonOutput> restResultBean = RestResultBean.withData(
                jsonOutput,
                RestStatisticsServer.OutputVersion1.JsonOutput.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    private void precheckPublicHealthAndStats( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        if ( pwmRequest.getPwmDomain().getApplicationMode() == PwmApplicationMode.CONFIGURATION )
        {
            return;
        }

        if ( pwmRequest.getPwmDomain().getApplicationMode() != PwmApplicationMode.RUNNING )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE );
            throw new PwmUnrecoverableException( errorInformation );
        }

        final Set<WebServiceUsage> enabledUsages = pwmRequest.getDomainConfig().readSettingAsOptionList( PwmSetting.WEBSERVICES_PUBLIC_ENABLE, WebServiceUsage.class );
        if ( !enabledUsages.contains( WebServiceUsage.Health ) && !enabledUsages.contains( WebServiceUsage.Statistics ) )
        {
            if ( !pwmRequest.isAuthenticated() )
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_AUTHENTICATION_REQUIRED );
                throw new PwmUnrecoverableException( errorInformation );
            }

            if ( !pwmRequest.checkPermission( Permission.PWMADMIN ) )
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, "admin privileges required" );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }
    }


    private static Map<String, Object> makeIdleTimeoutClientData(
            final PwmRequest pwmRequest,
            final DomainConfig config,
            final String pageUrl )
    {
        long idleSeconds = config.readSettingAsLong( PwmSetting.IDLE_TIMEOUT_SECONDS );
        if ( StringUtil.isEmpty( pageUrl ) )
        {
            LOGGER.warn( pwmRequest, () -> "request to /client data did not include pageUrl" );
        }
        else
        {
            try
            {
                final PwmURL pwmUrl = PwmURL.create( URI.create( pageUrl ), pwmRequest.getContextPath(), pwmRequest.getAppConfig() );
                final TimeDuration maxIdleTime = IdleTimeoutCalculator.idleTimeoutForRequest( pwmRequest, pwmUrl );
                idleSeconds = maxIdleTime.as( TimeDuration.Unit.SECONDS );
            }
            catch ( final Exception e )
            {
                LOGGER.error( pwmRequest, () -> "error determining idle timeout time for request: " + e.getMessage() );
            }
        }

        return Collections.singletonMap( "MaxInactiveInterval", idleSeconds );
    }
}

