/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.FileValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class HttpsServerCertificateManager {
    private static PwmLogger LOGGER = PwmLogger.forClass(HttpsServerCertificateManager.class);

    private static final String KEYSTORE_ALIAS = "https";

    private static boolean bouncyCastleInitialized;

    private final PwmApplication pwmApplication;

    public HttpsServerCertificateManager(final PwmApplication pwmApplication) {
        this.pwmApplication = pwmApplication;
    }

    private static void initBouncyCastleProvider() {
        if (!bouncyCastleInitialized) {
            Security.addProvider(new BouncyCastleProvider());
            bouncyCastleInitialized = true;
        }
    }

    public KeyStore configToKeystore() throws PwmUnrecoverableException {
        final Configuration configuration = pwmApplication.getConfig();
        final PasswordData keystorePassword = configuration.readSettingAsPassword(PwmSetting.HTTPS_CERT_PASSWORD);
        if (keystorePassword == null || keystorePassword.getStringValue().isEmpty()) {
            final String errorMsg = "https keystore password is not configured";
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorMsg, new String[]{errorMsg}));
        }

        Map<FileValue.FileInformation, FileValue.FileContent> files = configuration.readSettingAsFile(PwmSetting.HTTPS_CERT_PKCS12);
        if (files == null || files.isEmpty()) {
            final String errorMsg = "https keystore pkcs12 file is not present";
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorMsg, new String[]{errorMsg}));
        }

        final FileValue.FileInformation fileInformation = files.keySet().iterator().next();
        final FileValue.FileContent fileContent = files.get(fileInformation);

        final KeyStore keyStore;
        try {
            final InputStream stream = new ByteArrayInputStream(fileContent.getContents());
            keyStore = KeyStore.getInstance("pkcs12", "SunJSSE");
            keyStore.load(stream, keystorePassword.getStringValue().toCharArray());

            if (!keyStore.containsAlias(KEYSTORE_ALIAS)) {
                final String errorMsg = "pkcs12 store does not does not contain a certificate with \"" + KEYSTORE_ALIAS + "\" alias";
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, errorMsg, new String[]{errorMsg}));
            }
            return keyStore;
        } catch (IOException | NoSuchAlgorithmException | CertificateException | NoSuchProviderException | KeyStoreException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR, "error parsing pkcs12 file: " + e.getMessage()));
        }
    }

    public KeyStore makeSelfSignedCert(final String password)
            throws PwmUnrecoverableException
    {
        final Configuration configuration = pwmApplication.getConfig();

        try {
            final SelfCertGenerator selfCertGenerator = new SelfCertGenerator(configuration);
            return selfCertGenerator.makeSelfSignedCert(pwmApplication, password);
        } catch (Exception e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_CERTIFICATE_ERROR,"unable to generate self signed certificate: " + e.getMessage()));
        }
    }

    public static class StoredCertData implements Serializable {
        private X509Certificate x509Certificate;
        private String keypairb64;

        public StoredCertData(X509Certificate x509Certificate, KeyPair keypair)
                throws IOException
        {
            this.x509Certificate = x509Certificate;
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final ObjectOutputStream oos =  new ObjectOutputStream(baos);
            oos.writeObject(keypair);
            final byte[] ba = baos.toByteArray();
            keypairb64 = StringUtil.base64Encode(ba);
        }

        public X509Certificate getX509Certificate() {
            return x509Certificate;
        }

        public KeyPair getKeypair()
                throws IOException, ClassNotFoundException
        {
            final byte[] ba = StringUtil.base64Decode(keypairb64);
            final ByteArrayInputStream bais = new ByteArrayInputStream(ba);
            final ObjectInputStream ois = new ObjectInputStream(bais);
            return (KeyPair)ois.readObject();
        }
    }


    public static class SelfCertGenerator {
        private final Configuration config;

        public SelfCertGenerator(Configuration config) {
            this.config = config;
        }

        public KeyStore makeSelfSignedCert(final PwmApplication pwmApplication, final String password)
                throws Exception
        {
            final String cnName = makeSubjectName();
            final KeyStore keyStore = KeyStore.getInstance("jks");
            keyStore.load(null, password.toCharArray());
            StoredCertData storedCertData = pwmApplication.readAppAttribute(PwmApplication.AppAttribute.HTTPS_SELF_CERT, StoredCertData.class);
            if (storedCertData != null) {
                if (!cnName.equals(storedCertData.getX509Certificate().getSubjectDN().getName())) {
                    LOGGER.info("replacing stored self cert, subject name does not match configured site url");
                    storedCertData = null;
                } else if (storedCertData.getX509Certificate().getNotBefore().after(new Date())) {
                    LOGGER.info("replacing stored self cert, not-before date is in the future");
                    storedCertData = null;
                } else if (storedCertData.getX509Certificate().getNotAfter().before(new Date())) {
                    LOGGER.info("replacing stored self cert, not-after date is in the past");
                    storedCertData = null;
                }
            }

            if (storedCertData == null) {
                storedCertData = makeSelfSignedCert(cnName);
                pwmApplication.writeAppAttribute(PwmApplication.AppAttribute.HTTPS_SELF_CERT, storedCertData);
            }

            keyStore.setKeyEntry(
                    KEYSTORE_ALIAS,
                    storedCertData.getKeypair().getPrivate(),
                    password.toCharArray(),
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
                if (siteURL != null && !siteURL.isEmpty()) {
                    try {
                        URI uri = new URI(siteURL);
                        if (uri.getHost() != null && !uri.getHost().isEmpty()) {
                            cnName = uri.getHost();
                        }
                    } catch (URISyntaxException e) {
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
            X500NameBuilder subjectName = new X500NameBuilder(BCStyle.INSTANCE);
            subjectName.addRDN(BCStyle.CN, cnValue);

            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddhhmmss");
            String serNumStr = formatter.format(new Date(System.currentTimeMillis()));
            BigInteger serialNumber = new BigInteger(serNumStr);

            Date notBefore = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2)); // 2 days in the past
            Date notAfter = new Date(System.currentTimeMillis() + futureSeconds);

            X509v3CertificateBuilder certGen = new JcaX509v3CertificateBuilder(subjectName.build(), serialNumber, notBefore, notAfter, subjectName.build(), pair.getPublic());

            BasicConstraints basic = new BasicConstraints(false); // not a CA
            certGen.addExtension(Extension.basicConstraints, true, basic.getEncoded()); // OID, critical, ASN.1 encoded value

            KeyUsage keyUsage = new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment); // sign and key encipher
            certGen.addExtension(Extension.keyUsage, true, keyUsage.getEncoded()); // OID, critical, ASN.1 encoded value

            ExtendedKeyUsage extKeyUsage = new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth); // server authentication
            certGen.addExtension(Extension.extendedKeyUsage, true, extKeyUsage.getEncoded()); // OID, critical, ASN.1 encoded value

            ContentSigner sigGen = new JcaContentSignerBuilder("SHA256WithRSAEncryption").setProvider("BC").build(pair.getPrivate());

            return new JcaX509CertificateConverter().setProvider("BC").getCertificate(certGen.build(sigGen));
        }

        static KeyPair generateRSAKeyPair(final Configuration config)
                throws Exception
        {
            final int keySize = Integer.parseInt(config.readAppProperty(AppProperty.SECURITY_HTTPSSERVER_SELF_KEY_SIZE));
            final String keyAlg = config.readAppProperty(AppProperty.SECURITY_HTTPSSERVER_SELF_ALG);
            KeyPairGenerator kpGen = KeyPairGenerator.getInstance(keyAlg, "BC");
            kpGen.initialize(keySize, new SecureRandom());
            return kpGen.generateKeyPair();
        }
    }
}
