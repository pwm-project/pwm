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

package password.pwm.http.servlet.configeditor.function;

import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.X509CertificateValue;
import password.pwm.http.PwmRequest;
import password.pwm.util.secure.X509CertInfo;
import password.pwm.util.secure.X509Utils;

import java.io.Serializable;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class X509CertViewerFunction implements SettingUIFunction
{
    @Override
    public Serializable provideFunction(
            final PwmRequest pwmRequest,
            final StoredConfigurationModifier modifier,
            final StoredConfigKey key,
            final String extraData
    )
            throws Exception
    {
        final List<Map<String, String>> certificateInfos = makeCertDebugMap( key, modifier );
        return ( Serializable ) certificateInfos;
    }

    /**
     * Convert to json map where the certificate values are replaced with debug info for display in the config editor.
     *
     * @return a map suitable for json serialization
     */
    private static List<Map<String, String>> makeCertDebugMap(
            final StoredConfigKey key,
            final StoredConfigurationModifier modifier
    )
    {
        final StoredValue storedValue = modifier.newStoredConfiguration().readStoredValue( key ).orElseThrow();
        final List<X509Certificate> values = ( ( X509CertificateValue ) storedValue ).asX509Certificates();

        return values.stream()
                .map( cert -> X509CertInfo.makeDebugInfoMap( cert, X509Utils.DebugInfoFlag.IncludeCertificateDetail ) )
                .collect( Collectors.toUnmodifiableList() );
    }
}
