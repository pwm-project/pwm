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
import lombok.Builder;
import lombok.Value;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.peoplesearch.PhotoDataReader;
import password.pwm.user.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
        final ChaiUser theUser = HelpdeskServletUtil.getChaiUser( pwmRequest, helpdeskProfile, userIdentity );

        if ( !theUser.exists() )
        {
            return null;
        }

        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                actorLocale,
                userIdentity,
                theUser.getChaiProvider()
        );

        builder.userKey( userIdentity.toObfuscatedKey( pwmRequest.getPwmApplication() ) );

        final PhotoDataReader photoDataReader = HelpdeskServlet.photoDataReader( pwmRequest, helpdeskProfile, userIdentity );
        final Optional<String> optionalPhotoUrl = photoDataReader.figurePhotoURL();
        optionalPhotoUrl.ifPresent( builder::photoURL );

        builder.displayNames( figureDisplayNames( pwmRequest.getPwmDomain(), helpdeskProfile, pwmRequest.getLabel(), userInfo ) );

        final TimeDuration timeDuration = TimeDuration.fromCurrent( startTime );
        final HelpdeskCardInfoBean helpdeskCardInfoBean = builder.build();

        if ( pwmRequest.getAppConfig().isDevDebugMode() )
        {
            LOGGER.trace( pwmRequest, () -> "completed assembly of card data report for user " + userIdentity
                    + " in " + timeDuration.asCompactString() + ", contents: " + JsonFactory.get().serialize( helpdeskCardInfoBean ) );
        }

        return builder.build();
    }

    static String figureDisplayName(
            final HelpdeskProfile helpdeskProfile,
            final MacroRequest macroRequest
    )
    {
        final String configuredDisplayName = helpdeskProfile.readSettingAsString( PwmSetting.HELPDESK_DETAIL_DISPLAY_NAME );
        if ( configuredDisplayName != null && !configuredDisplayName.isEmpty() )
        {
            return macroRequest.expandMacros( configuredDisplayName );
        }
        return null;
    }

    private static List<String> figureDisplayNames(
            final PwmDomain pwmDomain,
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
            final MacroRequest macroRequest = MacroRequest.forUser( pwmDomain.getPwmApplication(), sessionLabel, userInfo, null );
            for ( final String displayStringSetting : displayStringSettings )
            {
                final String displayLabel = macroRequest.expandMacros( displayStringSetting );
                displayLabels.add( displayLabel );
            }
        }
        return displayLabels;
    }
}
