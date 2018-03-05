/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.http.servlet.helpdesk;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmHttpRequestWrapper;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.ldap.LdapPermissionTester;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.event.AuditEvent;
import password.pwm.svc.event.AuditRecordFactory;
import password.pwm.svc.event.HelpdeskAuditRecord;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

class HelpdeskServletUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskServletUtil.class );

    static String getSearchFilter( final Configuration configuration, final HelpdeskProfile helpdeskProfile )
    {
        final String configuredFilter = helpdeskProfile.readSettingAsString( PwmSetting.HELPDESK_SEARCH_FILTER );
        if ( configuredFilter != null && !configuredFilter.isEmpty() )
        {
            return configuredFilter;
        }

        final List<String> defaultObjectClasses = configuration.readSettingAsStringArray( PwmSetting.DEFAULT_OBJECT_CLASSES );
        final List<FormConfiguration> searchAttributes = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_FORM );
        final StringBuilder filter = new StringBuilder();

        //open AND clause for objectclasses and attributes
        filter.append( "(&" );

        for ( final String objectClass : defaultObjectClasses )
        {
            filter.append( "(objectClass=" ).append( objectClass ).append( ")" );
        }

        // open OR clause for attributes
        filter.append( "(|" );

        for ( final FormConfiguration formConfiguration : searchAttributes )
        {
            if ( formConfiguration != null && formConfiguration.getName() != null )
            {
                final String searchAttribute = formConfiguration.getName();
                filter.append( "(" ).append( searchAttribute ).append( "=*" ).append( PwmConstants.VALUE_REPLACEMENT_USERNAME ).append( "*)" );
            }
        }

        // close OR clause
        filter.append( ")" );

        // close AND clause
        filter.append( ")" );
        return filter.toString();
    }

    static void checkIfUserIdentityViewable(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final String filterSetting = getSearchFilter( pwmRequest.getConfig(), helpdeskProfile );
        String filterString = filterSetting.replace( PwmConstants.VALUE_REPLACEMENT_USERNAME, "*" );
        while ( filterString.contains( "**" ) )
        {
            filterString = filterString.replace( "**", "*" );
        }

        final boolean match = LdapPermissionTester.testQueryMatch(
                pwmRequest.getPwmApplication(),
                pwmRequest.getSessionLabel(),
                userIdentity,
                filterString
        );

        if ( !match )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_SERVICE_NOT_AVAILABLE,
                    "requested userDN is not available within configured search filter" )
            );
        }
    }

    static void processShowDetailRequest(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, IOException, ServletException
    {
        final HelpdeskDetailInfoBean helpdeskDetailInfoBean;
        try
        {
            helpdeskDetailInfoBean = processDetailRequestImpl( pwmRequest, helpdeskProfile, userIdentity );
        }
        catch ( PwmUnrecoverableException e )
        {
            LOGGER.debug( pwmRequest, e.getErrorInformation() );
            pwmRequest.respondWithError( e.getErrorInformation(), false );
            return;
        }

        if ( helpdeskDetailInfoBean != null )
        {
            final String obfuscatedDN = userIdentity.toObfuscatedKey( pwmRequest.getPwmApplication() );
            pwmRequest.setAttribute( PwmRequestAttribute.HelpdeskObfuscatedDN, obfuscatedDN );
        }

        pwmRequest.setAttribute( PwmRequestAttribute.HelpdeskDetail, helpdeskDetailInfoBean );
        pwmRequest.setAttribute( PwmRequestAttribute.HelpdeskVerificationEnabled, !helpdeskProfile.readOptionalVerificationMethods().isEmpty() );
        pwmRequest.forwardToJsp( JspUrl.HELPDESK_DETAIL );
    }

    static HelpdeskDetailInfoBean processDetailRequestImpl(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final UserIdentity actorUserIdentity = pwmRequest.getUserInfoIfLoggedIn().canonicalized( pwmRequest.getPwmApplication() );

        if ( actorUserIdentity.canonicalEquals( userIdentity, pwmRequest.getPwmApplication() ) )
        {
            final String errorMsg = "cannot select self";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNAUTHORIZED, errorMsg );
            throw new PwmUnrecoverableException( errorInformation );
        }
        LOGGER.trace( pwmRequest, "helpdesk detail view request for user details of " + userIdentity.toString() + " by actor " + actorUserIdentity.toString() );

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
        final HelpdeskAuditRecord auditRecord = new AuditRecordFactory( pwmRequest ).createHelpdeskAuditRecord(
                AuditEvent.HELPDESK_VIEW_DETAIL,
                pwmRequest.getPwmSession().getUserInfo().getUserIdentity(),
                null,
                userIdentity,
                pwmRequest.getSessionLabel().getSrcAddress(),
                pwmRequest.getSessionLabel().getSrcHostname()
        );
        pwmRequest.getPwmApplication().getAuditManager().submit( auditRecord );

        StatisticsManager.incrementStat( pwmRequest, Statistic.HELPDESK_USER_LOOKUP );
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
        if ( requiredMethods == null || requiredMethods.isEmpty() )
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
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final Configuration config = pwmRequest.getConfig();
        final Locale locale = pwmRequest.getLocale();
        final EmailItemBean configuredEmailSetting = config.readSettingAsEmail( PwmSetting.EMAIL_HELPDESK_UNLOCK, locale );

        if ( configuredEmailSetting == null )
        {
            LOGGER.debug( pwmRequest, "skipping send helpdesk unlock notice email for '" + userIdentity + "' no email configured" );
            return;
        }

        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getSessionLabel(),
                pwmRequest.getLocale(),
                userIdentity,
                chaiUser.getChaiProvider()
        );

        final MacroMachine macroMachine = MacroMachine.forUser(
                pwmApplication,
                pwmRequest.getSessionLabel(),
                userInfo,
                null
        );

        pwmApplication.getEmailQueue().submitEmail(
                configuredEmailSetting,
                userInfo,
                macroMachine
        );
    }

}
