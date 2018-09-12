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
