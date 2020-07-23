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
import lombok.Builder;
import lombok.Value;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.value.VerificationMethodValue;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.token.TokenUtil;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Value
@Builder
public class HelpdeskVerificationOptionsBean implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HelpdeskVerificationOptionsBean.class );

    private Map<VerificationMethodValue.EnabledState, Collection<IdentityVerificationMethod>> verificationMethods;
    private List<HelpdeskClientDataBean.FormInformation> verificationForm;
    private List<TokenDestinationItem> tokenDestinations;

    static HelpdeskVerificationOptionsBean makeBean(
            final PwmRequest pwmRequest,
            final HelpdeskProfile helpdeskProfile,
            final UserIdentity targetUser

    )
            throws PwmUnrecoverableException
    {
        final ChaiUser theUser = HelpdeskServlet.getChaiUser( pwmRequest, helpdeskProfile, targetUser );
        final UserInfo userInfo = UserInfoFactory.newUserInfo(
                pwmRequest.getPwmApplication(),
                pwmRequest.getLabel(),
                pwmRequest.getLocale(),
                targetUser,
                theUser.getChaiProvider() );

        final Locale locale = pwmRequest.getLocale();

        final List<HelpdeskClientDataBean.FormInformation> formInformations;
        {
            final List<HelpdeskClientDataBean.FormInformation> returnList = new ArrayList<>();
            final List<FormConfiguration> attributeVerificationForm = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_VERIFICATION_FORM );
            if ( attributeVerificationForm != null )
            {
                for ( final FormConfiguration formConfiguration : attributeVerificationForm )
                {
                    final String name = formConfiguration.getName();
                    String label = formConfiguration.getLabel( locale );
                    label = ( label != null && !label.isEmpty() ) ? label : formConfiguration.getName();
                    final HelpdeskClientDataBean.FormInformation formInformation = new HelpdeskClientDataBean.FormInformation( name, label );
                    returnList.add( formInformation );
                }
            }
            formInformations = Collections.unmodifiableList( returnList );
        }

        final List<TokenDestinationItem> tokenDestinations;
        {
            final MessageSendMethod testSetting = helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_TOKEN_SEND_METHOD, MessageSendMethod.class );
            final List<TokenDestinationItem> returnList = new ArrayList<>( );

            if ( testSetting != null && testSetting != MessageSendMethod.NONE )
            {
                try
                {
                    returnList.addAll( TokenUtil.figureAvailableTokenDestinations(
                            pwmRequest.getPwmApplication(),
                            pwmRequest.getLabel(),
                            pwmRequest.getLocale(),
                            userInfo,
                            testSetting
                    ) );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    LOGGER.trace( () -> "error while calculating available token methods: " + e.getMessage() );
                }
            }
            tokenDestinations = Collections.unmodifiableList( TokenDestinationItem.stripValues( returnList ) );
        }

        final Set<IdentityVerificationMethod> unavailableMethods;
        {
            final Set<IdentityVerificationMethod> returnSet = new HashSet<>();
            final Set<IdentityVerificationMethod> workSet = new HashSet<>();
            workSet.addAll( helpdeskProfile.readOptionalVerificationMethods()  );
            workSet.addAll( helpdeskProfile.readRequiredVerificationMethods()  );

            for ( final IdentityVerificationMethod method : workSet )
            {
                switch ( method )
                {
                    case ATTRIBUTES:
                    {
                        if ( JavaHelper.isEmpty( formInformations ) )
                        {
                            returnSet.add( IdentityVerificationMethod.ATTRIBUTES );
                        }
                    }
                    break;

                    case OTP:
                    {
                        if ( userInfo.getOtpUserRecord() == null )
                        {
                            returnSet.add( IdentityVerificationMethod.OTP );
                        }

                    }
                    break;

                    case TOKEN:
                    {
                        if ( JavaHelper.isEmpty( tokenDestinations ) )
                        {
                            returnSet.add( IdentityVerificationMethod.TOKEN );
                        }
                    }
                    break;

                    default:
                        break;
                }
            }

            unavailableMethods = Collections.unmodifiableSet( returnSet );
        }

        final Map<VerificationMethodValue.EnabledState, Collection<IdentityVerificationMethod>> verificationMethodsMap;
        {
            final Map<VerificationMethodValue.EnabledState, Collection<IdentityVerificationMethod>> returnMap = new HashMap<>();
            {
                final Set<IdentityVerificationMethod> optionalMethods = new HashSet<>( helpdeskProfile.readOptionalVerificationMethods() );
                optionalMethods.removeAll( unavailableMethods );
                returnMap.put( VerificationMethodValue.EnabledState.optional, optionalMethods );
            }
            {
                final Set<IdentityVerificationMethod> requiredMethods = new HashSet<>( helpdeskProfile.readRequiredVerificationMethods() );
                requiredMethods.removeAll( unavailableMethods );
                returnMap.put( VerificationMethodValue.EnabledState.required, requiredMethods );
            }
            verificationMethodsMap = Collections.unmodifiableMap( returnMap );
        }

        if (
                JavaHelper.isEmpty( verificationMethodsMap.get( VerificationMethodValue.EnabledState.required ) )
                        && !JavaHelper.isEmpty( helpdeskProfile.readRequiredVerificationMethods() )
        )
        {
            final String msg = "configuration requires verification, but target user has no eligible required verification methods available.";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_TOKEN_MISSING_CONTACT, msg );
            throw new PwmUnrecoverableException( errorInformation );
        }

        return HelpdeskVerificationOptionsBean.builder()
                .tokenDestinations( tokenDestinations )
                .verificationForm( formInformations )
                .verificationMethods( verificationMethodsMap )
                .build();
    }
}
