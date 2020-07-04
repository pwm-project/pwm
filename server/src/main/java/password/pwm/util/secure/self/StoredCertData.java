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

package password.pwm.util.secure.self;

import password.pwm.util.java.StringUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.security.KeyPair;
import java.security.cert.X509Certificate;

public class StoredCertData implements Serializable
{
    private final X509Certificate x509Certificate;
    private String keypairb64;

    public StoredCertData( final X509Certificate x509Certificate, final KeyPair keypair )
        throws IOException
    {
        this.x509Certificate = x509Certificate;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream( baos );
        oos.writeObject( keypair );
        final byte[] ba = baos.toByteArray();
        keypairb64 = StringUtil.base64Encode( ba );
    }

    public X509Certificate getX509Certificate( )
    {
        return x509Certificate;
    }

    public KeyPair getKeypair( )
        throws IOException, ClassNotFoundException
    {
        final byte[] ba = StringUtil.base64Decode( keypairb64 );
        final ByteArrayInputStream bais = new ByteArrayInputStream( ba );
        final ObjectInputStream ois = new ObjectInputStream( bais );
        return ( KeyPair ) ois.readObject();
    }
}
