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

package password.pwm.util;

import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmURL;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class X509Utils {
    private static final PwmLogger LOGGER = PwmLogger.forClass(X509Utils.class);

    public static X509Certificate[] readRemoteCertificates(final URI uri)
            throws PwmOperationalException
    {
        final String host = uri.getHost();
        final int port = PwmURL.portForUriSchema(uri);

        return readRemoteCertificates(host, port);
    }



    public static X509Certificate[] readRemoteCertificates(final String host, final int port)
            throws PwmOperationalException
    {
        LOGGER.debug("ServerCertReader: beginning certificate read procedure to import certificates from host=" + host + ", port=" + port);
        final CertReaderTrustManager certReaderTm = new CertReaderTrustManager();
        try { // use custom trust manager to read the certificates
            final SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{certReaderTm}, new SecureRandom());
            final SSLSocketFactory factory = ctx.getSocketFactory();
            final SSLSocket sslSock = (SSLSocket) factory.createSocket(host,port);
            LOGGER.debug("ServerCertReader: socket established to host=" + host + ", port=" + port);
            sslSock.isConnected();
            LOGGER.debug("ServerCertReader: connected to host=" + host + ", port=" + port);
            sslSock.startHandshake();
            LOGGER.debug("ServerCertReader: handshake completed to host=" + host + ", port=" + port);
            sslSock.close();
            LOGGER.debug("ServerCertReader: certificate information read from host=" + host + ", port=" + port);
        } catch (Exception e) {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("unable to read server certificates from host=");
            errorMsg.append(host).append(", port=").append(port);
            errorMsg.append(" error: ");
            errorMsg.append(e.getMessage());
            LOGGER.error("ServerCertReader: " + errorMsg);
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CERTIFICATE_ERROR, errorMsg.toString(), new String[]{errorMsg.toString()});
            throw new PwmOperationalException(errorInformation);
        }
        final X509Certificate[] certs = certReaderTm.getCertificates();
        if (certs == null) {
            LOGGER.debug("ServerCertReader: unable to read certificates: null returned from CertReaderTrustManager.getCertificates()");
        } else {
            for (final X509Certificate certificate : certs) {
                LOGGER.debug("ServerCertReader: read x509 Certificate from host=" + host + ", port=" + port + ": \n" + certificate.toString());
            }
        }
        LOGGER.debug("ServerCertReader: process completed");
        return certs;
    }

    public static boolean testIfLdapServerCertsInDefaultKeystore(final URI serverURI) {
        final String ldapHost = serverURI.getHost();
        final int ldapPort = serverURI.getPort();
        try { // use default socket factory to test if certs work with it
            final SSLSocketFactory factory = (SSLSocketFactory)SSLSocketFactory.getDefault();
            final SSLSocket sslSock = (SSLSocket) factory.createSocket(ldapHost,ldapPort);
            sslSock.isConnected();
            sslSock.getOutputStream().write("data!".getBytes());
            sslSock.close();
            return true;
        } catch (Exception e) {
            /* noop */
        }
        return false;
    }

    private static class CertReaderTrustManager implements X509TrustManager {
        private X509Certificate[] certificates;
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {}

        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            certificates = chain;
        }

        public X509Certificate[] getCertificates() {
            return certificates;
        }
    }

    public static class PromiscuousTrustManager implements X509TrustManager {
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }

        public void checkClientTrusted(X509Certificate[] certs, String authType) {
            logMsg(certs,authType);
        }

        public void checkServerTrusted(X509Certificate[] certs, String authType) {
            logMsg(certs,authType);
        }

        private static void logMsg(X509Certificate[] certs, String authType) {
            if (certs != null) {
                for (final X509Certificate cert : certs) {
                    try {
                        LOGGER.warn("blind trusting certificate during authType=" + authType + ", subject=" + cert.getSubjectDN().toString());
                    } catch (Exception e) {
                        LOGGER.error("error while decoding certificate: " + e.getMessage());
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
    }

    public static class CertMatchingTrustManager implements X509TrustManager {
        final X509Certificate[] certificates;
        final boolean validateTimestamps;

        public CertMatchingTrustManager(final Configuration config, final X509Certificate[] certificates) {
            this.certificates = certificates;
            validateTimestamps = config != null && Boolean.parseBoolean(config.readAppProperty(AppProperty.SECURITY_CERTIFICATES_VALIDATE_TIMESTAMPS));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            if (x509Certificates == null) {
                final String errorMsg = "no certificates in configuration trust store for this operation";
                throw new CertificateException(errorMsg);
            }

            for (X509Certificate loopCert : x509Certificates) {
                boolean certTrusted = false;
                for (X509Certificate storedCert : certificates) {
                    if (loopCert.equals(storedCert)) {
                        if (validateTimestamps) {
                            loopCert.checkValidity();
                        }
                        certTrusted = true;
                    }
                }
                if (!certTrusted) {
                    final String errorMsg = "server certificate {subject=" + loopCert.getSubjectDN().getName() + "} does not match a certificate in the configuration trust store.";
                    throw new CertificateException(errorMsg);
                }
                //LOGGER.trace("trusting configured certificate: " + makeDebugText(loopCert));
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];  //To change body of implemented methods use File | Settings | File Templates.
        }
    }

    public static String hexSerial(final X509Certificate x509Certificate) {
        String result = x509Certificate.getSerialNumber().toString(16).toUpperCase();
        while (result.length() % 2 != 0) {
            result = "0" + result;
        }
        return result;
    }

    public static String makeDetailText(final X509Certificate x509Certificate)
            throws CertificateEncodingException, PwmUnrecoverableException
    {
        return x509Certificate.toString()
                + "\n:MD5 checksum: " + SecureEngine.hash(new ByteArrayInputStream(x509Certificate.getEncoded()), PwmHashAlgorithm.MD5)
                + "\n:SHA1 checksum: " + SecureEngine.hash(new ByteArrayInputStream(x509Certificate.getEncoded()), PwmHashAlgorithm.SHA1)
                + "\n:SHA2-256 checksum: " + SecureEngine.hash(new ByteArrayInputStream(x509Certificate.getEncoded()), PwmHashAlgorithm.SHA256)
                + "\n:SHA2-512 checksum: " + SecureEngine.hash(new ByteArrayInputStream(x509Certificate.getEncoded()), PwmHashAlgorithm.SHA512);


    }

    public static String makeDebugText(final X509Certificate x509Certificate) {
        return "subject=" + x509Certificate.getSubjectDN().getName() + ", serial=" + x509Certificate.getSerialNumber();
    }

    enum CertDebugInfoKey {
        subject,
        serial,
        issuer,
        issueDate,
        expireDate,
        md5Hash,
        sha1Hash,
        sha512Hash,
        detail,
    }

    public enum DebugInfoFlag {
        IncludeCertificateDetail
    }

    public static List<Map<String,String>> makeDebugInfoMap(final X509Certificate[] certificates, DebugInfoFlag... flags) {
        final List<Map<String,String>> returnList = new ArrayList<>();
        if (certificates != null) {
            for (final X509Certificate cert : certificates) {
                returnList.add(makeDebugInfoMap(cert, flags));
            }
        }
        return returnList;
    }

    public static Map<String,String> makeDebugInfoMap(final X509Certificate cert, DebugInfoFlag... flags) {
        final Map<String,String> returnMap = new LinkedHashMap<>();
        returnMap.put(CertDebugInfoKey.subject.toString(), cert.getSubjectDN().toString());
        returnMap.put(CertDebugInfoKey.serial.toString(), X509Utils.hexSerial(cert));
        returnMap.put(CertDebugInfoKey.issuer.toString(), cert.getIssuerDN().toString());
        returnMap.put(CertDebugInfoKey.issueDate.toString(), PwmConstants.DEFAULT_DATETIME_FORMAT.format(cert.getNotBefore()));
        returnMap.put(CertDebugInfoKey.expireDate.toString(), PwmConstants.DEFAULT_DATETIME_FORMAT.format(cert.getNotAfter()));
        try {
            returnMap.put(CertDebugInfoKey.md5Hash.toString(), SecureEngine.hash(new ByteArrayInputStream(cert.getEncoded()), PwmHashAlgorithm.MD5));
            returnMap.put(CertDebugInfoKey.sha1Hash.toString(), SecureEngine.hash(new ByteArrayInputStream(cert.getEncoded()), PwmHashAlgorithm.SHA1));
            returnMap.put(CertDebugInfoKey.sha512Hash.toString(), SecureEngine.hash(new ByteArrayInputStream(cert.getEncoded()),
                    PwmHashAlgorithm.SHA512));
            if (Helper.enumArrayContainsValue(flags, DebugInfoFlag.IncludeCertificateDetail)) {
                returnMap.put(CertDebugInfoKey.detail.toString(),X509Utils.makeDetailText(cert));
            }
        } catch (PwmUnrecoverableException | CertificateEncodingException e) {
            LOGGER.warn("error generating hash for certificate: " + e.getMessage());
        }
        return returnMap;
    }

    enum KeyDebugInfoKey {
        algorithm,
        format,
    }

    public static Map<String,String> makeDebugInfoMap(final PrivateKey key) {
        final Map<String,String> returnMap = new LinkedHashMap<>();
        returnMap.put(KeyDebugInfoKey.algorithm.toString(), key.getAlgorithm());
        returnMap.put(KeyDebugInfoKey.format.toString(), key.getFormat());
        return returnMap;
    }

    public static X509Certificate certificateFromBase64(final String b64encodedStr)
            throws CertificateException, IOException
    {
        final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        final byte[] certByteValue = StringUtil.base64Decode(b64encodedStr);
        return (X509Certificate)certificateFactory.generateCertificate(new ByteArrayInputStream(certByteValue));
    }

    public static String certificateToBase64(final X509Certificate certificate)
            throws CertificateEncodingException
    {
        return StringUtil.base64Encode(certificate.getEncoded());
    }
}
