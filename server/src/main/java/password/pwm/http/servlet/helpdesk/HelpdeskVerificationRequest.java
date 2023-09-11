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

import password.pwm.http.servlet.helpdesk.data.HelpdeskTargetUserRequest;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;


/**
 * @param tokenData Contains encrypted {@link TokenData} during transport.
 */
public record HelpdeskVerificationRequest(
        String destination,
        String userKey,
        Map<String, String> attributeData,
        String code,
        String tokenData,
        String verificationState
)
    implements HelpdeskTargetUserRequest
{
    public HelpdeskVerificationRequest(
            final String destination,
            final String userKey,
            final Map<String, String> attributeData,
            final String code,
            final String tokenData,
            final String verificationState
    )
    {
        this.destination = destination;
        this.userKey = userKey;
        this.attributeData = CollectionUtil.stripNulls( attributeData );
        this.code = code;
        this.tokenData = tokenData;
        this.verificationState = verificationState;
    }

    public record TokenData(
            String token,
            Instant issueDate
    )
    {
        public TokenData(
                final String token,
                final Instant issueDate
        )
        {
            this.token = JavaHelper.requireNonEmpty( token );
            this.issueDate = Objects.requireNonNull( issueDate );
        }
    }
}
