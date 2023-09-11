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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiException;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ProcessStatus;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.http.servlet.helpdesk.data.HelpdeskSearchRequest;
import password.pwm.http.servlet.peoplesearch.PhotoDataReader;
import password.pwm.http.servlet.peoplesearch.SearchRequestBean;
import password.pwm.i18n.Error;
import password.pwm.i18n.Message;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.permission.UserPermissionType;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.otp.OTPUserRecord;
import password.pwm.svc.secure.DomainSecureService;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.svc.token.TokenService;
import password.pwm.svc.token.TokenUtil;
import password.pwm.user.UserInfo;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.PwmTimeUtil;
import password.pwm.util.java.PwmUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class HelpdeskServletUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskServletUtil.class );

    private HelpdeskServletUtil()
    {
    }

    static String makeAdvancedSearchFilter( final DomainConfig domainConfig, final HelpdeskProfile helpdeskProfile )
    {
        final String configuredFilter = helpdeskProfile.readSettingAsString( PwmSetting.HELPDESK_SEARCH_FILTER );
        if ( configuredFilter != null && !configuredFilter.isEmpty() )
        {
            return configuredFilter;
        }

        final List<String> defaultObjectClasses =
                domainConfig.readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES );
        final List<FormConfiguration> searchAttributes =
                helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_FORM );
        final StringBuilder filter = new StringBuilder();

        //open AND clause for objectclasses and attributes
        filter.append( "(&" );

        for ( final String objectClass : defaultObjectClasses )
        {
            filter.append( "(objectClass=" ).append( objectClass ).append( ')' );
        }

        // open OR clause for attributes
        filter.append( "(|" );

        for ( final FormConfiguration formConfiguration : searchAttributes )
        {
            if ( formConfiguration != null && formConfiguration.getName() != null )
            {
                final String searchAttribute = formConfiguration.getName();
                filter.append( '(' )
                        .append( searchAttribute )
                        .append( "=*" )
                        .append( PwmConstants.VALUE_REPLACEMENT_USERNAME )
                        .append( "*)" );
            }
        }

        // close OR clause
        filter.append( ')' );

        // close AND clause
        filter.append( ')' );
        return filter.toString();
    }

    static String makeAdvancedSearchFilter(
            final DomainConfig domainConfig,
            final HelpdeskProfile helpdeskProfile,
            final Map<String, String> attributesInSearchRequest
    )
    {
        final List<String> defaultObjectClasses =
                domainConfig.readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES );
        final List<FormConfiguration> searchAttributes =
                helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_FORM );
        return makeAdvancedSearchFilter( defaultObjectClasses, searchAttributes, attributesInSearchRequest );
    }

    public static String makeAdvancedSearchFilter(
            final List<String> defaultObjectClasses,
            final List<FormConfiguration> searchAttributes,
            final Map<String, String> attributesInSearchRequest
    )
    {
        final StringBuilder filter = new StringBuilder();

        //open AND clause for objectclasses and attributes
        filter.append( "(&" );

        for ( final String objectClass : defaultObjectClasses )
        {
            filter.append( "(objectClass=" ).append( objectClass ).append( ')' );
        }

        // open AND clause for attributes
        filter.append( "(&" );

        for ( final FormConfiguration formConfiguration : searchAttributes )
        {
            if ( formConfiguration != null && formConfiguration.getName() != null )
            {
                final String searchAttribute = formConfiguration.getName();
                final String value = attributesInSearchRequest.get( searchAttribute );
                if ( StringUtil.notEmpty( value ) )
                {
                    filter.append( '(' ).append( searchAttribute ).append( '=' );

                    if ( formConfiguration.getType() == FormConfiguration.Type.select )
                    {
                        // value is specified by admin, so wildcards are not required
                        filter.append( '%' ).append( searchAttribute ).append( "%)" );
                    }
                    else
                    {
                        filter.append( "*%" ).append( searchAttribute ).append( "%*)" );
                    }
                }
            }
        }

        // close OR clause
        filter.append( ')' );

        // close AND clause
        filter.append( ')' );
        return filter.toString();
    }

    static void checkIfUserIdentityViewable(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final String filterSetting = makeAdvancedSearchFilter( pwmRequest.getDomainConfig(), pwmRequest.getHelpdeskProfile() );
        String filterString = filterSetting.replace( PwmConstants.VALUE_REPLACEMENT_USERNAME, "*" );
        while ( filterString.contains( "**" ) )
        {
            filterString = filterString.replace( "**", "*" );
        }

        final UserPermission userPermission = new UserPermission(
                UserPermissionType.ldapQuery,
                userIdentity.getLdapProfileID(),
                filterString,
                null );

        final boolean match = UserPermissionUtility.testUserPermission(
                pwmRequest.getPwmRequestContext(),
                userIdentity,
                userPermission
        );

        if ( !match )
        {
            throw new PwmUnrecoverableException(
                    PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                    "requested user is not available within configured search filter" );
        }
    }

    static void verifyIfRequiredVerificationPassed(
            final PwmRequest pwmRequest,
            final UserIdentity targetUser,
            final HelpdeskClientState verificationStateBean
    )
            throws PwmUnrecoverableException
    {
        if ( !HelpdeskServletUtil.checkIfRequiredVerificationPassed( pwmRequest, targetUser, verificationStateBean ) )
        {
            final String errorMsg = "selected user has not been verified";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    static boolean checkIfRequiredVerificationPassed(
            final PwmRequest pwmRequest,
            final UserIdentity targetUser,
            final HelpdeskClientState verificationStateBean
    )
            throws PwmUnrecoverableException
    {
        final HelpdeskProfile helpdeskProfile = pwmRequest.getHelpdeskProfile();
        final Collection<IdentityVerificationMethod> requiredMethods =
                helpdeskProfile.readRequiredVerificationMethods();


        verifyTargetNotSelf( pwmRequest, targetUser );

        if ( CollectionUtil.isEmpty( requiredMethods ) )
        {
            return true;
        }

        for ( final IdentityVerificationMethod method : requiredMethods )
        {
            if ( verificationStateBean.hasRecord( targetUser, method ) )
            {
                return true;
            }
        }

        return false;
    }

    private static void verifyTargetNotSelf(
            final PwmRequest pwmRequest,
            final UserIdentity targetUser
    )
            throws PwmUnrecoverableException
    {
        final UserIdentity actorUserIdentity =
                pwmRequest.getUserInfoIfLoggedIn().canonicalized( pwmRequest.getLabel(),
                        pwmRequest.getPwmApplication() );

        if ( actorUserIdentity.canonicalEquals( pwmRequest.getLabel(), targetUser, pwmRequest.getPwmApplication() ) )
        {
            final String errorMsg = "cannot select self";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    static void sendUnlockNoticeEmail(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity,
            final ChaiUser chaiUser

    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final DomainConfig config = pwmRequest.getDomainConfig();
        final Locale locale = pwmRequest.getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_HELPDESK_UNLOCK,
                locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, () -> "skipping send helpdesk unlock notice email for '" + userIdentity
                    + "' no" + " email configured" );
            return;
        }

        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                userIdentity,
                chaiUser.getChaiProvider()
        );

        final MacroRequest macroRequest = getTargetUserMacroRequest( pwmRequest, userIdentity );

        pwmDomain.getPwmApplication().getEmailQueue().submitEmail(
                configuredEmailSetting,
                userInfo,
                macroRequest
        );
    }

    static ChaiUser getChaiUser(
            final PwmRequest pwmRequest,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final HelpdeskProfile helpdeskProfile = pwmRequest.getHelpdeskProfile();
        final boolean useProxy = helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_USE_PROXY );
        return useProxy
                ? pwmRequest.getPwmDomain().getProxiedChaiUser( pwmRequest.getLabel(), userIdentity )
                : pwmRequest.getClientConnectionHolder().getActor( userIdentity );
    }

    static UserInfo getTargetUserInfo(
            final PwmRequest pwmRequest,
            final UserIdentity targetUserIdentity
    )
            throws PwmUnrecoverableException
    {
        return UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                targetUserIdentity,
                getChaiUser( pwmRequest, targetUserIdentity ).getChaiProvider()
        );
    }

    static String obfuscateUserIdentity( final PwmRequest pwmRequest, final UserIdentity userIdentity )
    {
        try
        {
            return pwmRequest.encryptObjectToString( userIdentity );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new IllegalStateException( "unexpected error encoding userIdentity: " + e.getMessage() );
        }
    }


    public static UserIdentity readUserIdentity( final PwmRequest pwmRequest, final String input )
            throws PwmUnrecoverableException
    {
        try
        {
            final UserIdentity userIdentity = pwmRequest.decryptObject( input, UserIdentity.class );
            HelpdeskServletUtil.checkIfUserIdentityViewable( pwmRequest, userIdentity );
            return userIdentity;
        }
        catch ( final Exception e )
        {
            LOGGER.debug( pwmRequest, () -> "error reading userKey from request: " + e.getMessage() );
            throw new PwmUnrecoverableException( PwmError.ERROR_MISSING_PARAMETER,
                    "unreadable userKey parameter: " + e.getMessage() );
        }

    }

    static MacroRequest getTargetUserMacroRequest(
            final PwmRequest pwmRequest,
            final UserIdentity targetUserIdentity
    )
            throws PwmUnrecoverableException
    {
        if ( targetUserIdentity != null )
        {
            final UserInfo targetUserInfo = getTargetUserInfo( pwmRequest, targetUserIdentity );
            return MacroRequest.forTargetUser(
                    pwmRequest.getPwmApplication(),
                    pwmRequest.getLabel(),
                    pwmRequest.getPwmSession().getUserInfo(),
                    pwmRequest.getPwmSession().getLoginInfoBean(),
                    targetUserInfo );
        }

        return MacroRequest.forUser(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getPwmSession().getUserInfo(),
                pwmRequest.getPwmSession().getLoginInfoBean() );
    }

    static SearchConfiguration makeSearchConfiguration(
            final PwmRequest pwmRequest,
            final HelpdeskSearchRequest searchRequest,
            final SearchRequestBean.SearchMode searchMode
    )
            throws PwmUnrecoverableException
    {
        final HelpdeskProfile helpdeskProfile = HelpdeskServlet.getHelpdeskProfile( pwmRequest );
        final boolean useProxy = helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_USE_PROXY );

        final SearchConfiguration searchConfiguration;
        {
            final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();
            builder.contexts( helpdeskProfile.readSettingAsStringArray( PwmSetting.HELPDESK_SEARCH_BASE ) );
            builder.enableContextValidation( false );
            builder.enableValueEscaping( true );
            builder.enableSplitWhitespace( true );

            if ( !useProxy )
            {
                final UserIdentity loggedInUser = pwmRequest.getPwmSession().getUserInfo().getUserIdentity();
                builder.ldapProfile( loggedInUser.getLdapProfileID() );
                builder.chaiProvider( HelpdeskServletUtil.getChaiUser( pwmRequest, loggedInUser ).getChaiProvider() );
            }

            switch ( searchMode )
            {
                case simple ->
                        {
                            builder.username( searchRequest.username() );
                            builder.filter( HelpdeskServletUtil.makeAdvancedSearchFilter( pwmRequest.getDomainConfig(),
                                    helpdeskProfile ) );
                        }
                case advanced ->
                        {
                            final Map<FormConfiguration, String> formValues = new LinkedHashMap<>();
                            final Map<String, String> requestSearchValues = SearchRequestBean.searchValueToMap( searchRequest.searchValues() );

                            for ( final FormConfiguration formConfiguration : helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_FORM ) )
                            {
                                final String attribute = formConfiguration.getName();
                                final String value = requestSearchValues.get( attribute );
                                if ( StringUtil.notEmpty( value ) )
                                {
                                    formValues.put( formConfiguration, value );
                                }
                            }

                            builder.formValues( formValues );
                            builder.filter( HelpdeskServletUtil.makeAdvancedSearchFilter( pwmRequest.getDomainConfig(),
                                    helpdeskProfile, requestSearchValues ) );

                        }
                default -> PwmUtil.unhandledSwitchStatement( searchMode );
            }

            searchConfiguration = builder.build();
        }
        return searchConfiguration;
    }

    static ProcessStatus validateOtpCodeImpl(
            final PwmRequest pwmRequest,
            final HelpdeskVerificationRequest helpdeskVerificationRequest
    )
            throws IOException, PwmUnrecoverableException, ServletException
    {
        final HelpdeskProfile helpdeskProfile = HelpdeskServlet.getHelpdeskProfile( pwmRequest );

        final String userKey = helpdeskVerificationRequest.userKey();
        if ( StringUtil.isEmpty( userKey ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing" );
            pwmRequest.respondWithError( errorInformation, false );
            return ProcessStatus.Halt;
        }
        final UserIdentity userIdentity = HelpdeskServletUtil.readUserIdentity( pwmRequest, userKey );

        if ( !helpdeskProfile.readOptionalVerificationMethods().contains( IdentityVerificationMethod.OTP ) )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, "password otp verification request, but otp verify is not enabled" );
            LOGGER.error( pwmRequest, errorInformation );
            pwmRequest.respondWithError( errorInformation );
            return ProcessStatus.Halt;
        }

        final String code = helpdeskVerificationRequest.code();
        final OTPUserRecord otpUserRecord = pwmRequest.getPwmDomain().getOtpService().readOTPUserConfiguration( pwmRequest.getLabel(), userIdentity );
        try
        {
            final boolean passed = pwmRequest.getPwmDomain().getOtpService().validateToken(
                    pwmRequest.getLabel(),
                    userIdentity,
                    otpUserRecord,
                    code,
                    false
            );

            final HelpdeskClientState inputState = HelpdeskClientState.fromClientString(
                    pwmRequest,
                    helpdeskVerificationRequest.verificationState()
            );

            final HelpdeskClientState outputState;
            if ( passed )
            {
                submitAuditEvent( pwmRequest, userIdentity, AuditEvent.HELPDESK_VERIFY_OTP, null );
                StatisticsClient.incrementStat( pwmRequest, Statistic.HELPDESK_VERIFY_OTP );
                outputState = inputState.addRecord( userIdentity, IdentityVerificationMethod.OTP );
            }
            else
            {
                submitAuditEvent( pwmRequest, userIdentity, AuditEvent.HELPDESK_VERIFY_OTP_INCORRECT, null );
                outputState = inputState;
            }

            final String userMessage = passed
                    ? LocaleHelper.getLocalizedMessage( Message.Success_Unknown, pwmRequest )
                    : LocaleHelper.getLocalizedMessage( Error.Error_WrongOtpToken, pwmRequest );

            return HelpdeskServletUtil.outputVerificationResponseBean( pwmRequest, passed, userMessage, outputState );
        }
        catch ( final PwmOperationalException e )
        {
            pwmRequest.outputJsonResult( RestResultBean.fromError( e.getErrorInformation(), pwmRequest ) );
        }
        return ProcessStatus.Halt;
    }

    static ProcessStatus validateAttributesImpl(
            final PwmRequest pwmRequest,
            final HelpdeskVerificationRequest helpdeskVerificationRequest )
            throws IOException, PwmUnrecoverableException
    {
        final HelpdeskProfile helpdeskProfile = HelpdeskServlet.getHelpdeskProfile( pwmRequest );

        final UserIdentity userIdentity = HelpdeskServletUtil.readUserIdentity( pwmRequest,
                helpdeskVerificationRequest.userKey() );

        String userMessage = LocaleHelper.getLocalizedMessage( Error.Error_TokenIncorrect, pwmRequest );
        final List<String> verifiedAttributes = new ArrayList<>();
        boolean passed = false;
        {
            final List<FormConfiguration> verificationForms = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_VERIFICATION_FORM );
            if ( verificationForms == null || verificationForms.isEmpty() )
            {
                final ErrorInformation errorInformation = new ErrorInformation(
                        PwmError.ERROR_INVALID_CONFIG,
                        "attempt to verify ldap attributes with no ldap verification attributes configured"
                );
                throw new PwmUnrecoverableException( errorInformation );
            }

            final ChaiUser chaiUser = HelpdeskServletUtil.getChaiUser( pwmRequest, userIdentity );

            int successCount = 0;
            for ( final FormConfiguration formConfiguration : verificationForms )
            {
                final String name = formConfiguration.getName();
                final String suppliedValue = helpdeskVerificationRequest.attributeData().get( name );
                if ( suppliedValue != null )
                {
                    try
                    {
                        if ( chaiUser.compareStringAttribute( name, suppliedValue ) )
                        {
                            verifiedAttributes.add( name );
                            successCount++;
                        }
                        else
                        {
                            userMessage = LocaleHelper.getLocalizedMessage( Error.Error_FieldBadConfirm, pwmRequest, name );
                        }
                    }
                    catch ( final ChaiException e )
                    {
                        LOGGER.error( pwmRequest, () -> "error comparing ldap attribute during verification " + e.getMessage() );
                    }
                }
            }
            if ( successCount == verificationForms.size() )
            {
                passed = true;
                userMessage = LocaleHelper.getLocalizedMessage( Message.Success_Unknown, pwmRequest );
            }
        }

        final HelpdeskClientState inputState = HelpdeskClientState.fromClientString(
                pwmRequest,
                helpdeskVerificationRequest.verificationState()
        );


        final HelpdeskClientState outputState;
        if ( passed )
        {
            final String message = "verified attributes: " + StringUtil.collectionToString( verifiedAttributes );
            submitAuditEvent( pwmRequest, userIdentity, AuditEvent.HELPDESK_VERIFY_ATTRIBUTES, message );
            outputState = inputState.addRecord( userIdentity, IdentityVerificationMethod.ATTRIBUTES );
        }
        else
        {
            submitAuditEvent( pwmRequest, userIdentity, AuditEvent.HELPDESK_VERIFY_ATTRIBUTES_INCORRECT, null );
            outputState = inputState;
        }

        return outputVerificationResponseBean( pwmRequest, passed, userMessage, outputState );
    }


    static ProcessStatus outputVerificationResponseBean(
            final PwmRequest pwmRequest,
            final boolean passed,
            final String userMessage,
            final HelpdeskClientState verificationStateBean
    )
            throws IOException, PwmUnrecoverableException
    {
        // add a delay to prevent continuous checks
        final long delayMs = JavaHelper.silentParseLong( pwmRequest.getDomainConfig().readAppProperty( AppProperty.HELPDESK_VERIFICATION_INVALID_DELAY_MS ), 500 );
        PwmTimeUtil.jitterPause( TimeDuration.of( delayMs, TimeDuration.Unit.MILLISECONDS ), pwmRequest.getPwmDomain().getSecureService(), 0.3f );

        final HelpdeskVerificationResponse responseBean = new HelpdeskVerificationResponse(
                passed,
                userMessage,
                verificationStateBean.toClientString( pwmRequest )
        );
        final RestResultBean<HelpdeskVerificationResponse> restResultBean = RestResultBean.withData( responseBean, HelpdeskVerificationResponse.class );
        pwmRequest.outputJsonResult( restResultBean );
        return ProcessStatus.Halt;
    }

    static void verifyButtonEnabled(
            final PwmRequest pwmRequest,
            final UserIdentity targetUser,
            final HelpdeskDetailButton button
    )
            throws PwmUnrecoverableException
    {
        final HelpdeskUserDetail helpdeskUserDetail =
                HelpdeskUserDetail.makeHelpdeskDetailInfo( pwmRequest, targetUser );

        if ( button == null )
        {
            return;
        }

        if ( !helpdeskUserDetail.visibleButtons().contains( button ) )
        {
            final String errorMsg = "request for action '" + button + "' but button is not visible";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        if ( !helpdeskUserDetail.enabledButtons().contains( button ) )
        {
            final String errorMsg = "request for action '" + button + "' but button is not enabled";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }

    static void submitAuditEvent(
            final PwmRequest pwmRequest,
            final UserIdentity targetUser,
            final AuditEvent auditEvent,
            final String message
    )
            throws PwmUnrecoverableException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();

        final HelpdeskAuditRecord helpdeskAuditRecord = AuditRecordFactory.make( pwmRequest ).createHelpdeskAuditRecord(
                auditEvent,
                pwmSession.getUserInfo().getUserIdentity(),
                message,
                targetUser,
                pwmSession.getSessionStateBean().getSrcAddress(),
                pwmSession.getSessionStateBean().getSrcHostname()
        );

        AuditServiceClient.submit( pwmRequest, helpdeskAuditRecord );
    }



    static ProcessStatus verifyVerificationTokenRequestImpl(
            final PwmRequest pwmRequest,
            final HelpdeskVerificationRequest helpdeskVerificationRequest
    )
            throws IOException, PwmUnrecoverableException
    {

        final String token = helpdeskVerificationRequest.code();

        final DomainSecureService domainSecureService = pwmRequest.getPwmDomain().getSecureService();
        final HelpdeskVerificationRequest.TokenData tokenData = domainSecureService.decryptObject(
                helpdeskVerificationRequest.tokenData(),
                HelpdeskVerificationRequest.TokenData.class
        );

        final UserIdentity userIdentity = helpdeskVerificationRequest.readTargetUser( pwmRequest );

        final TimeDuration maxTokenAge = TimeDuration.of(
                Long.parseLong( pwmRequest.getDomainConfig().readAppProperty( AppProperty.HELPDESK_TOKEN_MAX_AGE ) ),
                TimeDuration.Unit.SECONDS
        );
        final Instant maxTokenAgeTimestamp = Instant.ofEpochMilli( System.currentTimeMillis() - maxTokenAge.asMillis() );
        if ( tokenData.issueDate().isBefore( maxTokenAgeTimestamp ) )
        {
            final String errorMsg = "token is older than maximum issue time (" + maxTokenAge.asCompactString() + ")";
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TOKEN_EXPIRED, errorMsg ) );
        }

        final boolean passed = tokenData.token().equals( token );

        final HelpdeskClientState inputState = helpdeskVerificationRequest.readVerificationState( pwmRequest );

        final HelpdeskClientState outputState;
        if ( passed )
        {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final HelpdeskAuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_VERIFY_TOKEN,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            AuditServiceClient.submit( pwmRequest, auditRecord );
            outputState = inputState.addRecord( userIdentity, IdentityVerificationMethod.TOKEN );
        }
        else
        {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            final HelpdeskAuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createHelpdeskAuditRecord(
                    AuditEvent.HELPDESK_VERIFY_TOKEN_INCORRECT,
                    pwmSession.getUserInfo().getUserIdentity(),
                    null,
                    userIdentity,
                    pwmSession.getSessionStateBean().getSrcAddress(),
                    pwmSession.getSessionStateBean().getSrcHostname()
            );
            AuditServiceClient.submit( pwmRequest, auditRecord );
            outputState = inputState;
        }

        final String userMessage = passed
                ? LocaleHelper.getLocalizedMessage( Message.Success_Unknown, pwmRequest )
                : LocaleHelper.getLocalizedMessage( Error.Error_TokenIncorrect, pwmRequest );

        return HelpdeskServletUtil.outputVerificationResponseBean( pwmRequest, passed, userMessage, outputState );
    }

    static void sendVerificationTokenRequestImpl(
            final PwmRequest pwmRequest,
            final HelpdeskSendVerificationTokenRequest helpdeskVerificationRequest
    )
            throws IOException, PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final DomainConfig config = pwmRequest.getDomainConfig();

        final UserIdentity targetUserIdentity = helpdeskVerificationRequest.readTargetUser( pwmRequest );
        final UserInfo targetUserInfo = HelpdeskServletUtil.getTargetUserInfo( pwmRequest, targetUserIdentity );

        final String requestedTokenID = helpdeskVerificationRequest.id();

        final TokenDestinationItem tokenDestinationItem;
        {
            final List<TokenDestinationItem> items = TokenUtil.figureAvailableTokenDestinations(
                    pwmRequest.getPwmDomain(),
                    pwmRequest.getLabel(),
                    pwmRequest.getLocale(),
                    targetUserInfo,
                    MessageSendMethod.CHOICE_SMS_EMAIL  );

            final Optional<TokenDestinationItem> selectedTokenDest = TokenDestinationItem.tokenDestinationItemForID( items, requestedTokenID );

            if ( selectedTokenDest.isPresent() )
            {
                tokenDestinationItem = selectedTokenDest.get();
            }
            else
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_INTERNAL, "unknown token id '" + requestedTokenID + "' in request" );
            }
        }

        final MacroRequest macroRequest = HelpdeskServletUtil.getTargetUserMacroRequest( pwmRequest, targetUserIdentity );
        final String configuredTokenString = config.readAppProperty( AppProperty.HELPDESK_TOKEN_VALUE );
        final String tokenKey = macroRequest.expandMacros( configuredTokenString );
        final EmailItemBean emailItemBean = config.readSettingAsEmail( PwmSetting.EMAIL_HELPDESK_TOKEN, pwmRequest.getLocale() );

        LOGGER.debug( pwmRequest, () -> "generated token code for " + targetUserIdentity.toDelimitedKey() );

        final String smsMessage = config.readSettingAsLocalizedString( PwmSetting.SMS_HELPDESK_TOKEN_TEXT, pwmRequest.getLocale() );

        try
        {
            TokenService.TokenSender.sendToken(
                    TokenService.TokenSendInfo.builder()
                            .pwmDomain( pwmRequest.getPwmDomain() )
                            .userInfo( targetUserInfo )
                            .macroRequest( macroRequest )
                            .configuredEmailSetting( emailItemBean )
                            .tokenDestinationItem( tokenDestinationItem )
                            .smsMessage( smsMessage )
                            .tokenKey( tokenKey )
                            .sessionLabel( pwmRequest.getLabel() )
                            .build()
            );
        }
        catch ( final PwmException e )
        {
            LOGGER.error( pwmRequest, e.getErrorInformation() );
            pwmRequest.outputJsonResult( RestResultBean.fromError( e.getErrorInformation(), pwmRequest ) );
            return;
        }

        final DomainSecureService domainSecureService = pwmRequest.getPwmDomain().getSecureService();
        final String newTokenData = domainSecureService.encryptObjectToString( new HelpdeskVerificationRequest.TokenData(
                tokenKey,
                Instant.now() ) );

        StatisticsClient.incrementStat( pwmRequest, Statistic.HELPDESK_TOKENS_SENT );

        final HelpdeskVerificationRequest helpdeskVerificationRequestBean = new HelpdeskVerificationRequest(
                tokenDestinationItem.getDisplay(),
                helpdeskVerificationRequest.userKey(),
                Map.of(),
                null,
                newTokenData,
                null );


        final RestResultBean<HelpdeskVerificationRequest> restResultBean = RestResultBean.withData( helpdeskVerificationRequestBean, HelpdeskVerificationRequest.class );
        pwmRequest.outputJsonResult( restResultBean );
        LOGGER.debug( pwmRequest, () -> "helpdesk operator "
                + pwmRequest.getUserInfoIfLoggedIn().toDisplayString()
                + " issued token for verification against user "
                + targetUserIdentity.toDisplayString()
                + " sent to destination(s) "
                + tokenDestinationItem.getDisplay()
                + " (" + TimeDuration.fromCurrent( startTime ).asCompactString() + ")" );

    }

    static HelpdeskUserCard makeHelpdeskCardInfo(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        LOGGER.trace( pwmRequest, () -> "beginning to assemble card data report for user " + userIdentity );
        final UserInfo userInfo = HelpdeskServletUtil.getTargetUserInfo( pwmRequest, userIdentity );

        final String userKey = HelpdeskServletUtil.obfuscateUserIdentity( pwmRequest, userIdentity );

        final PhotoDataReader photoDataReader = HelpdeskServlet.photoDataReader( pwmRequest, helpdeskProfile, userIdentity );
        final String optionalPhotoUrl = photoDataReader.figurePhotoURL().orElse( null );

        final List<String> displayLines = figureCardDisplayLines( pwmRequest.getPwmDomain(), helpdeskProfile, pwmRequest.getLabel(), userInfo );

        LOGGER.trace( pwmRequest, () -> "completed assembly of card data report for user " + userIdentity,  TimeDuration.fromCurrent( startTime ) );

        return new HelpdeskUserCard( userKey, displayLines, optionalPhotoUrl );
    }


    private static List<String> figureCardDisplayLines(
            final PwmDomain pwmDomain,
            final HelpdeskProfile helpdeskProfile,
            final SessionLabel sessionLabel,
            final UserInfo userInfo
    )
    {
        final List<String> displayLabels = new ArrayList<>();
        final List<String> displayStringSettings = helpdeskProfile.readSettingAsStringArray( PwmSetting.HELPDESK_DISPLAY_NAMES_CARD_LABELS );
        if ( displayStringSettings != null )
        {
            final MacroRequest macroRequest = MacroRequest.forUser( pwmDomain.getPwmApplication(), sessionLabel, userInfo, null );
            for ( final String displayStringSetting : displayStringSettings )
            {
                final String displayLabel = macroRequest.expandMacros( displayStringSetting );
                displayLabels.add( displayLabel );
            }
        }
        return List.copyOf( displayLabels );
    }

}
