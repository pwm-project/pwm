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

package password.pwm.http.auth;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import org.jasig.cas.client.authentication.AttributePrincipal;
import org.jasig.cas.client.ssl.HttpsURLConnectionFactory;
import org.jasig.cas.client.util.AbstractCasFilter;
import org.jasig.cas.client.util.CommonUtils;
import org.jasig.cas.client.util.XmlUtils;
import org.jasig.cas.client.validation.Assertion;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
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
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.servlet.http.HttpSession;
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

public class CASFilterAuthenticationProvider implements PwmHttpFilterAuthenticationProvider
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( CASFilterAuthenticationProvider.class );

    public static boolean isFilterEnabled( final PwmRequest pwmRequest )
    {
        final String clearPassUrl = pwmRequest.getConfig().readSettingAsString( PwmSetting.CAS_CLEAR_PASS_URL );

        if ( !( clearPassUrl == null || clearPassUrl.trim().isEmpty() ) )
        {
            return true;
        }

        final String alg = pwmRequest.getConfig().readSettingAsString( PwmSetting.CAS_CLEARPASS_ALGORITHM );
        final Map<FileInformation, FileContent> privatekey = pwmRequest.getConfig().readSettingAsFile( PwmSetting.CAS_CLEARPASS_KEY );

        if ( !privatekey.isEmpty() && ( !( alg == null || alg.trim().isEmpty() ) ) )
        {
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
        try
        {

            if ( CASFilterAuthenticationProvider.isFilterEnabled( pwmRequest ) )
            {
                LOGGER.trace( pwmRequest, () -> "checking for authentication via CAS" );
                if ( authUserUsingCASClearPass( pwmRequest ) )
                {
                    LOGGER.debug( pwmRequest, () -> "login via CAS successful" );
                }
            }
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
        catch ( final PwmOperationalException e )
        {
            throw new PwmUnrecoverableException( e.getErrorInformation() );
        }
        catch ( final UnsupportedEncodingException e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_INTERNAL, "error during CAS authentication: " + e.getMessage() ) );
        }
    }

    @Override
    public boolean hasRedirectedResponse( )
    {
        return false;
    }

    private static boolean authUserUsingCASClearPass(
            final PwmRequest pwmRequest )
            throws UnsupportedEncodingException, PwmUnrecoverableException, ChaiUnavailableException, PwmOperationalException
    {
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final HttpSession session = pwmRequest.getHttpServletRequest().getSession();

        //make sure user session isn't already authenticated
        if ( pwmSession.isAuthenticated() )
        {
            return false;
        }

        // read CAS assertion out of the header (if it exists);
        final Assertion assertion = ( Assertion ) session.getAttribute( AbstractCasFilter.CONST_CAS_ASSERTION );
        if ( assertion == null )
        {
            LOGGER.trace( pwmRequest, () -> "no CAS assertion header present, skipping CAS authentication attempt" );
            return false;
        }

        final String username = assertion.getPrincipal().getName();
        PasswordData password = null;
        final AttributePrincipal attributePrincipal = assertion.getPrincipal();
        final Map<String, Object> casAttributes = attributePrincipal.getAttributes();

        final String encodedPsw = ( String ) casAttributes.get( "credential" );
        if ( encodedPsw == null )
        {
            LOGGER.trace( () -> "No credential" );
        }
        else
        {
            final Map<FileInformation, FileContent> privatekey = pwmRequest.getConfig().readSettingAsFile( PwmSetting.CAS_CLEARPASS_KEY );
            final String alg = pwmRequest.getConfig().readSettingAsString( PwmSetting.CAS_CLEARPASS_ALGORITHM );

            password = decryptPassword( alg, privatekey, encodedPsw );
        }

        // If using the old method
        final String clearPassUrl = pwmRequest.getConfig().readSettingAsString( PwmSetting.CAS_CLEAR_PASS_URL );
        if ( ( clearPassUrl != null && clearPassUrl.length() > 0 ) && ( password == null || password.getStringValue().length() < 1 ) )
        {
            LOGGER.trace( pwmRequest, () -> "using CAS clearpass via proxy" );
            // read cas proxy ticket
            final String proxyTicket = assertion.getPrincipal().getProxyTicketFor( clearPassUrl );
            if ( proxyTicket == null )
            {
                LOGGER.trace( pwmRequest, () -> "no CAS proxy ticket available, skipping CAS authentication attempt" );
                return false;
            }

            final String clearPassRequestUrl = clearPassUrl + "?" + "ticket="
                    + proxyTicket + "&" + "service="
                    + StringUtil.urlEncode( clearPassUrl );

            try
            {
                final String response = CommonUtils.getResponseFromServer(
                        new URL( clearPassRequestUrl ), new HttpsURLConnectionFactory(), "UTF-8" );
                password = new PasswordData( XmlUtils.getTextForElement( response, "credentials" ) );
            }
            catch ( final MalformedURLException e )
            {
                LOGGER.error( pwmRequest, () -> "Invalid CAS clearPassUrl" );
            }

        }
        if ( password == null || password.getStringValue().length() < 1 )
        {
            final String errorMsg = "CAS server did not return credentials for user '" + username + "'";
            LOGGER.trace( pwmRequest, () -> errorMsg );
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_WRONGPASSWORD, errorMsg );
            throw new PwmOperationalException( errorInformation );
        }

        //user isn't already authenticated and has CAS assertion and password, so try to auth them.
        LOGGER.debug( pwmRequest, () -> "attempting to authenticate user '" + username + "' using CAS assertion and password" );
        final SessionAuthenticator sessionAuthenticator = new SessionAuthenticator( pwmApplication, pwmRequest, PwmAuthenticationSource.CAS );
        sessionAuthenticator.searchAndAuthenticateUser( username, password, null, null );
        return true;
    }

    private static PasswordData decryptPassword( final String alg,
                                                 final Map<FileInformation, FileContent> privatekey, final String encodedPsw )
    {
        PasswordData password = null;

        if ( alg == null || alg.trim().isEmpty() )
        {
            return password;
        }

        final byte[] privateKeyBytes;
        if ( privatekey != null && !privatekey.isEmpty() )
        {
            final FileValue.FileContent fileContent = privatekey.values().iterator().next();
            privateKeyBytes = fileContent.getContents().copyOf();
        }
        else
        {
            privateKeyBytes = null;
        }

        if ( privateKeyBytes != null )
        {
            final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec( privateKeyBytes );
            try
            {
                final KeyFactory kf = KeyFactory.getInstance( alg );
                final PrivateKey privateKey = kf.generatePrivate( spec );
                final Cipher cipher = Cipher.getInstance( privateKey.getAlgorithm() );
                final byte[] cred64 = StringUtil.base64Decode( encodedPsw );
                cipher.init( Cipher.DECRYPT_MODE, privateKey );
                final byte[] cipherData = cipher.doFinal( cred64 );
                if ( cipherData != null )
                {
                    try
                    {
                        password = new PasswordData( new String( cipherData, PwmConstants.DEFAULT_CHARSET ) );
                    }
                    catch ( final PwmUnrecoverableException e )
                    {
                        LOGGER.error( () -> "Decryption failed", e );
                        return password;
                    }
                }
            }
            catch ( final NoSuchAlgorithmException e1 )
            {
                LOGGER.error( () -> "Decryption failed", e1 );
                return password;
            }
            catch ( final InvalidKeySpecException e1 )
            {
                LOGGER.error( () -> "Decryption failed", e1 );
                return password;
            }
            catch ( final NoSuchPaddingException e1 )
            {
                LOGGER.error( () -> "Decryption failed", e1 );
                return password;
            }
            catch ( final IOException e1 )
            {
                LOGGER.error( () -> "Decryption failed", e1 );
                return password;
            }
            catch ( final InvalidKeyException e1 )
            {
                LOGGER.error( () -> "Decryption failed", e1 );
                return password;
            }
            catch ( final IllegalBlockSizeException e )
            {
                LOGGER.error( () -> "Decryption failed", e );
                return password;
            }
            catch ( final BadPaddingException e )
            {
                LOGGER.error( () -> "Decryption failed", e );
                return password;
            }
        }
        return password;
    }
}
