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

package password.pwm.bean;

import lombok.Value;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.X509Utils;

import java.io.IOException;
import java.io.Serializable;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.List;

@Value
public class PrivateKeyCertificate implements Serializable
{
    private final List<String> b64certificates;
    private final String privateKey;

    public PrivateKeyCertificate( final List<X509Certificate> certificates, final PrivateKey privateKey )
    {
        this.b64certificates = X509Utils.certificatesToBase64s( certificates );
        this.privateKey = StringUtil.base64Encode( privateKey.getEncoded() );
    }

    public List<X509Certificate> getCertificates()
    {
        return X509Utils.certificatesFromBase64s( b64certificates );
    }

    public PrivateKey getKey()
    {
        try
        {
            final byte[] privateKeyBytes = StringUtil.base64Decode( privateKey );
            return KeyFactory.getInstance( "RSA" ).generatePrivate( new PKCS8EncodedKeySpec( privateKeyBytes ) );
        }
        catch ( final InvalidKeySpecException | NoSuchAlgorithmException | IOException e )
        {
            throw new IllegalStateException( "unexpected error converting b64 privateKey to PrivateKey instance: " + e.getMessage() );
        }
    }
}
