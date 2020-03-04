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
import password.pwm.AppAttribute;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.PrivateKeyValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.PwmDateFormat;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HttpsServerCertificateManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HttpsServerCertificateManager.class );

    private static volatile boolean bouncyCastleInitialized;

    private static synchronized void initBouncyCastleProvider( )
    {
        if ( !bouncyCastleInitialized )
        {
            Security.addProvider( new BouncyCastleProvider() );
            bouncyCastleInitialized = true;
        }
    }


    public static KeyStore keyStoreForApplication(
            final PwmApplication pwmApplication,
            final PasswordData passwordData,
            final String alias
    )
            throws PwmUnrecoverableException
    {
        KeyStore keyStore = exportKey( pwmApplication.getConfig(), KeyStoreFormat.JKS, passwordData, alias );

        if ( keyStore == null )
        {
            keyStore = makeSelfSignedCert( pwmApplication, passwordData, alias );
        }

        return keyStore;
    }

    private static KeyStore exportKey(
            final Configuration configuration,
            final KeyStoreFormat format,
            final PasswordData passwordData,
            final String alias
    )
            throws PwmUnrecoverableException
    {
        final PrivateKeyCertificate privateKeyCertificate = configuration.readSettingAsPrivateKey( PwmSetting.HTTPS_CERT );
        if ( privateKeyCertificate == null )
        {
            return null;
        }

        final KeyStore.PasswordProtection passwordProtection = new KeyStore.PasswordProtection( passwordData.getStringValue().toCharArray() );
        try
        {
            final KeyStore keyStore = KeyStore.getInstance( format.toString() );

            //load of null is required to init keystore.
            keyStore.load( null, passwordData.getStringValue().toCharArray() );

            keyStore.setEntry(
                    alias,
                    new KeyStore.PrivateKeyEntry(
                            privateKeyCertificate.getKey(),
                            privateKeyCertificate.getCertificates().toArray( new X509Certificate[ privateKeyCertificate.getCertificates().size() ] )
                    ),
                    passwordProtection
            );
            return keyStore;
        }
        catch ( final Exception e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, "error generating keystore file;: " + e.getMessage() ) );
        }
    }

    private static KeyStore makeSelfSignedCert( final PwmApplication pwmApplication, final PasswordData password, final String alias )
            throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmApplication.getConfig();

        try
        {
            final SelfCertGenerator selfCertGenerator = new SelfCertGenerator( configuration );
            return selfCertGenerator.makeSelfSignedCert( pwmApplication, password, alias );
        }
        catch ( final Exception e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_CERTIFICATE_ERROR, "unable to generate self signed certificate: " + e.getMessage() ) );
        }
    }

    public static class StoredCertData implements Serializable
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


    public static class SelfCertGenerator
    {
        private final Configuration config;

        public SelfCertGenerator( final Configuration config )
        {
            this.config = config;
        }

        public KeyStore makeSelfSignedCert( final PwmApplication pwmApplication, final PasswordData password, final String alias )
                throws Exception
        {
            final String cnName = makeSubjectName();
            final KeyStore keyStore = KeyStore.getInstance( "jks" );
            keyStore.load( null, password.getStringValue().toCharArray() );
            StoredCertData storedCertData = pwmApplication.readAppAttribute( AppAttribute.HTTPS_SELF_CERT, StoredCertData.class );
            if ( storedCertData != null )
            {
                if ( !cnName.equals( storedCertData.getX509Certificate().getSubjectDN().getName() ) )
                {
                    LOGGER.info( () -> "replacing stored self cert, subject name does not match configured site url" );
                    storedCertData = null;
                }
                else if ( storedCertData.getX509Certificate().getNotBefore().after( new Date() ) )
                {
                    LOGGER.info( () -> "replacing stored self cert, not-before date is in the future" );
                    storedCertData = null;
                }
                else if ( storedCertData.getX509Certificate().getNotAfter().before( new Date() ) )
                {
                    LOGGER.info( () -> "replacing stored self cert, not-after date is in the past" );
                    storedCertData = null;
                }
            }

            if ( storedCertData == null )
            {
                storedCertData = makeSelfSignedCert( cnName );
                pwmApplication.writeAppAttribute( AppAttribute.HTTPS_SELF_CERT, storedCertData );
            }

            keyStore.setKeyEntry(
                    alias,
                    storedCertData.getKeypair().getPrivate(),
                    password.getStringValue().toCharArray(),
                    new X509Certificate[]
                            {
                                    storedCertData.getX509Certificate(),
                            }
            );
            return keyStore;
        }

        public String makeSubjectName( )
                throws Exception
        {
            String cnName = PwmConstants.PWM_APP_NAME.toLowerCase() + ".example.com";
            {
                final String siteURL = config.readSettingAsString( PwmSetting.PWM_SITE_URL );
                if ( siteURL != null && !siteURL.isEmpty() )
                {
                    try
                    {
                        final URI uri = new URI( siteURL );
                        if ( uri.getHost() != null && !uri.getHost().isEmpty() )
                        {
                            cnName = uri.getHost();
                        }
                    }
                    catch ( final URISyntaxException e )
                    {
                        // disregard
                    }
                }
            }
            return cnName;
        }


        public StoredCertData makeSelfSignedCert( final String cnName )
                throws Exception
        {
            initBouncyCastleProvider();

            LOGGER.debug( () -> "creating self-signed certificate with cn of " + cnName );
            final KeyPair keyPair = generateRSAKeyPair( config );
            final long futureSeconds = Long.parseLong( config.readAppProperty( AppProperty.SECURITY_HTTPSSERVER_SELF_FUTURESECONDS ) );
            final X509Certificate certificate = generateV3Certificate( keyPair, cnName, futureSeconds );
            return new StoredCertData( certificate, keyPair );
        }


        public static X509Certificate generateV3Certificate( final KeyPair pair, final String cnValue, final long futureSeconds )
                throws Exception
        {
            final X500NameBuilder subjectName = new X500NameBuilder( BCStyle.INSTANCE );
            subjectName.addRDN( BCStyle.CN, cnValue );

            final BigInteger serialNumber = makeSerialNumber();


            // 2 days in the past
            final Date notBefore = new Date( System.currentTimeMillis() - TimeUnit.DAYS.toMillis( 2 ) );

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

        private static BigInteger makeSerialNumber()
        {
            final PwmDateFormat formatter = PwmDateFormat.newPwmDateFormat( "yyyyMMddhhmmss" );
            final String serNumStr = formatter.format( Instant.now() );
            return new BigInteger( serNumStr );
        }

        static KeyPair generateRSAKeyPair( final Configuration config )
                throws Exception
        {
            final int keySize = Integer.parseInt( config.readAppProperty( AppProperty.SECURITY_HTTPSSERVER_SELF_KEY_SIZE ) );
            final String keyAlg = config.readAppProperty( AppProperty.SECURITY_HTTPSSERVER_SELF_ALG );
            final KeyPairGenerator kpGen = KeyPairGenerator.getInstance( keyAlg, "BC" );
            kpGen.initialize( keySize, new SecureRandom() );
            return kpGen.generateKeyPair();
        }
    }


    public enum KeyStoreFormat
    {
        PKCS12,
        JKS,
    }

    public static void importKey(
            final StoredConfigurationModifier storedConfiguration,
            final KeyStoreFormat keyStoreFormat,
            final InputStream inputStream,
            final PasswordData password,
            final String alias
    )
            throws PwmUnrecoverableException
    {
        final char[] charPassword = password == null ? new char[ 0 ] : password.getStringValue().toCharArray();
        final PrivateKeyCertificate privateKeyCertificate;
        try
        {
            final KeyStore keyStore = KeyStore.getInstance( keyStoreFormat.toString() );
            keyStore.load( inputStream, charPassword );

            final String effectiveAlias;
            {
                final List<String> allAliases = new ArrayList<>();
                for ( final Enumeration<String> aliasEnum = keyStore.aliases(); aliasEnum.hasMoreElements(); )
                {
                    final String value = aliasEnum.nextElement();
                    allAliases.add( value );
                }
                effectiveAlias = allAliases.size() == 1 ? allAliases.iterator().next() : alias;
            }

            final KeyStore.PasswordProtection passwordProtection = new KeyStore.PasswordProtection( charPassword );
            final KeyStore.PrivateKeyEntry entry = ( KeyStore.PrivateKeyEntry ) keyStore.getEntry( effectiveAlias, passwordProtection );
            if ( entry == null )
            {
                final String errorMsg = "unable to import https key entry with alias '" + alias + "'";
                throw new PwmUnrecoverableException( new ErrorInformation(
                        PwmError.ERROR_CERTIFICATE_ERROR,
                        errorMsg,
                        new String[]
                                {
                                        "no key entry alias '" + alias + "' in keystore",
                                }
                ) );
            }

            final PrivateKey key = entry.getPrivateKey();
            final List<X509Certificate> certificates = Arrays.asList( ( X509Certificate[] ) entry.getCertificateChain() );

            LOGGER.debug( () -> "importing certificate chain: " + JsonUtil.serializeCollection( X509Utils.makeDebugInfoMap( certificates ) ) );
            privateKeyCertificate = new PrivateKeyCertificate( certificates, key );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "unable to load configured https certificate: " + e.getMessage();
            final String[] errorDetail = new String[]
                    {
                            e.getMessage(),
                    };
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_CERTIFICATE_ERROR, errorMsg, errorDetail ) );
        }

        final StoredValue storedValue = new PrivateKeyValue( privateKeyCertificate );
        storedConfiguration.writeSetting( PwmSetting.HTTPS_CERT, null, storedValue, null );
    }

}
