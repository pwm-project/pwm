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
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.user.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.ldap.permission.UserPermissionType;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.AuditServiceClient;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsClient;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HelpdeskServletUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskServletUtil.class );

    static String makeAdvancedSearchFilter( final DomainConfig domainConfig, final HelpdeskProfile helpdeskProfile )
    {
        final String configuredFilter = helpdeskProfile.readSettingAsString( PwmSetting.HELPDESK_SEARCH_FILTER );
        if ( configuredFilter != null && !configuredFilter.isEmpty() )
        {
            return configuredFilter;
        }

        final List<String> defaultObjectClasses = domainConfig.readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES );
        final List<FormConfiguration> searchAttributes = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_FORM );
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
        final List<String> defaultObjectClasses = domainConfig.readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES );
        final List<FormConfiguration> searchAttributes = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_FORM );
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
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final String filterSetting = makeAdvancedSearchFilter( pwmRequest.getDomainConfig(), helpdeskProfile );
        String filterString = filterSetting.replace( PwmConstants.VALUE_REPLACEMENT_USERNAME, "*" );
        while ( filterString.contains( "**" ) )
        {
            filterString = filterString.replace( "**", "*" );
        }

        final UserPermission userPermission = UserPermission.builder()
                .type( UserPermissionType.ldapQuery )
                .ldapQuery( filterString )
                .ldapProfileID( userIdentity.getLdapProfileID() )
                .build();

        final boolean match = UserPermissionUtility.testUserPermission(
                pwmRequest.getPwmRequestContext(),
                userIdentity,
                userPermission
        );

        if ( !match )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                    "requested userDN is not available within configured search filter" )
            );
        }
    }

    static HelpdeskDetailInfoBean processDetailRequestImpl(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final UserIdentity actorUserIdentity = pwmRequest.getUserInfoIfLoggedIn().canonicalized( pwmRequest.getLabel(), pwmRequest.getPwmApplication() );

        if ( actorUserIdentity.canonicalEquals( pwmRequest.getLabel(), userIdentity, pwmRequest.getPwmApplication() ) )
        {
            final String errorMsg = "cannot select self";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        LOGGER.trace( pwmRequest, () -> "helpdesk detail view request for user details of " + userIdentity.toString() + " by actor " + actorUserIdentity );

        final HelpdeskVerificationStateBean verificationStateBean = HelpdeskVerificationStateBean.fromClientString(
                pwmRequest,
                pwmRequest.readParameterAsString( HelpdeskVerificationStateBean.PARAMETER_VERIFICATION_STATE_KEY, PwmHttpRequestWrapper.Flag.BypassValidation )
        );

        if ( !HelpdeskServletUtil.checkIfRequiredVerificationPassed( userIdentity, verificationStateBean, helpdeskProfile ) )
        {
            final String errorMsg = "selected user has not been verified";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        final HelpdeskDetailInfoBean helpdeskDetailInfoBean = HelpdeskDetailInfoBean.makeHelpdeskDetailInfo( pwmRequest, helpdeskProfile, userIdentity );
        final HelpdeskAuditRecord auditRecord = AuditRecordFactory.make( pwmRequest ).createHelpdeskAuditRecord(
                AuditEvent.HELPDESK_VIEW_DETAIL,
                pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
                null,
                userIdentity,
                pwmRequest.getLabel().getSourceAddress(),
                pwmRequest.getLabel().getSourceHostname()
        );
        AuditServiceClient.submit( pwmRequest, auditRecord );

        StatisticsClient.incrementStat( pwmRequest, Statistic.HELPDESK_USER_LOOKUP );
        return helpdeskDetailInfoBean;
    }

    static UserIdentity userIdentityFromMap( final PwmRequest pwmRequest, final Map<String, String> bodyMap ) throws PwmUnrecoverableException
    {
        final String userKey = bodyMap.get( "userKey" );
        if ( userKey == null || userKey.length() < 1 )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_PARAMETER, "userKey parameter is missing" );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return UserIdentity.fromObfuscatedKey( userKey, pwmRequest.getPwmApplication() );
    }


    static boolean checkIfRequiredVerificationPassed(
            final UserIdentity userIdentity,
            final HelpdeskVerificationStateBean verificationStateBean,
            final HelpdeskProfile helpdeskProfile
    )
    {
        final Collection<IdentityVerificationMethod> requiredMethods = helpdeskProfile.readRequiredVerificationMethods();
        if ( CollectionUtil.isEmpty( requiredMethods ) )
        {
            return true;
        }

        for ( final IdentityVerificationMethod method : requiredMethods )
        {
            if ( verificationStateBean.hasRecord( userIdentity, method ) )
            {
                return true;
            }
        }

        return false;
    }

    static void sendUnlockNoticeEmail(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity,
            final ChaiUser chaiUser

    )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmDomain();
        final DomainConfig config = pwmRequest.getDomainConfig();
        final Locale locale = pwmRequest.getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_HELPDESK_UNLOCK, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, () -> "skipping send helpdesk unlock notice email for '" + userIdentity + "' no email configured" );
            return;
        }

        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                userIdentity,
                chaiUser.getChaiProvider()
        );

        final MacroRequest macroRequest = getTargetUserMacroRequest( pwmRequest, helpdeskProfile, userIdentity );

        pwmDomain.getPwmApplication().getEmailQueue().submitEmail(
                configuredEmailSetting,
                userInfo,
                macroRequest
        );
    }

    static ChaiUser getChaiUser(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final boolean useProxy = helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_USE_PROXY );
        return useProxy
                ? pwmRequest.getPwmDomain().getProxiedChaiUser( pwmRequest.getLabel(), userIdentity )
                : pwmRequest.getClientConnectionHolder().getActor( userIdentity );
    }

    static UserInfo getTargetUserInfo(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity targetUserIdentity
    )
            throws PwmUnrecoverableException
    {
        return UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                targetUserIdentity,
                getChaiUser( pwmRequest, helpdeskProfile, targetUserIdentity ).getChaiProvider()
        );
    }

    static MacroRequest getTargetUserMacroRequest(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity targetUserIdentity
    )
            throws PwmUnrecoverableException
    {
        final MacroRequest macroRequest = MacroRequest.forUser(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                getTargetUserInfo( pwmRequest, helpdeskProfile, targetUserIdentity ),
                pwmRequest.getPwmSession().getLoginInfoBean()
        );

        if ( targetUserIdentity != null )
        {
            final UserInfo targetUserInfo = getTargetUserInfo( pwmRequest, helpdeskProfile, targetUserIdentity );
            return macroRequest.toBuilder().targetUserInfo( targetUserInfo ).build();
        }

        return macroRequest;
    }
}
