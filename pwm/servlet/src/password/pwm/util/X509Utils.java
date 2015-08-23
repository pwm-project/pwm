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

package password.pwm.util;

import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public abstract class X509Utils {
    private static final PwmLogger LOGGER = PwmLogger.forClass(X509Utils.class);

    public static X509Certificate[] readRemoteCertificates(final URI uri)
            throws PwmOperationalException
    {
        final String host = uri.getHost();
        final int port = Helper.portForUriSchema(uri);

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
}
