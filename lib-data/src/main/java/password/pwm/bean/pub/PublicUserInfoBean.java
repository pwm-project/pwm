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

package password.pwm.bean.pub;

import lombok.Builder;
import lombok.Value;
import password.pwm.bean.PasswordStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class PublicUserInfoBean implements PublishedBean
{
    private String userDN;
    private String ldapProfile;
    private String userID;
    private String userGUID;
    private String userEmailAddress;
    private String userEmailAddress2;
    private String userEmailAddress3;
    private String userSmsNumber;
    private String userSmsNumber2;
    private String userSmsNumber3;
    private String language;
    private Instant passwordExpirationTime;
    private Instant passwordLastModifiedTime;
    private Instant lastLoginTime;
    private Instant accountExpirationTime;
    private boolean requiresNewPassword;
    private boolean requiresResponseConfig;
    private boolean requiresUpdateProfile;
    private boolean requiresOtpConfig;
    private boolean requiresInteraction;

    private PasswordStatus passwordStatus;
    private Map<String, String> passwordPolicy;
    private List<String> passwordRules;
    private Map<String, String> attributes;

}
