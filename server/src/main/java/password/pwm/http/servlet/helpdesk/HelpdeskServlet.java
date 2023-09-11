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

package password.pwm.http.servlet.helpdesk;

import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.PhotoDataBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.http.servlet.ControlledPwmServlet;
import password.pwm.http.servlet.helpdesk.data.HelpdeskCheckVerificationRequest;
import password.pwm.http.servlet.helpdesk.data.HelpdeskCheckVerificationResponse;
import password.pwm.http.servlet.helpdesk.data.HelpdeskSearchRequest;
import password.pwm.http.servlet.helpdesk.data.HelpdeskSearchResponse;
import password.pwm.http.servlet.peoplesearch.PhotoDataReader;
import password.pwm.http.servlet.peoplesearch.SearchRequestBean;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchResults;
import password.pwm.ldap.search.UserSearchService;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.svc.cache.CacheService;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.EnumUtil;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StatisticCounterBundle;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;

@WebServlet(
        name = "HelpdeskServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/helpdesk",
                PwmConstants.URL_PREFIX_PRIVATE + "/Helpdesk",
        }
)
public class HelpdeskServlet extends ControlledPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskServlet.class );

    private static final StatisticCounterBundle<HelpdeskAction> ACTION_STATS = new StatisticCounterBundle<>( HelpdeskAction.class );

    @Override
    protected PwmLogger getLogger()
    {
        return LOGGER;
    }

    public enum HelpdeskAction implements AbstractPwmServlet.ProcessAction
    {
        helpdeskClientData( HttpMethod.GET ),
        search( HttpMethod.POST ),
        validateOtpCode( HttpMethod.POST ),
        sendVerificationToken( HttpMethod.POST ),
        verifyVerificationToken( HttpMethod.POST ),
        checkVerification( HttpMethod.POST ),
        showVerifications( HttpMethod.POST ),
        validateAttributes( HttpMethod.POST ),
        photo( HttpMethod.GET ),
        card( HttpMethod.POST ),;

        private final HttpMethod method;

        HelpdeskAction( final HttpMethod method )
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
        return Optional.of( HelpdeskAction.class );
    }


    static HelpdeskProfile getHelpdeskProfile( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return pwmRequest.getHelpdeskProfile( );
    }

    @Override
    protected void nextStep( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ChaiUnavailableException, ServletException
    {
        forwardToSearchJsp( pwmRequest );
    }

    private static ProcessStatus forwardToSearchJsp( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, ServletException, IOException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskProfile( pwmRequest );
        pwmRequest.setAttribute( PwmRequestAttribute.HelpdeskVerificationEnabled, !helpdeskProfile.readRequiredVerificationMethods().isEmpty() );
        pwmRequest.forwardToJsp( JspUrl.HELPDESK_SEARCH );
        return ProcessStatus.Halt;
    }

    @Override
    public ProcessStatus preProcessCheck( final PwmRequest pwmRequest ) throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();

        if ( !pwmRequest.isAuthenticated() )
        {
            pwmRequest.respondWithError( PwmError.ERROR_AUTHENTICATION_REQUIRED.toInfo() );
            return ProcessStatus.Halt;
        }

        if ( !pwmDomain.getConfig().readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE ) )
        {
            pwmRequest.respondWithError( new ErrorInformation(
                    PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                    "Setting " + PwmSetting.HELPDESK_ENABLE.toMenuLocationDebug( null, null ) + " is not enabled." )
            );
            return ProcessStatus.Halt;
        }

        final HelpdeskProfile helpdeskProfile = pwmRequest.getHelpdeskProfile( );
        if ( helpdeskProfile == null )
        {
            pwmRequest.respondWithError( PwmError.ERROR_UNAUTHORIZED.toInfo() );
            return ProcessStatus.Halt;
        }

        // verify the chaiProvider is available - ie, password is supplied, proxy available etc.
        // we do this now so redirects can handle properly instead of during a later rest request.
        final UserIdentity loggedInUser = pwmRequest.getPwmSession().getUserInfo().getUserIdentity();
        HelpdeskServletUtil.getChaiUser( pwmRequest, loggedInUser ).getChaiProvider();

        EnumUtil.readEnumFromString( HelpdeskAction.class, pwmRequest.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) )
                .ifPresent( ACTION_STATS::increment );

        System.out.println( ACTION_STATS.debugStats( PwmConstants.DEFAULT_LOCALE  ) );


        return ProcessStatus.Continue;
    }

    @ActionHandler( action = "helpdeskClientData" )
    public ProcessStatus restHelpdeskClientData( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskProfile( pwmRequest );
        final HelpdeskClientData returnValues = HelpdeskClientData.fromConfig( helpdeskProfile, pwmRequest.getLocale() );

        final RestResultBean<?> restResultBean = RestResultBean.withData( returnValues, HelpdeskClientData.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "card" )
    public ProcessStatus processCardRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskProfile( pwmRequest );
        final UserIdentity userIdentity = readUserKeyRequestParameter( pwmRequest );

        HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, userIdentity );

        final HelpdeskUserCard helpdeskCardInfoBean = HelpdeskServletUtil.makeHelpdeskCardInfo( pwmRequest, helpdeskProfile, userIdentity );

        final RestResultBean<HelpdeskUserCard> restResultBean = RestResultBean.withData( helpdeskCardInfoBean, HelpdeskUserCard.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "search" )
    public ProcessStatus restSearchRequest(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException, IOException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskProfile( pwmRequest );
        final HelpdeskSearchRequest searchRequest = pwmRequest.readBodyAsJsonObject( HelpdeskSearchRequest.class );

        final CacheKey cacheKey = CacheKey.newKey(
                HelpdeskServletUtil.class,
                pwmRequest.getUserInfoIfLoggedIn(),
                HelpdeskServlet.HelpdeskAction.search + JsonFactory.get().serialize( searchRequest ) );

        final CachePolicy policy = CachePolicy.makePolicyWithExpiration( TimeDuration.ZERO );

        final CacheService cacheService = pwmRequest.getPwmDomain().getCacheService();

        final HelpdeskSearchResponse searchResultsBean = cacheService.get(
                cacheKey,
                policy,
                HelpdeskSearchResponse.class,
                () -> searchImpl( pwmRequest, helpdeskProfile, searchRequest ) );

        final RestResultBean<HelpdeskSearchResponse> restResultBean = RestResultBean.withData( searchResultsBean, HelpdeskSearchResponse.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    private static HelpdeskSearchResponse searchImpl(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final HelpdeskSearchRequest searchRequest
    )
            throws PwmUnrecoverableException
    {

        final List<FormConfiguration> searchForm = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_RESULT_FORM );
        final int maxResults = ( int ) helpdeskProfile.readSettingAsLong( PwmSetting.HELPDESK_RESULT_LIMIT );

        final SearchRequestBean.SearchMode searchMode = searchRequest.mode() == null
                ? SearchRequestBean.SearchMode.simple
                : searchRequest.mode();

        switch ( searchMode )
        {
            case simple ->
                    {
                        if ( StringUtil.isEmpty( searchRequest.username() ) )
                        {
                            return HelpdeskSearchResponse.emptyResult();
                        }
                    }
            case advanced ->
                    {
                        if ( CollectionUtil.isEmpty( searchRequest.searchValues() ) )
                        {
                            return HelpdeskSearchResponse.emptyResult();
                        }
                    }
            default -> PwmUtil.unhandledSwitchStatement( searchMode );
        }

        final UserSearchService userSearchService = pwmRequest.getPwmDomain().getUserSearchEngine();

        final SearchConfiguration searchConfiguration = HelpdeskServletUtil.makeSearchConfiguration(
                pwmRequest,
                searchRequest,
                searchMode );


        final Locale locale = pwmRequest.getLocale();
        final UserSearchResults results = userSearchService.performMultiUserSearchFromForm( locale, searchConfiguration, maxResults, searchForm, pwmRequest.getLabel() );
        final boolean sizeExceeded = results.isSizeExceeded();

        final List<Map<String, Object>> jsonResults = results.resultsAsJsonOutput(
                userIdentity -> HelpdeskServletUtil.obfuscateUserIdentity( pwmRequest, userIdentity ),
                pwmRequest.getUserInfoIfLoggedIn() );

        return new HelpdeskSearchResponse( jsonResults, sizeExceeded );
    }

    @ActionHandler( action = "validateOtpCode" )
    public ProcessStatus restValidateOtpCodeRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final HelpdeskVerificationRequest helpdeskVerificationRequest = JsonFactory.get().deserialize(
                pwmRequest.readRequestBodyAsString(),
                HelpdeskVerificationRequest.class
        );

        return HelpdeskServletUtil.validateOtpCodeImpl( pwmRequest, helpdeskVerificationRequest );
    }

    @ActionHandler( action = "sendVerificationToken" )
    public ProcessStatus restSendVerificationTokenRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final HelpdeskSendVerificationTokenRequest helpdeskVerificationRequest = JsonFactory.get().deserialize(
                pwmRequest.readRequestBodyAsString(),
                HelpdeskSendVerificationTokenRequest.class
        );

        HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, helpdeskVerificationRequest.readTargetUser( pwmRequest ) );

        HelpdeskServletUtil.sendVerificationTokenRequestImpl( pwmRequest, helpdeskVerificationRequest );

        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "verifyVerificationToken" )
    public ProcessStatus restVerifyVerificationTokenRequest(
            final PwmRequest pwmRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final HelpdeskVerificationRequest helpdeskVerificationRequest =
                pwmRequest.readBodyAsJsonObject( HelpdeskVerificationRequest.class );

        HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, helpdeskVerificationRequest.readTargetUser( pwmRequest ) );

        return HelpdeskServletUtil.verifyVerificationTokenRequestImpl( pwmRequest, helpdeskVerificationRequest );
    }

    @ActionHandler( action = "checkVerification" )
    public ProcessStatus restCheckVerification( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = getHelpdeskProfile( pwmRequest );
        final var checkRequest = pwmRequest.readBodyAsJsonObject( HelpdeskCheckVerificationRequest.class );

        final UserIdentity userIdentity = checkRequest.readTargetUser( pwmRequest );
        HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, userIdentity );

        final HelpdeskClientState state = checkRequest.readVerificationState( pwmRequest );
        final boolean passed = HelpdeskServletUtil.checkIfRequiredVerificationPassed( pwmRequest, userIdentity, state );
        final HelpdeskVerificationOptions optionsBean = HelpdeskVerificationOptions.fromConfig( pwmRequest, helpdeskProfile, userIdentity );
        final HelpdeskCheckVerificationResponse responseBean = new HelpdeskCheckVerificationResponse(
                passed,
                optionsBean );
        final RestResultBean<HelpdeskCheckVerificationResponse> restResultBean = RestResultBean.withData( responseBean, HelpdeskCheckVerificationResponse.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }


    @ActionHandler( action = "showVerifications" )
    public ProcessStatus restShowVerifications( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException, ChaiOperationException
    {
        final var checkRequest = pwmRequest.readBodyAsJsonObject( HelpdeskCheckVerificationRequest.class );
        final HelpdeskClientState state = checkRequest.readVerificationState( pwmRequest );

        record ShowVerificationResponse(
                List<HelpdeskVerificationDisplayRecord> records
        )
        {
        }

        final ShowVerificationResponse response = new ShowVerificationResponse( state.asDisplayableValidationRecords( pwmRequest.getPwmRequestContext() ) );
        final RestResultBean<ShowVerificationResponse> restResultBean = RestResultBean.withData( response, ShowVerificationResponse.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    @ActionHandler( action = "validateAttributes" )
    public ProcessStatus restValidateAttributes( final PwmRequest pwmRequest )
            throws IOException, PwmUnrecoverableException
    {
        final HelpdeskVerificationRequest helpdeskVerificationRequest = JsonFactory.get().deserialize(
                pwmRequest.readRequestBodyAsString(),
                HelpdeskVerificationRequest.class
        );

        return HelpdeskServletUtil.validateAttributesImpl( pwmRequest, helpdeskVerificationRequest );
    }

    @ActionHandler( action = "photo" )
    public ProcessStatus processUserPhotoImageRequest( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final UserIdentity userIdentity = readUserKeyRequestParameter( pwmRequest );
        final HelpdeskProfile helpdeskProfile = getHelpdeskProfile( pwmRequest );
        HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, userIdentity  );
        final PhotoDataReader photoDataReader = photoDataReader( pwmRequest, helpdeskProfile, userIdentity );

        LOGGER.debug( pwmRequest, () -> "received user photo request to view user " + userIdentity.toString() );

        final Callable<Optional<PhotoDataBean>> callablePhotoReader = photoDataReader::readPhotoData;
        PhotoDataReader.servletRespondWithPhoto( pwmRequest, callablePhotoReader );
        return ProcessStatus.Halt;
    }

    static UserIdentity readUserKeyRequestParameter( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException
    {
        final String userKey = pwmRequest.readParameterAsString( PwmConstants.PARAM_USERKEY, PwmHttpRequestWrapper.Flag.BypassValidation );
        if ( StringUtil.isEmpty( userKey ) )
        {

            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing" );
            throw new PwmUnrecoverableException( errorInformation );
        }
        return HelpdeskServletUtil.readUserIdentity( pwmRequest, userKey );
    }

    static PhotoDataReader photoDataReader( final PwmRequest pwmRequest, final HelpdeskProfile helpdeskProfile, final UserIdentity userIdentity )
            throws PwmUnrecoverableException
    {

        final boolean enabled = helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE_PHOTOS );
        final PhotoDataReader.Settings settings = PhotoDataReader.Settings.builder()
                .enabled( enabled )
                .photoPermissions( null )
                .chaiProvider( HelpdeskServletUtil.getChaiUser( pwmRequest, userIdentity ).getChaiProvider() )
                .build();

        return new PhotoDataReader( pwmRequest, settings, userIdentity );
    }
}
