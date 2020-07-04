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

package password.pwm.config.profile;

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;

import java.util.Optional;

public enum ProfileDefinition
{
    ChangePassword(
            Type.AUTHENTICATED,
            ChangePasswordProfile.class,
            ChangePasswordProfile.ChangePasswordProfileFactory.class,
            PwmSettingCategory.CHANGE_PASSWORD_PROFILE,
            PwmSetting.QUERY_MATCH_CHANGE_PASSWORD ),
    AccountInformation(
            Type.AUTHENTICATED,
            AccountInformationProfile.class,
            AccountInformationProfile.AccountInformationProfileFactory.class,
            PwmSettingCategory.ACCOUNT_INFO_PROFILE,
            PwmSetting.ACCOUNT_INFORMATION_QUERY_MATCH ),
    Helpdesk(
            Type.AUTHENTICATED,
            HelpdeskProfile.class,
            HelpdeskProfile.HelpdeskProfileFactory.class,
            PwmSettingCategory.HELPDESK_PROFILE,
            PwmSetting.HELPDESK_PROFILE_QUERY_MATCH ),
    ForgottenPassword(
            Type.PUBLIC,
            ForgottenPasswordProfile.class,
            ForgottenPasswordProfile.ForgottenPasswordProfileFactory.class,
            PwmSettingCategory.RECOVERY_PROFILE,
            PwmSetting.RECOVERY_PROFILE_QUERY_MATCH ),
    NewUser(
            Type.PUBLIC,
            NewUserProfile.class,
            NewUserProfile.NewUserProfileFactory.class,
            PwmSettingCategory.NEWUSER_PROFILE,
            null ),
    UpdateAttributes(
            Type.AUTHENTICATED,
            UpdateProfileProfile.class,
            UpdateProfileProfile.UpdateProfileProfileFactory.class,
            PwmSettingCategory.UPDATE_PROFILE,
            PwmSetting.UPDATE_PROFILE_QUERY_MATCH ),
    ActivateUser(
            Type.PUBLIC,
            ActivateUserProfile.class,
            ActivateUserProfile.UserActivationProfileFactory.class,
            PwmSettingCategory.ACTIVATION_PROFILE,
            PwmSetting.ACTIVATE_USER_QUERY_MATCH ),
    DeleteAccount(
            Type.AUTHENTICATED,
            DeleteAccountProfile.class,
            DeleteAccountProfile.DeleteAccountProfileFactory.class,
            PwmSettingCategory.DELETE_ACCOUNT_PROFILE,
            PwmSetting.DELETE_ACCOUNT_PERMISSION ),
    SetupOTPProfile(
            Type.AUTHENTICATED,
            SetupOtpProfile.class,
            SetupOtpProfile.SetupOtpProfileFactory.class,
            PwmSettingCategory.OTP_PROFILE,
            PwmSetting.OTP_SETUP_USER_PERMISSION ),
    PeopleSearch(
            Type.AUTHENTICATED,
            PeopleSearchProfile.class,
            PeopleSearchProfile.PeopleSearchProfileFactory.class,
            PwmSettingCategory.PEOPLE_SEARCH_PROFILE,
            PwmSetting.PEOPLE_SEARCH_QUERY_MATCH ),
    PeopleSearchPublic(
            Type.PUBLIC,
            PeopleSearchProfile.class,
            PeopleSearchProfile.PeopleSearchProfileFactory.class,
            PwmSettingCategory.PEOPLE_SEARCH_PROFILE,
            null ),
    EmailServers(
            Type.SERVICE,
            EmailServerProfile.class,
            EmailServerProfile.EmailServerProfileFactory.class,
            PwmSettingCategory.EMAIL_SERVERS,
            null ),
    PasswordPolicy(
            Type.SERVICE,
            PwmPasswordPolicy.class,
            null,
            PwmSettingCategory.PASSWORD_POLICY,
            PwmSetting.PASSWORD_POLICY_QUERY_MATCH ),
    LdapProfile(
            Type.SERVICE,
            LdapProfile.class,
            LdapProfile.LdapProfileFactory.class,
            PwmSettingCategory.LDAP_PROFILE,
            null ),
    ChallengeProfile(
            Type.SERVICE,
            ChallengeProfile.class,
            null,
            PwmSettingCategory.CHALLENGE_POLICY,
            PwmSetting.CHALLENGE_POLICY_QUERY_MATCH ),;

    private final Type type;
    private final Class<? extends Profile> profileImplClass;
    private final Class<? extends Profile.ProfileFactory> profileFactoryClass;
    private final PwmSettingCategory category;
    private final PwmSetting queryMatch;

    enum Type
    {
        PUBLIC,
        AUTHENTICATED,
        SERVICE,
    }

    ProfileDefinition(
            final Type type,
            final Class<? extends Profile> profileImplClass,
            final Class<? extends Profile.ProfileFactory> profileFactoryClass,
            final PwmSettingCategory category,
            final PwmSetting queryMatch
    )
    {
        this.type = type;
        this.profileImplClass = profileImplClass;
        this.profileFactoryClass = profileFactoryClass;
        this.category = category;
        this.queryMatch = queryMatch;
    }

    public boolean isAuthenticated( )
    {
        return type == Type.AUTHENTICATED;
    }

    public PwmSettingCategory getCategory( )
    {
        return category;
    }

    public Optional<PwmSetting> getQueryMatch( )
    {
        return Optional.ofNullable( queryMatch );
    }

    public Class<? extends Profile> getProfileImplClass()
    {
        return profileImplClass;
    }

    public Optional<Class<? extends Profile.ProfileFactory>> getProfileFactoryClass()
    {
        return Optional.ofNullable( profileFactoryClass );
    }
}
