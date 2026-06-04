/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import password.pwm.config.option.CertificateMatchingMode;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Tests for pwm-project/pwm issue 735: in {@code CA_ONLY} mode, configuring only a root CA should
 * validate a {@code leaf -> intermediate -> root} chain using the intermediate(s) supplied by the
 * remote server, rather than requiring every intermediate to be configured explicitly.
 */
public class PwmTrustManagerTest
{
    private static X509Certificate rootCert;
    private static X509Certificate intermediateCert;
    private static X509Certificate leafCert;
    private static X509Certificate unrelatedRootCert;

    private static final TrustManagerSettings CA_ONLY_SETTINGS =
            new TrustManagerSettings( false, false, CertificateMatchingMode.CA_ONLY );

    @BeforeClass
    public static void generateChain() throws Exception
    {
        final KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance( "RSA" );
        keyPairGenerator.initialize( 2048 );

        final KeyPair rootKeyPair = keyPairGenerator.generateKeyPair();
        final KeyPair intermediateKeyPair = keyPairGenerator.generateKeyPair();
        final KeyPair leafKeyPair = keyPairGenerator.generateKeyPair();
        final KeyPair unrelatedRootKeyPair = keyPairGenerator.generateKeyPair();

        final X500Name rootDn = new X500Name( "CN=PWM Test Root CA" );
        final X500Name intermediateDn = new X500Name( "CN=PWM Test Intermediate CA" );
        final X500Name leafDn = new X500Name( "CN=server.example.edu" );
        final X500Name unrelatedRootDn = new X500Name( "CN=PWM Test Unrelated Root CA" );

        // root: self-signed CA
        rootCert = makeCertificate( rootDn, rootKeyPair.getPublic(), rootDn, rootKeyPair.getPrivate(), true, BigInteger.valueOf( 1 ) );
        // intermediate: CA, signed by root
        intermediateCert = makeCertificate( intermediateDn, intermediateKeyPair.getPublic(), rootDn, rootKeyPair.getPrivate(), true, BigInteger.valueOf( 2 ) );
        // leaf: end-entity, signed by intermediate
        leafCert = makeCertificate( leafDn, leafKeyPair.getPublic(), intermediateDn, intermediateKeyPair.getPrivate(), false, BigInteger.valueOf( 3 ) );
        // an unrelated, independently-rooted CA used to prove untrusted chains are rejected
        unrelatedRootCert = makeCertificate( unrelatedRootDn, unrelatedRootKeyPair.getPublic(), unrelatedRootDn, unrelatedRootKeyPair.getPrivate(), true, BigInteger.valueOf( 1 ) );
    }

    private static X509Certificate makeCertificate(
            final X500Name subjectDn,
            final PublicKey subjectPublicKey,
            final X500Name issuerDn,
            final PrivateKey issuerPrivateKey,
            final boolean isCa,
            final BigInteger serial
    )
            throws Exception
    {
        final Date notBefore = Date.from( Instant.now().minus( 1, ChronoUnit.DAYS ) );
        final Date notAfter = Date.from( Instant.now().plus( 365, ChronoUnit.DAYS ) );

        final JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuerDn, serial, notBefore, notAfter, subjectDn, subjectPublicKey );

        builder.addExtension( Extension.basicConstraints, true, new BasicConstraints( isCa ) );
        if ( isCa )
        {
            // keyCertSign is what X509Utils.certIsRootCA() keys on, and what PKIX requires of CA certs
            builder.addExtension( Extension.keyUsage, true, new KeyUsage( KeyUsage.keyCertSign | KeyUsage.cRLSign ) );
        }

        final ContentSigner signer = new JcaContentSignerBuilder( "SHA256withRSA" ).build( issuerPrivateKey );
        final X509CertificateHolder holder = builder.build( signer );
        return new JcaX509CertificateConverter().getCertificate( holder );
    }

    private static PwmTrustManager caOnlyTrustManager( final List<X509Certificate> configuredCerts )
    {
        return PwmTrustManager.createPwmTrustManager( CA_ONLY_SETTINGS, configuredCerts );
    }

    @Test
    public void configuringOnlyRootValidatesChainWithServerSuppliedIntermediate() throws Exception
    {
        final PwmTrustManager trustManager = caOnlyTrustManager( Collections.singletonList( rootCert ) );

        // server presents leaf + intermediate (typical); only the root is configured
        trustManager.checkServerTrusted( new X509Certificate[] { leafCert, intermediateCert }, "RSA" );
    }

    @Test
    public void configuringRootAndIntermediateStillValidates() throws Exception
    {
        final PwmTrustManager trustManager = caOnlyTrustManager( java.util.Arrays.asList( rootCert, intermediateCert ) );

        trustManager.checkServerTrusted( new X509Certificate[] { leafCert, intermediateCert }, "RSA" );
    }

    @Test
    public void serverSendingFullChainIncludingRootValidates() throws Exception
    {
        final PwmTrustManager trustManager = caOnlyTrustManager( Collections.singletonList( rootCert ) );

        trustManager.checkServerTrusted( new X509Certificate[] { leafCert, intermediateCert, rootCert }, "RSA" );
    }

    @Test
    public void chainNotTrustedByConfiguredRootIsRejected()
    {
        final PwmTrustManager trustManager = caOnlyTrustManager( Collections.singletonList( unrelatedRootCert ) );

        try
        {
            trustManager.checkServerTrusted( new X509Certificate[] { leafCert, intermediateCert }, "RSA" );
            Assert.fail( "expected CertificateException for a chain not anchored to the configured CA" );
        }
        catch ( final CertificateException e )
        {
            // expected
        }
    }

    @Test
    public void emptyConfiguredTrustStoreIsRejected()
    {
        final PwmTrustManager trustManager = caOnlyTrustManager( Collections.emptyList() );

        try
        {
            trustManager.checkServerTrusted( new X509Certificate[] { leafCert, intermediateCert }, "RSA" );
            Assert.fail( "expected CertificateException when no CA certificates are configured" );
        }
        catch ( final CertificateException e )
        {
            // expected
        }
    }
}
