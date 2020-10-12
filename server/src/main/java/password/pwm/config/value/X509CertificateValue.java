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

package password.pwm.config.value;

import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigXmlSerializer;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;
import password.pwm.util.secure.X509Utils;

import java.io.ByteArrayInputStream;
import java.io.Serializable;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class X509CertificateValue extends AbstractValue implements StoredValue
{
    private static final long serialVersionUID = 1L;

    private static final PwmLogger LOGGER = PwmLogger.forClass( X509CertificateValue.class );

    // native object;
    private final List<String> b64certificates;

    // non-serializable representation of native object
    private final transient Supplier<List<X509Certificate>> certs;

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public X509CertificateValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final List<String> b64certificates = new ArrayList<>();
                final List<XmlElement> valueElements = settingElement.getChildren( StoredConfigXmlSerializer.StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                for ( final XmlElement loopValueElement : valueElements )
                {
                    final String b64encodedStr = loopValueElement.getText();

                    b64certificates.add( b64encodedStr );
                }
                return new X509CertificateValue( Collections.unmodifiableList( b64certificates ) );
            }

            @Override
            public X509CertificateValue fromJson( final String input )
            {
                return new X509CertificateValue( Collections.emptyList() );
            }
        };
    }

    public boolean hasCertificates( )
    {
        return !JavaHelper.isEmpty( b64certificates );
    }

    public X509CertificateValue( final List<String> b64certificates )
    {
        if ( b64certificates == null )
        {
            throw new NullPointerException( "certificates cannot be null" );
        }
        this.b64certificates = Collections.unmodifiableList(
                b64certificates.stream()
                .map( StringUtil::stripAllWhitespace )
                .collect( Collectors.toList() ) );
        this.certs = new LazySupplier<>( () -> X509Utils.certificatesFromBase64s( b64certificates ) );
    }

    public static X509CertificateValue fromX509( final Collection<X509Certificate> x509Certificate )
    {
        return new X509CertificateValue( X509Utils.certificatesToBase64s( x509Certificate ) );
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final String b64value : b64certificates )
        {
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            final String splitValue = StringUtil.insertRepeatedLineBreaks( b64value, PwmConstants.XML_OUTPUT_LINE_WRAP_LENGTH );
            valueElement.addText( splitValue );

            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public Object toNativeObject( )
    {
        return b64certificates;
    }

    public List<X509Certificate> asX509Certificates()
    {
        return certs.get();
    }

    @Override
    public List<String> validateValue( final PwmSetting pwm )
    {
        return Collections.emptyList();
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        final StringBuilder sb = new StringBuilder();
        final int counter = 0;
        for ( final X509Certificate cert : certs.get() )
        {
            sb.append( "Certificate " ).append( counter ).append( "\n" );
            sb.append( " Subject: " ).append( cert.getSubjectDN().toString() ).append( "\n" );
            sb.append( " Serial: " ).append( X509Utils.hexSerial( cert ) ).append( "\n" );
            sb.append( " Issuer: " ).append( cert.getIssuerDN().toString() ).append( "\n" );
            sb.append( " IssueDate: " ).append( JavaHelper.toIsoDate( cert.getNotBefore() ) ).append( "\n" );
            sb.append( " ExpireDate: " ).append( JavaHelper.toIsoDate( cert.getNotAfter() ) ).append( "\n" );
            try
            {
                sb.append( " MD5 Hash: " ).append( SecureEngine.hash( new ByteArrayInputStream( cert.getEncoded() ),
                        PwmHashAlgorithm.MD5 ) ).append( "\n" );
                sb.append( " SHA1 Hash: " ).append( SecureEngine.hash( new ByteArrayInputStream( cert.getEncoded() ),
                        PwmHashAlgorithm.SHA1 ) ).append( "\n" );
            }
            catch ( final PwmUnrecoverableException | CertificateEncodingException e )
            {
                LOGGER.warn( () -> "error generating hash for certificate: " + e.getMessage() );
            }
        }
        return sb.toString();
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return ( Serializable ) toInfoMap( false );
    }

    public List<Map<String, String>> toInfoMap( final boolean includeDetail )
    {
        if ( this.certs.get() == null )
        {
            return Collections.emptyList();
        }

        final List<Map<String, String>> list = new ArrayList<>();
        for ( final X509Certificate cert : certs.get() )
        {
            final X509Utils.DebugInfoFlag[] flags = includeDetail
                    ? new X509Utils.DebugInfoFlag[]
                    {
                            X509Utils.DebugInfoFlag.IncludeCertificateDetail,
                            }
                    : null;
            list.add( X509Utils.makeDebugInfoMap( cert, flags ) );
        }
        return Collections.unmodifiableList( list );
    }

}
