/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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
import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmURL;
import password.pwm.http.servlet.PwmServletDefinition;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Value
@Builder
public class HelpdeskCardInfoBean implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskCardInfoBean.class );

    private String userKey;
    private List<String> displayNames;
    private String photoURL;

    static HelpdeskCardInfoBean makeHelpdeskCardInfo(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException, ChaiUnavailableException
    {
        final HelpdeskCardInfoBean.HelpdeskCardInfoBeanBuilder builder = HelpdeskCardInfoBean.builder();
        final Instant startTime = Instant.now();
        LOGGER.trace( pwmRequest, () -> "beginning to assemble card data report for user " + userIdentity );
        final Locale actorLocale = pwmRequest.getLocale();
        final ChaiUser theUser = HelpdeskServlet.getChaiUser( pwmRequest, helpdeskProfile, userIdentity );

        if ( !theUser.exists() )
        {
            return null;
        }

        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getSessionLabel(),
                actorLocale,
                userIdentity,
                theUser.getChaiProvider()
        );
        final MacroMachine macroMachine = MacroMachine.forUser( pwmRequest.getPwmApplication(), pwmRequest.getSessionLabel(), userInfo, null );

        builder.userKey( userIdentity.toObfuscatedKey( pwmRequest.getPwmApplication() ) );

        builder.photoURL( figurePhotoURL( pwmRequest, helpdeskProfile, theUser, macroMachine, userIdentity ) );

        builder.displayNames( figureDisplayNames( pwmRequest.getPwmApplication(), helpdeskProfile, pwmRequest.getSessionLabel(), userInfo ) );

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
        final HelpdeskCardInfoBean helpdeskCardInfoBean = builder.build();

        if ( pwmRequest.getConfig().isDevDebugMode() )
        {
            LOGGER.trace( pwmRequest, () -> "completed assembly of card data report for user " + userIdentity
                    + " in " + timeDuration.asCompactString() + ", contents: " + JsonUtil.serialize( helpdeskCardInfoBean ) );
        }

        return builder.build();
    }

    static String figureDisplayName(
            final HelpdeskProfile helpdeskProfile,
            final MacroMachine macroMachine
    )
    {
        final String configuredDisplayName = helpdeskProfile.readSettingAsString( PwmSetting.HELPDESK_DETAIL_DISPLAY_NAME );
        if ( configuredDisplayName != null && !configuredDisplayName.isEmpty() )
        {
            return macroMachine.expandMacros( configuredDisplayName );
        }
        return null;
    }

    private static List<String> figureDisplayNames(
            final PwmApplication pwmApplication,
            final HelpdeskProfile helpdeskProfile,
            final SessionLabel sessionLabel,
            final UserInfo userInfo
    )
            throws PwmUnrecoverableException
    {
        final List<String> displayLabels = new ArrayList<>();
        final List<String> displayStringSettings = helpdeskProfile.readSettingAsStringArray( PwmSetting.HELPDESK_DISPLAY_NAMES_CARD_LABELS );
        if ( displayStringSettings != null )
        {
            final MacroMachine macroMachine = MacroMachine.forUser( pwmApplication, sessionLabel, userInfo, null );
            for ( final String displayStringSetting : displayStringSettings )
            {
                final String displayLabel = macroMachine.expandMacros( displayStringSetting );
                displayLabels.add( displayLabel );
            }
        }
        return displayLabels;
    }

    private static String figurePhotoURL(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final ChaiUser chaiUser,
            final MacroMachine macroMachine,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final boolean enabled = helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE_PHOTOS );

        if ( !enabled )
        {
            LOGGER.debug( pwmRequest, () -> "detailed user data lookup for " + userIdentity.toString() + ", failed photo query filter, denying photo view" );
            return null;
        }

        final LdapProfile ldapProfile = userIdentity.getLdapProfile(  pwmApplication.getConfig() );

        final String overrideURL = ldapProfile.readSettingAsString( PwmSetting.LDAP_ATTRIBUTE_PHOTO_URL_OVERRIDE );
        try
        {
            if ( !StringUtil.isEmpty( overrideURL ) )
            {
                return macroMachine.expandMacros( overrideURL );
            }

            try
            {
                LdapOperationsHelper.readPhotoDataFromLdap( pwmApplication.getConfig(), chaiUser, userIdentity );
            }
            catch ( PwmOperationalException e )
            {
                LOGGER.debug( pwmRequest, () -> "determined " + userIdentity + " does not have photo data available while generating detail data" );
                return null;
            }
        }
        catch ( ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }

        String returnUrl = pwmRequest.getContextPath() + PwmServletDefinition.Helpdesk.servletUrl();
        returnUrl = PwmURL.appendAndEncodeUrlParameters( returnUrl, PwmConstants.PARAM_ACTION_REQUEST, HelpdeskServlet.HelpdeskAction.photo.name() );
        returnUrl = PwmURL.appendAndEncodeUrlParameters( returnUrl, PwmConstants.PARAM_USERKEY,  userIdentity.toObfuscatedKey( pwmApplication ) );
        return returnUrl;
    }
}
