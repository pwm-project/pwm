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

package password.pwm.config.profile;

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;

public enum ProfileType
{
    Helpdesk            ( true,  PwmSettingCategory.HELPDESK_PROFILE,    PwmSetting.HELPDESK_PROFILE_QUERY_MATCH ),
    ForgottenPassword   ( false, PwmSettingCategory.RECOVERY_PROFILE,    PwmSetting.RECOVERY_PROFILE_QUERY_MATCH ),
    NewUser             ( false, PwmSettingCategory.NEWUSER_PROFILE,     null ),
    UpdateAttributes    ( true,  PwmSettingCategory.UPDATE_PROFILE,      PwmSetting.UPDATE_PROFILE_QUERY_MATCH ),
    DeleteAccount       ( true,  PwmSettingCategory.DELETE_ACCOUNT_PROFILE, PwmSetting.DELETE_ACCOUNT_PERMISSION ),
    SetupOTPProfile     ( true, PwmSettingCategory.OTP_PROFILE, PwmSetting.OTP_SETUP_USER_PERMISSION ),
    EmailServers        ( true, PwmSettingCategory.EMAIL_SERVERS, null ),;

    
    private final boolean authenticated;
    private final PwmSettingCategory category;
    private final PwmSetting queryMatch;

    ProfileType( final boolean authenticated, final PwmSettingCategory category, final PwmSetting queryMatch )
    {
        this.authenticated = authenticated;
        this.category = category;
        this.queryMatch = queryMatch;
    }

    public boolean isAuthenticated( )
    {
        return authenticated;
    }

    public PwmSettingCategory getCategory( )
    {
        return category;
    }

    public PwmSetting getQueryMatch( )
    {
        return queryMatch;
    }
}
