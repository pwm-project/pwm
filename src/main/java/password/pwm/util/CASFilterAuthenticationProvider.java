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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpSession;

import org.jasig.cas.client.authentication.AttributePrincipal;
import org.jasig.cas.client.ssl.HttpsURLConnectionFactory;
import org.jasig.cas.client.util.AbstractCasFilter;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.XmlUtils;
import org.jasig.cas.client.validation.Assertion;

import com.novell.ldapchai.exception.ChaiUnavailableException;

import password.pwm.PwmApplication;
import password.pwm.PwmHttpFilterAuthenticationProvider;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.FileValue;
import password.pwm.config.value.FileValue.FileContent;
import password.pwm.config.value.FileValue.FileInformation;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.ldap.auth.PwmAuthenticationSource;
import password.pwm.ldap.auth.SessionAuthenticator;
import password.pwm.util.logging.PwmLogger;

public class CASFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider {

    private static final PwmLogger LOGGER = PwmLogger.forClass(CASFilterAuthenticationProvider.class);

    public static boolean isFilterEnabled(final PwmRequest pwmRequest) {
        final String clearPassUrl = pwmRequest.getConfig().readSettingAsString(PwmSetting.CAS_CLEAR_PASS_URL);
        
        if (!(clearPassUrl == null || clearPassUrl.trim().isEmpty())) {
            return true;
        }
        
        final String alg = pwmRequest.getConfig().readSettingAsString(PwmSetting.CAS_CLEARPASS_ALGORITHM);
        final Map<FileInformation, FileContent> privatekey = pwmRequest.getConfig().readSettingAsFile(PwmSetting.CAS_CLEARPASS_KEY);
        
        if (!privatekey.isEmpty() && (!(alg == null || alg.trim().isEmpty()))) {
            return true;
        }
    
        return false;
    }
    
