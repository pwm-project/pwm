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

package password.pwm;

import password.pwm.config.PwmSetting;

/**
 * @author Jason D. Rivard
 */
public enum Permission
{
    PWMADMIN( PwmSetting.QUERY_MATCH_PWM_ADMIN ),
    CHANGE_PASSWORD( PwmSetting.QUERY_MATCH_CHANGE_PASSWORD ),
    ACTIVATE_USER( PwmSetting.ACTIVATE_USER_QUERY_MATCH ),
    SETUP_RESPONSE( PwmSetting.QUERY_MATCH_SETUP_RESPONSE ),
    SETUP_OTP_SECRET( PwmSetting.OTP_SETUP_USER_PERMISSION ),
    GUEST_REGISTRATION( PwmSetting.GUEST_ADMIN_GROUP ),
    PEOPLE_SEARCH( PwmSetting.PEOPLE_SEARCH_QUERY_MATCH ),
    PROFILE_UPDATE( PwmSetting.UPDATE_PROFILE_QUERY_MATCH ),
    WEBSERVICE( PwmSetting.WEBSERVICES_QUERY_MATCH ),
    WEBSERVICE_THIRDPARTY( PwmSetting.WEBSERVICES_THIRDPARTY_QUERY_MATCH ),;

    private final PwmSetting pwmSetting;

    Permission( final PwmSetting pwmSetting )
    {
        this.pwmSetting = pwmSetting;
    }

    public PwmSetting getPwmSetting( )
    {
        return pwmSetting;
    }

    public enum PermissionStatus
    {
        UNCHECKED,
        GRANTED,
        DENIED
    }
}
