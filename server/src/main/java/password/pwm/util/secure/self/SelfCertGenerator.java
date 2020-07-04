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

import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import password.pwm.util.java.PwmDateFormat;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureService;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.TimeUnit;

class SelfCertGenerator
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SelfCertGenerator.class );

    private static volatile boolean bouncyCastleInitialized;

    private final Settings settings;
    private final SecureService secureService;

    SelfCertGenerator( final Settings settings,  final SecureService secureService )
    {
        this.secureService = secureService;
        this.settings = settings;
    }

    StoredCertData generateNewCertificate( final String cnName )
        throws Exception
    {
        initBouncyCastleProvider();

        LOGGER.debug( () -> "creating self-signed certificate with cn of " + cnName );
        final KeyPair keyPair = generateRSAKeyPair( );
        final X509Certificate certificate = generateV3Certificate( keyPair, cnName );
        return new StoredCertData( certificate, keyPair );
    }


    private X509Certificate generateV3Certificate( final KeyPair pair, final String cnValue )
        throws Exception
    {
        final X500NameBuilder subjectName = new X500NameBuilder( BCStyle.INSTANCE );
        subjectName.addRDN( BCStyle.CN, cnValue );

        final BigInteger serialNumber = makeSerialNumber();

        // 2 days in the past
        final Date notBefore = new Date( System.currentTimeMillis() - TimeUnit.DAYS.toMillis( 2 ) );

        final long futureSeconds = settings.getFutureSeconds();
        final Date notAfter = new Date( System.currentTimeMillis() + ( futureSeconds * 1000 ) );

        final X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(
            subjectName.build(),
            serialNumber,
            notBefore,
            notAfter,
            subjectName.build(),
            pair.getPublic()
        );

        // false == not a CA
        final BasicConstraints basic = new BasicConstraints( false );

        // OID, critical, ASN.1 encoded value
        certGen.addExtension( Extension.basicConstraints, true, basic.getEncoded() );

        // add subject alternate name
        /*
        {
            final ASN1Encodable[] subjectAlternativeNames = new ASN1Encodable[]
                {
                    new GeneralName( GeneralName.dNSName, cnValue ),
                    };
            final DERSequence subjectAlternativeNamesExtension = new DERSequence( subjectAlternativeNames );
            certGen.addExtension( Extension.subjectAlternativeName, false, subjectAlternativeNamesExtension );
        }
        */


        // sign and key encipher
        final KeyUsage keyUsage = new KeyUsage( KeyUsage.digitalSignature | KeyUsage.keyEncipherment );

        // OID, critical, ASN.1 encoded value
        certGen.addExtension( Extension.keyUsage, true, keyUsage.getEncoded() );

        // server authentication
        final ExtendedKeyUsage extKeyUsage = new ExtendedKeyUsage( KeyPurposeId.id_kp_serverAuth );

        // OID, critical, ASN.1 encoded value
        certGen.addExtension( Extension.extendedKeyUsage, true, extKeyUsage.getEncoded() );

        final ContentSigner sigGen = new JcaContentSignerBuilder( "SHA256WithRSAEncryption" ).setProvider( "BC" ).build( pair.getPrivate() );

        return new JcaX509CertificateConverter().setProvider( "BC" ).getCertificate( certGen.build( sigGen ) );
    }

    private BigInteger makeSerialNumber()
    {
        final PwmDateFormat formatter = PwmDateFormat.newPwmDateFormat( "yyyyMMddhhmmss" );
        final String serNumStr = formatter.format( Instant.now() );
        return new BigInteger( serNumStr );
    }

    private KeyPair generateRSAKeyPair( )
        throws Exception
    {
        final KeyPairGenerator kpGen = KeyPairGenerator.getInstance( settings.getKeyAlg(), "BC" );
        kpGen.initialize( settings.getKeySize(), secureService == null ? new SecureRandom() : secureService.pwmRandom() );
        return kpGen.generateKeyPair();
    }

    private static synchronized void initBouncyCastleProvider( )
    {
        if ( !bouncyCastleInitialized )
        {
            Security.addProvider( new BouncyCastleProvider() );
            bouncyCastleInitialized = true;
        }
    }
}
