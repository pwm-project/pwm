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

package password.pwm.util.secure.self;

import password.pwm.AppAttribute;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.SecureService;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Optional;

public class SelfCertFactory
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SelfCertFactory.class );

    public static KeyStore getExistingCertOrGenerateNewCert( final PwmApplication pwmApplication, final PasswordData password, final String alias )
        throws Exception
    {
        final Settings settings = Settings.fromConfiguration( pwmApplication.getConfig() );

        final Optional<StoredCertData> existingCert = loadExistingStoredCert( pwmApplication );
        if ( existingCert.isPresent() )
        {
            if ( evaluateExistingStoredCert( existingCert.get(), settings ) )
            {
                return storedCertToKeyStore( existingCert.get(), alias, password );
            }
        }

        return generateNewCert(
            settings,
            pwmApplication.getSecureService(),
            password,
            alias );
    }

    public static KeyStore generateNewCert(
        final Settings settings,
        final SecureService secureService,
        final PasswordData password,
        final String alias
    )
        throws Exception
    {
        final SelfCertGenerator selfCertGenerator = new SelfCertGenerator(
            settings,
            secureService );
        final StoredCertData storedCertData = selfCertGenerator.generateNewCertificate( makeSubjectName( settings ) );
        return storedCertToKeyStore( storedCertData, alias, password );
    }

    private static Optional<StoredCertData> loadExistingStoredCert( final PwmApplication pwmApplication )
    {
        return pwmApplication.readAppAttribute( AppAttribute.HTTPS_SELF_CERT, StoredCertData.class );
    }

    private static boolean evaluateExistingStoredCert( final StoredCertData storedCertData, final Settings settings )
    {
        final String cnName = makeSubjectName( settings );
        if ( !cnName.equals( storedCertData.getX509Certificate().getSubjectDN().getName() ) )
        {
            LOGGER.info( () -> "replacing stored self cert, subject name does not match configured site url" );
            return false;
        }
        else if ( storedCertData.getX509Certificate().getNotBefore().after( new Date() ) )
        {
            LOGGER.info( () -> "replacing stored self cert, not-before date is in the future" );
            return false;
        }
        else if ( storedCertData.getX509Certificate().getNotAfter().before( new Date() ) )
        {
            LOGGER.info( () -> "replacing stored self cert, not-after date is in the past" );
            return false;
        }

        return true;
    }

    private static String makeSubjectName( final Settings settings )
    {
        String cnName = PwmConstants.PWM_APP_NAME.toLowerCase() + ".example.com";
        {
            final String siteURL = settings.getSiteUrl();
            if ( !StringUtil.isEmpty( siteURL ) )
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

    static KeyStore storedCertToKeyStore( final StoredCertData storedCertData, final String alias, final PasswordData password )
        throws KeyStoreException, IOException, ClassNotFoundException, PwmUnrecoverableException, CertificateException, NoSuchAlgorithmException
    {
        final KeyStore keyStore = KeyStore.getInstance( "jks" );
        keyStore.load( null, password.getStringValue().toCharArray() );
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
}
