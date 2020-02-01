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

package password.pwm.ldap.search;

import com.novell.ldapchai.provider.ChaiProvider;
import lombok.Builder;
import lombok.Value;
import password.pwm.bean.SessionLabel;
import password.pwm.config.profile.LdapProfile;

import java.util.Collection;

@Value
@Builder
public class UserSearchJobParameters
{
    private final LdapProfile ldapProfile;
    private final String searchFilter;
    private final String context;
    private final Collection<String> returnAttributes;
    private final int maxResults;
    private final ChaiProvider chaiProvider;
    private final long timeoutMs;
    private final SessionLabel sessionLabel;
    private final int searchID;
    private final int jobId;
}
