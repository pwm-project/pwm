/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

import lombok.Builder;
import lombok.Value;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.HelpdeskClearResponseMode;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;
import password.pwm.config.profile.HelpdeskProfile;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.http.servlet.peoplesearch.bean.SearchAttributeBean;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Value
@Builder
public class HelpdeskClientDataBean implements Serializable
{
    private Map<String, String> searchColumns;
    private boolean enablePhoto;
    private boolean maskPasswords;
    private HelpdeskClearResponseMode clearResponses;
    private HelpdeskUIMode pwUiMode;
    private MessageSendMethod tokenSendMethod;
    private Map<String, ActionInformation> actions;
    private Map<String, Collection<IdentityVerificationMethod>> verificationMethods;
    private List<FormInformation> verificationForm;
    private int maxAdvancedSearchAttributes;
    private List<SearchAttributeBean> advancedSearchAttributes;
    private boolean enableAdvancedSearch;


    @Value
    public static class ActionInformation implements Serializable
    {
        private String name;
        private String description;
    }

    @Value
    public static class FormInformation implements Serializable
    {
        private String name;
        private String label;
    }


    static HelpdeskClientDataBean fromConfig(
            final HelpdeskProfile helpdeskProfile,
            final Locale locale
    )
    {
        final HelpdeskClientDataBean.HelpdeskClientDataBeanBuilder builder = HelpdeskClientDataBean.builder();
        {
            // search page
            final List<FormConfiguration> searchForm = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_RESULT_FORM );
            final Map<String, String> searchColumns = new LinkedHashMap<>();
            for ( final FormConfiguration formConfiguration : searchForm )
            {
                searchColumns.put( formConfiguration.getName(), formConfiguration.getLabel( locale ) );
            }
            builder.searchColumns( searchColumns );
        }
        {
            // detail page
            builder.maskPasswords( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_PASSWORD_MASKVALUE ) );
            builder.clearResponses( helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_CLEAR_RESPONSES, HelpdeskClearResponseMode.class ) );
            builder.pwUiMode( helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_SET_PASSWORD_MODE, HelpdeskUIMode.class ) );
            builder.tokenSendMethod( helpdeskProfile.readSettingAsEnum( PwmSetting.HELPDESK_TOKEN_SEND_METHOD, MessageSendMethod.class ) );
            builder.enablePhoto( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE_PHOTOS ) );
        }
        {
            // actions
            final List<ActionConfiguration> actionConfigurations = helpdeskProfile.readSettingAsAction( PwmSetting.HELPDESK_ACTIONS );
            final Map<String, HelpdeskClientDataBean.ActionInformation> actions = new LinkedHashMap<>();
            for ( final ActionConfiguration actionConfiguration : actionConfigurations )
            {
                final HelpdeskClientDataBean.ActionInformation actionInformation = new HelpdeskClientDataBean.ActionInformation(
                        actionConfiguration.getName(),
                        actionConfiguration.getDescription()
                );
                actions.put( actionConfiguration.getName(), actionInformation );
            }

            builder.actions( actions );
        }
        {
            final Map<String, Collection<IdentityVerificationMethod>> verificationMethodsMap = new HashMap<>();
            verificationMethodsMap.put( "optional", helpdeskProfile.readOptionalVerificationMethods() );
            verificationMethodsMap.put( "required", helpdeskProfile.readRequiredVerificationMethods() );
            builder.verificationMethods( verificationMethodsMap );
        }
        {
            final List<FormConfiguration> attributeVerificationForm = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_VERIFICATION_FORM );
            final List<HelpdeskClientDataBean.FormInformation> formInformations = new ArrayList<>();
            if ( attributeVerificationForm != null )
            {
                for ( final FormConfiguration formConfiguration : attributeVerificationForm )
                {
                    final String name = formConfiguration.getName();
                    String label = formConfiguration.getLabel( locale );
                    label = ( label != null && !label.isEmpty() ) ? label : formConfiguration.getName();
                    final HelpdeskClientDataBean.FormInformation formInformation = new HelpdeskClientDataBean.FormInformation( name, label );
                    formInformations.add( formInformation );
                }
            }
            builder.verificationForm( formInformations );
        }
        {
            final List<SearchAttributeBean> searchAttributes = SearchAttributeBean.searchAttributesFromForm(
                    locale,
                    helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_FORM ) );

                    builder.enableAdvancedSearch( helpdeskProfile.readSettingAsBoolean( PwmSetting.HELPDESK_ENABLE_ADVANCED_SEARCH ) );
                    builder.maxAdvancedSearchAttributes( 3 );
                    builder.advancedSearchAttributes( searchAttributes );
        }


        return builder.build();
    }
}
