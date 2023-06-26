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

package password.pwm.util.password;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class RandomGeneratorConfigRequest
{
    /**
     * A set of phrases (Strings) used to generate the pwmRandom passwords.  There must be enough
     * values in the phrases to build a random password that meets rule requirements
     */
    @Builder.Default
    private List<String> seedlistPhrases = List.of();

    /**
     * The minimum length desired for the password.  The algorithm will attempt to make
     * the returned value at least this long, but it is not guaranteed.
     */
    @Builder.Default
    private int minimumLength = -1;

    @Builder.Default
    private int maximumLength = -1;

    /**
     * The minimum length desired strength.  The algorithm will attempt to make
     * the returned value at least this strong, but it is not guaranteed.
     */
    @Builder.Default
    private int minimumStrength = -1;

}
