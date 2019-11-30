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

import password.pwm.PwmConstants;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.XmlDocument;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Locale;

public abstract class AbstractValue implements StoredValue
{
    private final transient LazySupplier<String> valueHashSupplier = new LazySupplier<>( () -> valueHashComputer( AbstractValue.this ) );

    public String toString()
    {
        return toDebugString( null );
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        return JsonUtil.serialize( ( Serializable ) this.toNativeObject(), JsonUtil.Flag.PrettyPrint );
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

    static String valueHashComputer( final StoredValue storedValue )
    {
        try
        {
            final PwmSecurityKey testingKey = new PwmSecurityKey( "test" );
            final XmlOutputProcessData xmlOutputProcessData = XmlOutputProcessData.builder()
                    .pwmSecurityKey( testingKey )
                    .storedValueEncoderMode( StoredValueEncoder.Mode.PLAIN )
                    .build();
            final List<XmlElement> xmlValues = storedValue.toXmlValues( StoredConfigXmlConstants.XML_ELEMENT_VALUE, xmlOutputProcessData );
            final XmlDocument document = XmlFactory.getFactory().newDocument( "root" );
            document.getRootElement().addContent( xmlValues );
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            XmlFactory.getFactory().outputDocument( document, byteArrayOutputStream );
            final String stringToHash = new String( byteArrayOutputStream.toByteArray(), PwmConstants.DEFAULT_CHARSET );
            return SecureEngine.hash( stringToHash, PwmHashAlgorithm.SHA512 );

        }
        catch ( final IOException | PwmUnrecoverableException e )
        {
            throw new IllegalStateException( e );
        }
    }
}
