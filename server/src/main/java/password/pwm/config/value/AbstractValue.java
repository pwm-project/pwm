/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlDocument;
import org.jrivard.xmlchai.XmlElement;
import password.pwm.PwmConstants;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.data.ImmutableByteArray;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.DigestOutputStream;
import java.util.List;
import java.util.Locale;

public abstract class AbstractValue implements StoredValue
{
    private static final PwmSecurityKey HASHING_KEY;

    static
    {
        try
        {
            HASHING_KEY = new PwmSecurityKey( "hash-key" );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new IllegalStateException( "unable to create internal HASHING_KEY: " + e.getMessage() );
        }
    }

    private final transient LazySupplier<String> valueHashSupplier = new LazySupplier<>( () -> valueHashComputer( AbstractValue.this ) );

    public String toString()
    {
        return toDebugString( null );
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        return JsonFactory.get().serialize( ( Serializable ) this.toNativeObject(), JsonProvider.Flag.PrettyPrint );
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return ( Serializable ) this.toNativeObject();
    }

    @Override
    public int currentSyntaxVersion()
    {
        return 0;
    }

    @Override
    public final String valueHash()
    {
        return valueHashSupplier.get();
    }

    protected static String b64encode( final ImmutableByteArray immutableByteArray )
    {
        final String input = StringUtil.base64Encode( immutableByteArray.copyOf(), StringUtil.Base64Options.GZIP );
        return "\n" + StringUtil.insertRepeatedLineBreaks( input, PwmConstants.XML_OUTPUT_LINE_WRAP_LENGTH ) + "\n";
    }

    protected static ImmutableByteArray b64decode( final String b64EncodedContents )
    {
        try
        {
            final CharSequence whitespaceStripped = StringUtil.stripAllWhitespace( b64EncodedContents );
            final byte[] output = StringUtil.base64Decode( whitespaceStripped, StringUtil.Base64Options.GZIP );
            return ImmutableByteArray.of( output );
        }
        catch ( final Exception e )
        {
            throw new IllegalStateException( e );
        }
    }
    
    static String valueHashComputer( final StoredValue storedValue )
    {
        try
        {
            final XmlOutputProcessData xmlOutputProcessData = XmlOutputProcessData.builder()
                    .pwmSecurityKey( HASHING_KEY )
                    .storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN )
                    .build();
            final List<XmlElement> xmlValues = storedValue.toXmlValues( StoredConfigXmlConstants.XML_ELEMENT_VALUE, xmlOutputProcessData );
            final XmlDocument document = XmlChai.getFactory().newDocument( "root" );
            document.getRootElement().attachElement( xmlValues );

            final DigestOutputStream digestOutputStream = new DigestOutputStream(
                    OutputStream.nullOutputStream(),
                    PwmHashAlgorithm.SHA512.newMessageDigest() );
            XmlChai.getFactory().output( document, digestOutputStream );
            return JavaHelper.binaryArrayToHex( digestOutputStream.getMessageDigest().digest() );
        }
        catch ( final IOException e )
        {
            throw new IllegalStateException( e );
        }
    }

}
