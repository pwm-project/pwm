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
            final List<FormConfiguration> searchForm = helpdeskProfile.readSettingAsForm( PwmSetting.HELPDESK_SEARCH_FORM );
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


        return builder.build();
    }
}
