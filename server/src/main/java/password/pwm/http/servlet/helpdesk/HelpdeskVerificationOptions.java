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
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.value.VerificationMethodValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.token.TokenUtil;
import password.pwm.user.UserInfo;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record HelpdeskVerificationOptions(
        Map<VerificationMethodValue.EnabledState, Set<IdentityVerificationMethod>> verificationMethods,
        List<HelpdeskClientData.FormInformation> verificationForm,
        List<TokenDestinationItem> tokenDestinations
)
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskVerificationOptions.class );

    public HelpdeskVerificationOptions(
            final Map<VerificationMethodValue.EnabledState, Set<IdentityVerificationMethod>> verificationMethods,
            final List<HelpdeskClientData.FormInformation> verificationForm,
            final List<TokenDestinationItem> tokenDestinations
    )
    {
        this.verificationMethods = CollectionUtil.stripNulls( verificationMethods );
        this.verificationForm = CollectionUtil.stripNulls( verificationForm );
        this.tokenDestinations = CollectionUtil.stripNulls( tokenDestinations );
    }

    static HelpdeskVerificationOptions fromConfig(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity targetUser
    )
            throws PwmUnrecoverableException
    {
        final ChaiUser theUser = HelpdeskServletUtil.getChaiUser( pwmRequest, targetUser );
        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                targetUser,
                theUser.getChaiProvider() );

        final Locale locale = pwmRequest.getLocale();

        final List<HelpdeskClientData.FormInformation> formInformation = HelpdeskClientData.makeFormInformation(
                helpdeskProfile,
                locale );

        final List<TokenDestinationItem> tokenDestinations = makeTokenDestinationItems( pwmRequest, helpdeskProfile, userInfo );

        final Set<IdentityVerificationMethod> unavailableMethods = makeIdentityVerificationMethods( helpdeskProfile,
                userInfo, formInformation, tokenDestinations );

        final Map<VerificationMethodValue.EnabledState, Set<IdentityVerificationMethod>> verificationMethodsMap =
                makeEnabledStateSetMap( helpdeskProfile, unavailableMethods );

        if (
                CollectionUtil.isEmpty( verificationMethodsMap.get( VerificationMethodValue.EnabledState.required ) )
                        && !CollectionUtil.isEmpty( helpdeskProfile.readRequiredVerificationMethods() )
        )
        {
            final String msg = "configuration requires verification, but target user has no eligible required verification methods available.";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_MISSING_CONTACT, msg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return new HelpdeskVerificationOptions(
                verificationMethodsMap,
                formInformation,
                tokenDestinations );
    }

    private static Map<VerificationMethodValue.EnabledState, Set<IdentityVerificationMethod>> makeEnabledStateSetMap(
            final HelpdeskProfile helpdeskProfile,
            final Set<IdentityVerificationMethod> unavailableMethods
    )
    {
        final Map<VerificationMethodValue.EnabledState, Set<IdentityVerificationMethod>> returnMap
                = new EnumMap<>( VerificationMethodValue.EnabledState.class );

        {
            final Set<IdentityVerificationMethod> optionalMethods = CollectionUtil.copyToEnumSet(
                    helpdeskProfile.readOptionalVerificationMethods(),
                    IdentityVerificationMethod.class );
            optionalMethods.removeAll( unavailableMethods );
            returnMap.put( VerificationMethodValue.EnabledState.optional, optionalMethods );
        }

        {
            final Set<IdentityVerificationMethod> requiredMethods = CollectionUtil.copyToEnumSet(
                    helpdeskProfile.readRequiredVerificationMethods(),
                    IdentityVerificationMethod.class );
            requiredMethods.removeAll( unavailableMethods );
            returnMap.put( VerificationMethodValue.EnabledState.required, requiredMethods );
        }

        return Map.copyOf( returnMap );
    }

    private static Set<IdentityVerificationMethod> makeIdentityVerificationMethods(
            final HelpdeskProfile helpdeskProfile,
            final UserInfo userInfo,
            final List<HelpdeskClientData.FormInformation> formInformation,
            final List<TokenDestinationItem> tokenDestinations
    )
            throws PwmUnrecoverableException
    {
        final Set<IdentityVerificationMethod> returnSet = EnumSet.noneOf( IdentityVerificationMethod.class );
        final Set<IdentityVerificationMethod> workSet = EnumSet.noneOf( IdentityVerificationMethod.class );
        workSet.addAll( helpdeskProfile.readOptionalVerificationMethods()  );
        workSet.addAll( helpdeskProfile.readRequiredVerificationMethods()  );

        if ( workSet.contains( IdentityVerificationMethod.ATTRIBUTES ) )
        {
            if ( CollectionUtil.isEmpty( formInformation ) )
            {
                returnSet.add( IdentityVerificationMethod.ATTRIBUTES );
            }
        }

        if ( workSet.contains( IdentityVerificationMethod.OTP ) )
        {
            if ( userInfo.getOtpUserRecord() == null )
            {
                returnSet.add( IdentityVerificationMethod.OTP );
            }
        }

        if ( workSet.contains( IdentityVerificationMethod.TOKEN ) )
        {
            if ( CollectionUtil.isEmpty( tokenDestinations ) )
            {
                returnSet.add( IdentityVerificationMethod.TOKEN );
            }
        }

        return Set.copyOf( returnSet );
    }

    private static List<TokenDestinationItem> makeTokenDestinationItems(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserInfo userInfo
    )
    {
        final MessageSendMethod testSetting = helpdeskProfile.readSettingAsEnum(
                PwmSetting.HELPDESK_TOKEN_SEND_METHOD, MessageSendMethod.class );

        final List<TokenDestinationItem> returnList = new ArrayList<>( );

        if ( testSetting != null && testSetting != MessageSendMethod.NONE )
        {
            try
            {
                returnList.addAll( TokenUtil.figureAvailableTokenDestinations(
                        pwmRequest.getPwmDomain(),
                        pwmRequest.getLabel(),
                        pwmRequest.getLocale(),
                        userInfo,
                        testSetting
                ) );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.trace( pwmRequest, () -> "error while calculating available token methods: " + e.getMessage() );
            }
        }
        return List.copyOf( TokenDestinationItem.stripValues( returnList ) );

    }

}
