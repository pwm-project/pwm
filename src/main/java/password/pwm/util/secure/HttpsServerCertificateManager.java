/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.util.secure;

import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.PrivateKeyValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.JsonUtil;
import password.pwm.util.PasswordData;
import password.pwm.util.StringUtil;
import password.pwm.util.X509Utils;
import password.pwm.util.logging.PwmLogger;

import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * {@link }
 */
public class HttpsServerCertificateManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass(HttpsServerCertificateManager.class);

    private static boolean bouncyCastleInitialized;

    private static void initBouncyCastleProvider()
    {
        if (!bouncyCastleInitialized)
        {
            Security.addProvider(new BouncyCastleProvider());
            bouncyCastleInitialized = true;
        }
    }


    public static KeyStore keyStoreForApplication(final PwmApplication pwmApplication, final PasswordData passwordData, final String alias) throws PwmUnrecoverableException {
        KeyStore keyStore = null;
        keyStore = exportKey(pwmApplication.getConfig(), KeyStoreFormat.JKS, passwordData, alias);

        if (keyStore == null) {
            keyStore = makeSelfSignedCert(pwmApplication, passwordData, alias);
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
        final PrivateKeyCertificate privateKeyCertificate = configuration.readSettingAsPrivateKey(PwmSetting.HTTPS_CERT);
        if (privateKeyCertificate == null) {
            return null;
        }

        final KeyStore.PasswordProtection passwordProtection = new KeyStore.PasswordProtection(passwordData.getStringValue().toCharArray());
        try {
            final KeyStore keyStore = KeyStore.getInstance(format.toString());
            keyStore.load(null, passwordData.getStringValue().toCharArray()); //load of null is required to init keystore.
            keyStore.setEntry(
                    alias,
                    new KeyStore.PrivateKeyEntry(
                            privateKeyCertificate.getKey(),
                            privateKeyCertificate.getCertificates()
                    ),
                    passwordProtection
            );
            return keyStore;
        } catch (Exception e)
        {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, "error generating keystore file;: " + e.getMessage()));
        }
    }

    private static KeyStore makeSelfSignedCert(final PwmApplication pwmApplication, final PasswordData password, final String alias)
            throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmApplication.getConfig();

        try
        {
            final SelfCertGenerator selfCertGenerator = new SelfCertGenerator(configuration);
            return selfCertGenerator.makeSelfSignedCert(pwmApplication, password, alias);
        } catch (Exception e)
        {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CERTIFICATE_ERROR, "unable to generate self signed certificate: " + e.getMessage()));
        }
    }

    public static class StoredCertData implements Serializable
    {
        private final X509Certificate x509Certificate;
        private String keypairb64;

        public StoredCertData(X509Certificate x509Certificate, KeyPair keypair)
                throws IOException
        {
            this.x509Certificate = x509Certificate;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(keypair);
            final byte[] ba = baos.toByteArray();
            keypairb64 = StringUtil.base64Encode(ba);
        }

        public X509Certificate getX509Certificate()
        {
            return x509Certificate;
        }

        public KeyPair getKeypair()
                throws IOException, ClassNotFoundException
        {
            final byte[] ba = StringUtil.base64Decode(keypairb64);
            final ByteArrayInputStream bais = new ByteArrayInputStream(ba);
            final ObjectInputStream ois = new ObjectInputStream(bais);
            return (KeyPair) ois.readObject();
        }
    }


    public static class SelfCertGenerator
    {
        private final Configuration config;

        public SelfCertGenerator(Configuration config)
        {
            this.config = config;
        }

        public KeyStore makeSelfSignedCert(final PwmApplication pwmApplication, final PasswordData password, final String alias)
                throws Exception
        {
            final String cnName = makeSubjectName();
            final KeyStore keyStore = KeyStore.getInstance("jks");
            keyStore.load(null, password.getStringValue().toCharArray());
            StoredCertData storedCertData = pwmApplication.readAppAttribute(PwmApplication.AppAttribute.HTTPS_SELF_CERT, StoredCertData.class);
            if (storedCertData != null)
            {
                if (!cnName.equals(storedCertData.getX509Certificate().getSubjectDN().getName()))
                {
                    LOGGER.info("replacing stored self cert, subject name does not match configured site url");
                    storedCertData = null;
                } else if (storedCertData.getX509Certificate().getNotBefore().after(new Date()))
                {
                    LOGGER.info("replacing stored self cert, not-before date is in the future");
                    storedCertData = null;
                } else if (storedCertData.getX509Certificate().getNotAfter().before(new Date()))
                {
                    LOGGER.info("replacing stored self cert, not-after date is in the past");
                    storedCertData = null;
                }
            }

            if (storedCertData == null)
            {
                storedCertData = makeSelfSignedCert(cnName);
                pwmApplication.writeAppAttribute(PwmApplication.AppAttribute.HTTPS_SELF_CERT, storedCertData);
            }

            keyStore.setKeyEntry(
                    alias,
                    storedCertData.getKeypair().getPrivate(),
                    password.getStringValue().toCharArray(),
                    new X509Certificate[]{storedCertData.getX509Certificate()}
            );
            return keyStore;
        }

        public String makeSubjectName()
                throws Exception
        {
            String cnName = PwmConstants.PWM_APP_NAME.toLowerCase() + ".example.com";
            {
                final String siteURL = config.readSettingAsString(PwmSetting.PWM_SITE_URL);
                if (siteURL != null && !siteURL.isEmpty())
                {
                    try
                    {
                        final URI uri = new URI(siteURL);
                        if (uri.getHost() != null && !uri.getHost().isEmpty())
                        {
                            cnName = uri.getHost();
                        }
                    } catch (URISyntaxException e)
                    {
                        // disregard
                    }
                }
            }
            return cnName;
        }


        public StoredCertData makeSelfSignedCert(final String cnName)
                throws Exception
        {
            initBouncyCastleProvider();

            LOGGER.debug("creating self-signed certificate with cn of " + cnName);
            final KeyPair keyPair = generateRSAKeyPair(config);
            final long futureSeconds = Long.parseLong(config.readAppProperty(AppProperty.SECURITY_HTTPSSERVER_SELF_FUTURESECONDS));
            final X509Certificate certificate = generateV3Certificate(keyPair, cnName, futureSeconds);
            return new StoredCertData(certificate, keyPair);
        }


        public static X509Certificate generateV3Certificate(final KeyPair pair, final String cnValue, final long futureSeconds)
                throws Exception
        {
            final X500NameBuilder subjectName = new X500NameBuilder(BCStyle.INSTANCE);
            subjectName.addRDN(BCStyle.CN, cnValue);

            final SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddhhmmss");
            final String serNumStr = formatter.format(new Date(System.currentTimeMillis()));
            final BigInteger serialNumber = new BigInteger(serNumStr);

            final Date notBefore = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)); // 2 days in the past
            final Date notAfter = new Date(System.currentTimeMillis() + (futureSeconds * 1000));

            final X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(subjectName.build(), serialNumber, notBefore, notAfter, subjectName.build(), pair.getPublic());

            final BasicConstraints basic = new BasicConstraints(false); // not a CA
            certGen.addExtension(Extension.basicConstraints, true, basic.getEncoded()); // OID, critical, ASN.1 encoded value

            final KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment); // sign and key encipher
            certGen.addExtension(Extension.keyUsage, true, keyUsage.getEncoded()); // OID, critical, ASN.1 encoded value

            final ExtendedKeyUsage extKeyUsage = new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth); // server authentication
            certGen.addExtension(Extension.extendedKeyUsage, true, extKeyUsage.getEncoded()); // OID, critical, ASN.1 encoded value

            final ContentSigner sigGen = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC").build(pair.getPrivate());

            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certGen.build(sigGen));
        }

        static KeyPair generateRSAKeyPair(final Configuration config)
                throws Exception
        {
            final int keySize = Integer.parseInt(config.readAppProperty(AppProperty.SECURITY_HTTPSSERVER_SELF_KEY_SIZE));
            final String keyAlg = config.readAppProperty(AppProperty.SECURITY_HTTPSSERVER_SELF_ALG);
            final KeyPairGenerator kpGen = KeyPairGenerator.getInstance(keyAlg, "BC");
            kpGen.initialize(keySize, new SecureRandom());
            return kpGen.generateKeyPair();
        }
    }


    public enum KeyStoreFormat {
        PKCS12,
        JKS,
    }

    public static void importKey(
            final StoredConfiguration storedConfiguration,
            final KeyStoreFormat keyStoreFormat,
            final InputStream inputStream,
            final PasswordData password,
            final String alias
    ) throws PwmUnrecoverableException {
        final PrivateKeyCertificate privateKeyCertificate;
        try {
            final KeyStore keyStore = KeyStore.getInstance(keyStoreFormat.toString());
            keyStore.load(inputStream, password.getStringValue().toCharArray());

            final String effectiveAlias;
            {
                final List<String> allAliases = new ArrayList<>();
                for (final Enumeration enu = keyStore.aliases(); enu.hasMoreElements(); ) {
                    final String value = (String) enu.nextElement();
                    allAliases.add(value);
                }
                effectiveAlias = allAliases.size() == 1 ? allAliases.iterator().next() : alias;
            }

            final KeyStore.PasswordProtection passwordProtection = new KeyStore.PasswordProtection(password.getStringValue().toCharArray());
            final KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)keyStore.getEntry(effectiveAlias, passwordProtection);
            if (entry == null) {
                final String errorMsg = "unable to import https key entry with alias '" + alias + "'";
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CERTIFICATE_ERROR, errorMsg, new String[]{"no key entry alias '" + alias + "' in keystore"}));
            }

            final PrivateKey key = entry.getPrivateKey();
            final X509Certificate[] certificates = (X509Certificate[])entry.getCertificateChain();

            LOGGER.debug("importing certificate chain: " + JsonUtil.serializeCollection(X509Utils.makeDebugInfoMap(certificates)));
            privateKeyCertificate = new PrivateKeyCertificate(certificates, key);
        } catch (Exception e) {
            final String errorMsg = "unable to load configured https certificate: " + e.getMessage();
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CERTIFICATE_ERROR, errorMsg, new String[]{e.getMessage()}));
        }

        final StoredValue storedValue = new PrivateKeyValue(privateKeyCertificate);
        storedConfiguration.writeSetting(PwmSetting.HTTPS_CERT,storedValue,null);
    }

}
