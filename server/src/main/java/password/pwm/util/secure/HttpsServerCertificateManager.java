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

package password.pwm.util.secure;

import password.pwm.PwmApplication;
import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.StoredValue;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.PrivateKeyValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.self.SelfCertFactory;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

public class HttpsServerCertificateManager
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( HttpsServerCertificateManager.class );

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
                            privateKeyCertificate.getCertificates().toArray( new X509Certificate[0] )
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
        try
        {
            return SelfCertFactory.getExistingCertOrGenerateNewCert( pwmApplication, password, alias );
        }
        catch ( final Exception e )
        {
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_CERTIFICATE_ERROR, "unable to generate self signed certificate: " + e.getMessage() ) );
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
