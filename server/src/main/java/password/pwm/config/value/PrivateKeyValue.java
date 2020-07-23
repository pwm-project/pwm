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

package password.pwm.config.value;

import password.pwm.bean.PrivateKeyCertificate;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.X509Utils;

import java.io.Serializable;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class PrivateKeyValue extends AbstractValue
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( PrivateKeyValue.class );

    private static final String ELEMENT_NAME_CERTIFICATE = "certificate";
    private static final String ELEMENT_NAME_KEY = "key";

    private final PrivateKeyCertificate privateKeyCertificate;

    public static StoredValue.StoredValueFactory factory( )
    {
        return new StoredValue.StoredValueFactory()
        {
            public PrivateKeyValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                if ( settingElement != null && settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_VALUE  ).isPresent() )
                {

                    final Optional<XmlElement> valueElement = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                    if ( valueElement.isPresent() )
                    {
                        final List<X509Certificate> certificates = new ArrayList<>();
                        for ( final XmlElement certificateElement : valueElement.get().getChildren( ELEMENT_NAME_CERTIFICATE ) )
                        {
                            try
                            {
                                final String b64Text = certificateElement.getText();
                                final X509Certificate cert = X509Utils.certificateFromBase64( b64Text );
                                certificates.add( cert );
                            }
                            catch ( final Exception e )
                            {
                                LOGGER.error( () -> "error reading certificate: " + e.getMessage(), e );
                            }

                        }


                        PrivateKey privateKey = null;
                        try
                        {
                            final Optional<XmlElement> keyElement = valueElement.get().getChild( ELEMENT_NAME_KEY );
                            if ( keyElement.isPresent() )
                            {
                                final String encryptedText = keyElement.get().getText();
                                final Optional<String> decryptedText = StoredValueEncoder.decode( encryptedText, StoredValueEncoder.Mode.CONFIG_PW, key );
                                if ( decryptedText.isPresent() )
                                {
                                    final byte[] privateKeyBytes = StringUtil.base64Decode( decryptedText.get() );
                                    privateKey = KeyFactory.getInstance( "RSA" ).generatePrivate( new PKCS8EncodedKeySpec( privateKeyBytes ) );
                                }
                            }
                            else
                            {
                                LOGGER.error( () -> "error reading privateKey for setting: '" + pwmSetting.getKey() + "': missing 'value' element" );
                            }
                        }
                        catch ( final Exception e )
                        {
                            LOGGER.error( () -> "error reading privateKey for setting: '" + pwmSetting.getKey() + "': " + e.getMessage(), e );
                        }

                        if ( !certificates.isEmpty() && privateKey != null )
                        {
                            final PrivateKeyCertificate privateKeyCertificate = new PrivateKeyCertificate( certificates, privateKey );
                            return new PrivateKeyValue( privateKeyCertificate );
                        }
                    }
                }
                return new PrivateKeyValue( null );
            }

            public X509CertificateValue fromJson( final String input )
            {
                return new X509CertificateValue( new X509Certificate[ 0 ] );
            }
        };
    }

    public PrivateKeyValue( final PrivateKeyCertificate privateKeyCertificate )
    {
        this.privateKeyCertificate = privateKeyCertificate;
    }


    public List<XmlElement> toXmlValues( final String valueElementName )
    {
        throw new IllegalStateException( "password xml output requires hash key" );
    }

    @Override
    public Object toNativeObject( )
    {
        return privateKeyCertificate;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwm )
    {
        return Collections.emptyList();
    }

    @Override
    public int currentSyntaxVersion( )
    {
        return 0;
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final XmlElement valueElement = XmlFactory.getFactory().newElement( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
        if ( privateKeyCertificate != null )
        {
            try
            {
                {
                    for ( final X509Certificate certificate : privateKeyCertificate.getCertificates() )
                    {
                        final XmlElement certificateElement = XmlFactory.getFactory().newElement( ELEMENT_NAME_CERTIFICATE );
                        certificateElement.addText( X509Utils.certificateToBase64( certificate ) );
                        valueElement.addContent( certificateElement );
                    }
                }
                {
                    final XmlElement keyElement = XmlFactory.getFactory().newElement( ELEMENT_NAME_KEY );
                    final String b64EncodedKey = StringUtil.base64Encode( privateKeyCertificate.getKey().getEncoded() );
                    final String encryptedKey = StoredValueEncoder.encode(
                            b64EncodedKey,
                            xmlOutputProcessData.getStoredValueEncoderMode(),
                            xmlOutputProcessData.getPwmSecurityKey() );

                    keyElement.addText( encryptedKey );
                    valueElement.addContent( keyElement );
                }
            }
            catch ( final Exception e )
            {
                throw new RuntimeException( "missing required AES and SHA1 libraries, or other crypto fault: " + e.getMessage() );
            }
        }
        return Collections.singletonList( valueElement );
    }

    public String toDebugString( final Locale locale )
    {
        if ( privateKeyCertificate != null )
        {
            return "PrivateKeyCertificate: key=" + JsonUtil.serializeMap( X509Utils.makeDebugInfoMap( privateKeyCertificate.getKey() ) )
                    + ", certificates=" + JsonUtil.serializeCollection( X509Utils.makeDebugInfoMap( privateKeyCertificate.getCertificates() ) );
        }
        return "";
    }

    public Map<String, Object> toInfoMap( final boolean includeDetail )
    {
        if ( privateKeyCertificate == null )
        {
            return null;
        }
        final X509Utils.DebugInfoFlag[] flags = includeDetail
                ? new X509Utils.DebugInfoFlag[]
                {
                        X509Utils.DebugInfoFlag.IncludeCertificateDetail,
                }
                : null;
        final Map<String, Object> returnMap = new LinkedHashMap<>();
        returnMap.put( "certificates", X509Utils.makeDebugInfoMap( privateKeyCertificate.getCertificates(), flags ) );
        final Map<String, Object> privateKeyInfo = new LinkedHashMap<>();
        privateKeyInfo.put( "algorithm", privateKeyCertificate.getKey().getAlgorithm() );
        privateKeyInfo.put( "format", privateKeyCertificate.getKey().getFormat() );
        returnMap.put( "key", privateKeyInfo );
        return returnMap;
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return ( Serializable ) toInfoMap( false );
    }
}
