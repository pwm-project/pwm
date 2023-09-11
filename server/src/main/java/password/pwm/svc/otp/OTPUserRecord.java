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

package password.pwm.svc.otp;

import lombok.Data;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class OTPUserRecord
{
    private static final String CURRENT_VERSION = "1";

    private Instant timestamp = Instant.now();
    private String identifier;
    private String secret;
    private List<RecoveryCode> recoveryCodes = new ArrayList<>();
    private RecoveryInfo recoveryInfo;
    private long attemptCount = 0;
    private Type type = Type.TOTP;
    private String version = CURRENT_VERSION;

    public record RecoveryInfo(
            String salt,
            String hashMethod,
            int hashCount
    )
    {
    }

    public enum Type
    {
        // NOT currently used!
        HOTP,

        TOTP,
    }

    public record RecoveryCode(
            String hash,
            boolean used
    )
    {
    }
}
