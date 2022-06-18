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
import org.jrivard.xmlchai.XmlElement;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmInternalException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class PasswordValue implements StoredValue
{
    private static final long serialVersionUID = 1L;

    private final transient LazySupplier<String> valueHashSupplier = new LazySupplier<>( () -> AbstractValue.valueHashComputer( PasswordValue.this ) );

    private final PasswordData value;

    PasswordValue( )
    {
        value = null;
    }

    public PasswordValue( final PasswordData passwordData )
    {
        value = passwordData;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public PasswordValue fromJson( final String value )
            {
                final String strValue = JsonFactory.get().deserialize( value, String.class );
                if ( strValue != null && !strValue.isEmpty() )
                {
                    try
                    {
                        return new PasswordValue( new PasswordData( strValue ) );
                    }
                    catch ( final PwmUnrecoverableException e )
                    {
                        throw new IllegalStateException(
                                "PasswordValue can not be json de-serialized: " + e.getMessage() );
                    }
                }
                return new PasswordValue();
            }

            @Override
            public PasswordValue fromXmlElement(
                    final PwmSetting pwmSetting,
                    final XmlElement settingElement,
                    final PwmSecurityKey key
            )
                    throws PwmOperationalException, PwmUnrecoverableException
            {
                final Optional<XmlElement> valueElement = settingElement.getChild( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                if ( valueElement.isPresent() )
                {
                    final Optional<String> rawValue = valueElement.get().getText();

                    if ( rawValue.isEmpty() )
                    {
                        return new PasswordValue();
                    }

                    final boolean plainTextSetting = valueElement.get().getAttribute( "plaintext" )
                            .map( Boolean::parseBoolean )
                            .orElse( false );

                    if ( plainTextSetting )
                    {
                        return new PasswordValue( new PasswordData( rawValue.get() ) );
                    }
                    else
                    {
                        try
                        {
                            final Optional<String> encodedValue = StoredValueEncoder.decode( rawValue.get(), StoredValueEncoder.Mode.CONFIG_PW, key );
                            if ( encodedValue.isPresent() )
                            {
                                return new PasswordValue( new PasswordData( encodedValue.get() ) );
                            }
                            else
                            {
                                return new PasswordValue( new PasswordData( "" ) );
                            }
                        }
                        catch ( final Exception e )
                        {
                            final String errorMsg = "unable to decode encrypted password value for setting: " + e.getMessage();
                            final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                            throw new PwmOperationalException( errorInfo );
                        }
                    }
                }
                return new PasswordValue();
            }
        };
    }

    @Override
    public Object toNativeObject( )
    {
        return value;
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

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        if ( value == null )
        {
            final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );
            return Collections.singletonList( valueElement );
        }
        final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );
        try
        {
            final String encodedValue = StoredValueEncoder.encode(
                    value.getStringValue(),
                    xmlOutputProcessData.getStoredValueEncoderMode(),
                    xmlOutputProcessData.getPwmSecurityKey() );

            valueElement.setText( encodedValue );
        }
        catch ( final Exception e )
        {
            throw new PwmInternalException( "missing required AES and SHA1 libraries, or other crypto fault: " + e.getMessage() );
        }
        return Collections.singletonList( valueElement );
    }

    public String toString( )
    {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    @Override
    public String valueHash()
    {
        return valueHashSupplier.get();
    }
}
