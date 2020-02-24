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

package password.pwm.util.secure;

import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.config.option.CertificateMatchingMode;

@Value
class TrustManagerSettings
{
    private final boolean validateTimestamps;
    private final boolean allowSelfSigned;
    private final CertificateMatchingMode certificateMatchingMode;

    public static TrustManagerSettings fromConfiguration( final Configuration config )
    {
        final boolean validateTimestamps = config != null && Boolean.parseBoolean( config.readAppProperty( AppProperty.SECURITY_CERTIFICATES_VALIDATE_TIMESTAMPS ) );
        final boolean allowSelfSigned = config != null && Boolean.parseBoolean( config.readAppProperty( AppProperty.SECURITY_CERTIFICATES_ALLOW_SELF_SIGNED ) );
        final CertificateMatchingMode certificateMatchingMode = config == null
                ? CertificateMatchingMode.CERTIFICATE_CHAIN
                : config.readCertificateMatchingMode();
        return new TrustManagerSettings( validateTimestamps, allowSelfSigned, certificateMatchingMode );
    }
}
