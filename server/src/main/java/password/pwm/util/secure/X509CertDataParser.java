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

package password.pwm.util.secure;

import password.pwm.util.java.CollectionUtil;
import password.pwm.util.logging.PwmLogger;

import java.security.PublicKey;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.Optional;
import java.util.stream.Collectors;

class X509CertDataParser
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( X509CertDataParser.class );

    static Optional<String> readCertSubject( final X509Certificate cert )
    {
        if ( cert.getSubjectX500Principal() != null )
        {
            return Optional.of( cert.getSubjectX500Principal().getName() );
        }
        return Optional.empty();
    }

    static Optional<String> readCertIssuer( final X509Certificate cert )
    {
        if ( cert.getIssuerX500Principal() != null )
        {
            return Optional.of( cert.getIssuerX500Principal().getName() );
        }
        return Optional.empty();
    }

    static Optional<String> readCertPublicKeyInfo( final X509Certificate cert )
    {
        if ( cert.getPublicKey() != null )
        {
            final PublicKey publicKey = cert.getPublicKey();
            final String publicKeyInfo = publicKey.getFormat() + " " + publicKey.getAlgorithm();
            return Optional.of( publicKeyInfo );
        }

        return Optional.empty();
    }

    static Optional<String> readCertSubjectAlternativeNames( final X509Certificate cert )
    {

        try
        {
            if ( !CollectionUtil.isEmpty( cert.getSubjectAlternativeNames() ) )
            {
                final String sans = cert.getSubjectAlternativeNames().stream()
                        .map( Object::toString )
                        .collect( Collectors.joining( "," ) );

                return Optional.of( sans );
            }
        }
        catch ( final CertificateParsingException e )
        {
            LOGGER.trace( () -> "error while examining subject alternate names for certificate: " + e.getMessage() );
        }

        return Optional.empty();
    }

    static boolean certIsSigningKey( final X509Certificate certificate )
    {
        final int keyCertSignBitPosition = 5;
        final boolean[] keyUsages = certificate.getKeyUsage();
        return keyUsages != null
                && keyUsages.length > keyCertSignBitPosition - 1
                && keyUsages[keyCertSignBitPosition];
    }
}