    @Override
    public void attemptAuthentication(
            final PwmRequest pwmRequest
    )
            throws PwmUnrecoverableException
    {
        try {
             
            if (CASFilterAuthenticationProvider.isFilterEnabled(pwmRequest)) {
                LOGGER.trace(pwmRequest, "checking for authentication via CAS");
                if (authUserUsingCASClearPass(pwmRequest)) {
                    LOGGER.debug(pwmRequest, "login via CAS successful");
                }
            }
        } catch (ChaiUnavailableException e) {
            throw PwmUnrecoverableException.fromChaiException(e);
        } catch (PwmOperationalException e) {
            throw new PwmUnrecoverableException(e.getErrorInformation());
        } catch (UnsupportedEncodingException e) {
            throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_UNKNOWN,"error during CAS authentication: " + e.getMessage()));
        }
    }

    @Override
    public boolean hasRedirectedResponse() {
        return false;
    }

    private static boolean authUserUsingCASClearPass(
            final PwmRequest pwmRequest)
            throws UnsupportedEncodingException, PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final HttpSession session = pwmRequest.getHttpServletRequest().getSession();

        //make sure user session isn't already authenticated
        if (pwmSession.isAuthenticated()) {
            return false;
        }

        // read CAS assertion out of the header (if it exists);
        final Assertion assertion = (Assertion) session.getAttribute(AbstractCasFilter.CONST_CAS_ASSERTION);
        if (assertion == null) {
            LOGGER.trace(pwmSession,"no CAS assertion header present, skipping CAS authentication attempt");
            return false;
        }

        final String username = assertion.getPrincipal().getName();
        PasswordData password = null;
        final AttributePrincipal attributePrincipal = assertion.getPrincipal();
        final Map<String, Object> casAttributes = attributePrincipal.getAttributes();
        
        final String encodedPsw = (String) casAttributes.get("credential");
        if (encodedPsw == null) {
            LOGGER.trace("No credential");
        } else {
            final Map<FileInformation, FileContent> privatekey = pwmRequest.getConfig().readSettingAsFile(PwmSetting.CAS_CLEARPASS_KEY);
            final String alg = pwmRequest.getConfig().readSettingAsString(PwmSetting.CAS_CLEARPASS_ALGORITHM);

            password = decryptPassword(alg, privatekey, encodedPsw);
        }
        
        // If using the old method
        final String clearPassUrl = pwmRequest.getConfig().readSettingAsString(PwmSetting.CAS_CLEAR_PASS_URL);
        if ((clearPassUrl != null && clearPassUrl.length() > 0) && (password == null || password.getStringValue().length() < 1)) {
            LOGGER.trace(pwmSession, "Using CAS clearpass via proxy");
            // read cas proxy ticket
            final String proxyTicket = assertion.getPrincipal().getProxyTicketFor(clearPassUrl);
            if (proxyTicket == null) {
                LOGGER.trace(pwmSession,"no CAS proxy ticket available, skipping CAS authentication attempt");
                return false;
            }

            final String clearPassRequestUrl = clearPassUrl + "?" + "ticket="
                    + proxyTicket + "&" + "service="
                    + StringUtil.urlEncode(clearPassUrl);

            try {
                final String response = CommonUtils.getResponseFromServer(
                        new URL(clearPassRequestUrl), new HttpsURLConnectionFactory(), "UTF-8");
                password = new PasswordData(XmlUtils.getTextForElement(response, "credentials"));
            } catch (MalformedURLException e) {
                LOGGER.error(pwmSession, "Invalid CAS clearPassUrl");
            }
            
        }
        if (password == null || password.getStringValue().length() < 1) {
            final String errorMsg = "CAS server did not return credentials for user '" + username + "'";
            LOGGER.trace(pwmSession, errorMsg);
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_WRONGPASSWORD,errorMsg);
            throw new PwmOperationalException(errorInformation);
        }

        //user isn't already authenticated and has CAS assertion and password, so try to auth them.
        LOGGER.debug(pwmSession, "attempting to authenticate user '" + username + "' using CAS assertion and password");
        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator(pwmApplication, pwmSession, PwmAuthenticationSource.CAS);
        sessionAuthenticator.searchAndAuthenticateUser(username, password, null, null);
        return true;
    }

    private static PasswordData decryptPassword(final String alg,
            final Map<FileInformation, FileContent> privatekey, final String encodedPsw)
            {
        PasswordData password = null;
        
        if (alg == null || alg.trim().isEmpty()) {
            return password;
        }
        
        final byte[] privateKeyBytes;
        if (privatekey != null && !privatekey.isEmpty()) {
            final FileValue.FileInformation fileInformation1 = privatekey.keySet().iterator().next();
            final FileValue.FileContent fileContent = privatekey.get(fileInformation1);
            privateKeyBytes = fileContent.getContents();
        } else {
            privateKeyBytes = null;
        }
        
        if (privateKeyBytes != null) {
            final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyBytes);
            try {
                final KeyFactory kf = KeyFactory.getInstance(alg);
                final PrivateKey privateKey = kf.generatePrivate(spec);
                final Cipher cipher = Cipher.getInstance(privateKey.getAlgorithm());
                final byte[] cred64 = StringUtil.base64Decode(encodedPsw);
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                final byte[] cipherData = cipher.doFinal(cred64);
                if (cipherData != null) {
                    try {
                        password = new PasswordData(new String(cipherData));
                    } catch (PwmUnrecoverableException e) {
                        LOGGER.error("Decryption failed", e);
                        return password;
                    }
                }
            } catch (NoSuchAlgorithmException e1) {
                LOGGER.error("Decryption failed", e1);
                return password;
            } catch (InvalidKeySpecException e1) {
                LOGGER.error("Decryption failed", e1);
                return password;
            } catch (NoSuchPaddingException e1) {
                LOGGER.error("Decryption failed", e1);
                return password;
            } catch (IOException e1) {
                LOGGER.error("Decryption failed", e1);
                return password;
            } catch (InvalidKeyException e1) {
                LOGGER.error("Decryption failed", e1);
                return password;
            } catch (IllegalBlockSizeException e) {
                LOGGER.error("Decryption failed", e);
                return password;
            } catch (BadPaddingException e) {
                LOGGER.error("Decryption failed", e);
                return password;
            }
        }
        return password;
    }
}
