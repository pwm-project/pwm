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

package password.pwm.http.servlet.admin;

import lombok.Builder;
import lombok.Value;
import password.pwm.Permission;
import password.pwm.bean.pub.PublicUserInfoBean;
import password.pwm.config.profile.ProfileDefinition;
import password.pwm.config.profile.PwmPasswordPolicy;
import password.pwm.ldap.UserInfo;
import password.pwm.svc.pwnotify.PwNotifyUserStatus;

import java.io.Serializable;
import java.util.Map;

@Value
@Builder
public class UserDebugDataBean implements Serializable
{
    private static final long serialVersionUID = 1L;

    private transient UserInfo userInfo;

    private final PublicUserInfoBean publicUserInfoBean;
    private final boolean passwordReadable;
    private final boolean passwordWithinMinimumLifetime;
    private final Map<Permission, String> permissions;

    private final PwmPasswordPolicy ldapPasswordPolicy;
    private final PwmPasswordPolicy configuredPasswordPolicy;
    private final Map<ProfileDefinition, String> profiles;

    private final PwNotifyUserStatus pwNotifyUserStatus;
}
