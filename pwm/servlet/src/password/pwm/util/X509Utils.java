/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;

import javax.net.ssl.*;
import java.net.URI;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public abstract class X509Utils {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(X509Utils.class);

    public static X509Certificate[] readLdapServerCerts(final URI ldapUri)
            throws PwmOperationalException
    {
        final String ldapHost = ldapUri.getHost();
        final int ldapPort = ldapUri.getPort();

        LOGGER.debug("LDAPCertReader: beginning certificate read procedure to import ldap certificates from host=" + ldapHost + ", port=" + ldapPort);
        final CertReaderTrustManager certReaderTm = new CertReaderTrustManager();
        try { // use custom trust manager to read the certificates
            final SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{certReaderTm}, new SecureRandom());
            final SSLSocketFactory factory = ctx.getSocketFactory();
            final SSLSocket sslSock = (SSLSocket) factory.createSocket(ldapHost,ldapPort);
            LOGGER.debug("LDAPCertReader: socket established to host=" + ldapHost + ", port=" + ldapPort);
            sslSock.isConnected();
            LOGGER.debug("LDAPCertReader: connected to host=" + ldapHost + ", port=" + ldapPort);
            sslSock.getOutputStream().write("data!".getBytes());//write some data so the connection gets established
            LOGGER.debug("LDAPCertReader: data transfer completed host=" + ldapHost + ", port=" + ldapPort);
            sslSock.close();
            LOGGER.debug("LDAPCertReader: certificate information read from host=" + ldapHost + ", port=" + ldapPort);
        } catch (Exception e) {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("unable to read ldap server certificates from ");
            errorMsg.append(ldapUri.toString());
            errorMsg.append(" error: ");
            errorMsg.append(e.getMessage());
            LOGGER.error("LDAPCertReader: " + errorMsg);
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CERTIFICATE_ERROR, errorMsg.toString(), new String[]{errorMsg.toString()});
            throw new PwmOperationalException(errorInformation);
        }
        final X509Certificate[] certs = certReaderTm.getCertificates();
        if (certs == null) {
            LOGGER.debug("LDAPCertReader: unable to read certificates: null returned from CertReaderTrustManager.getCertificates()");
        } else {
            for (final X509Certificate certificate : certs) {
                LOGGER.debug("LDAPCertReader: read x509 Certificate from host=" + ldapHost + ", port=" + ldapPort + ": \n" + certificate.toString());
            }
        }
        LOGGER.debug("LDAPCertReader: process completed");
        return certs;
    }

    public static boolean testIfLdapServerCertsInDefaultKeystore(final URI ldapUri) {
        final String ldapHost = ldapUri.getHost();
        final int ldapPort = ldapUri.getPort();
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

    public static class PwmTrustManager implements X509TrustManager {
        final X509Certificate[] certificates;

        public PwmTrustManager(final X509Certificate[] certificates) {
            this.certificates = certificates;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
            if (x509Certificates == null) {
                return;
            }


            for (X509Certificate loopCert : x509Certificates) {
                boolean certTrusted = false;
                for (X509Certificate storedCert : certificates) {
                    if (loopCert.equals(storedCert)) {
                        //loopCert.checkValidity();
                        certTrusted = true;
                    }
                }
                if (!certTrusted) {
                    final String errorMsg = "server certificate {subject=" + loopCert.getSubjectDN().getName() + "} does not match a certificate in the configuration trust store.";
                    throw new CertificateException(errorMsg);
                }
            }
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];  //To change body of implemented methods use File | Settings | File Templates.
        }
    }
}
