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

import lombok.AccessLevel;
import lombok.Builder;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.HelpdeskClearResponseMode;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.value.VerificationMethodValue;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.http.servlet.peoplesearch.bean.SearchAttributeBean;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.CollectorUtil;
import password.pwm.util.java.StringUtil;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public record HelpdeskClientData(
        Map<String, String> searchColumns,
        boolean enablePhoto,
        boolean maskPasswords,
        HelpdeskClearResponseMode clearResponses,
        HelpdeskUIMode pwUiMode,
        MessageSendMethod tokenSendMethod,
        Map<String, ActionInformation> actions,
        Map<VerificationMethodValue.EnabledState, Set<IdentityVerificationMethod>> verificationMethods,
        List<FormInformation> verificationForm,
        int maxAdvancedSearchAttributes,
        List<SearchAttributeBean> advancedSearchAttributes,
        boolean enableAdvancedSearch
)
{
    @Builder( access = AccessLevel.PRIVATE )
    public HelpdeskClientData
    {
    }

    public record ActionInformation(
            String name,
            String description
    )
    {
    }

    public record FormInformation(
            String name,
            String label
    )
    {
        static FormInformation fromFormConfiguration(
                final FormConfiguration formConfiguration,
                final Locale locale
        )
        {
            final String name = formConfiguration.getName();
            String label = formConfiguration.getLabel( locale );
            label = !StringUtil.isEmpty( label ) ? label : formConfiguration.getName();
            return new HelpdeskClientData.FormInformation( name, label );
        }
    }

    static HelpdeskClientData fromConfig(
            final HelpdeskProfile helpdeskProfile,
            final Locale locale
    )
    {
        final HelpdeskClientData.HelpdeskClientDataBuilder builder = HelpdeskClientData.builder();


        builder.searchColumns( makeSearchColumns( helpdeskProfile, locale ) );
        builder.actions( makeActions( helpdeskProfile ) );
        builder.verificationMethods( makeVerificationMethods( helpdeskProfile ) );
        builder.verificationForm( makeFormInformation( helpdeskProfile, locale ) );

        {
            final List<SearchAttributeBean> searchAttributes = SearchAttributeBean.searchAttributesFromForm(
                    locale,
                    helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_FORM ) );

            builder.enableAdvancedSearch( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE_ADVANCED_SEARCH ) );
            builder.maxAdvancedSearchAttributes( 3 );
            builder.advancedSearchAttributes( searchAttributes );
        }

        // detail page
        builder.maskPasswords( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_PASSWORD_MASKVALUE ) );
        builder.clearResponses( helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_CLEAR_RESPONSES, HelpdeskClearResponseMode.class ) );
        builder.pwUiMode( helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_SET_PASSWORD_MODE, HelpdeskUIMode.class ) );
        builder.tokenSendMethod( helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_TOKEN_SEND_METHOD, MessageSendMethod.class ) );
        builder.enablePhoto( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE_PHOTOS ) );

        return builder.build();
    }


    private static Map<VerificationMethodValue.EnabledState, Set<IdentityVerificationMethod>> makeVerificationMethods(
            final HelpdeskProfile helpdeskProfile
    )
    {
        return Map.of(
                VerificationMethodValue.EnabledState.optional, helpdeskProfile.readOptionalVerificationMethods(),
                VerificationMethodValue.EnabledState.required, helpdeskProfile.readRequiredVerificationMethods() );

    }

    private static Map<String, String> makeSearchColumns( final HelpdeskProfile helpdeskProfile, final Locale locale )
    {
        // search page
        final List<FormConfiguration> searchForm = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_RESULT_FORM );
        return searchForm.stream().collect( CollectorUtil.toUnmodifiableLinkedMap(
                FormConfiguration::getName,
                formConfiguration -> formConfiguration.getLabel( locale )
        ) );
    }

    private static Map<String, ActionInformation> makeActions( final HelpdeskProfile helpdeskProfile )
    {
        final List<ActionConfiguration> actionConfigurations = helpdeskProfile.readSettingAsAction( PwmSetting.HELPDESK_ACTIONS );

        return actionConfigurations.stream().map( actionConfiguration -> new ActionInformation(
                        actionConfiguration.getName(),
                        actionConfiguration.getDescription() ) )
                .collect( CollectorUtil.toUnmodifiableLinkedMap(
                        ActionInformation::name,
                        Function.identity() ) );

    }

    static List<HelpdeskClientData.FormInformation> makeFormInformation(
            final HelpdeskProfile helpdeskProfile,
            final Locale locale
    )
    {
        final List<FormConfiguration> attributeVerificationForm = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_VERIFICATION_FORM );

        if ( CollectionUtil.isEmpty( attributeVerificationForm ) )
        {
            return List.of();
        }

        return attributeVerificationForm.stream()
                .map( f -> HelpdeskClientData.FormInformation.fromFormConfiguration( f, locale ) )
                .toList();
    }

}
