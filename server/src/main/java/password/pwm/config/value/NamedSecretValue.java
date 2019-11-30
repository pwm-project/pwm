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

import com.google.gson.reflect.TypeToken;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.data.NamedSecretData;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmSecurityKey;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class NamedSecretValue implements StoredValue
{

    private final transient LazySupplier<String> valueHashSupplier = new LazySupplier<>( () -> AbstractValue.valueHashComputer( NamedSecretValue.this ) );

    private static final String ELEMENT_NAME = "name";
    private static final String ELEMENT_PASSWORD = "password";
    private static final String ELEMENT_USAGE = "usage";

    private final Map<String, NamedSecretData> values;

    NamedSecretValue( )
    {
        values = Collections.emptyMap();
    }

    public NamedSecretValue( final Map<String, NamedSecretData> values )
    {
        this.values = values == null ? Collections.emptyMap() : Collections.unmodifiableMap( values );
    }

    public static StoredValue.StoredValueFactory factory( )
    {
        return new StoredValue.StoredValueFactory()
        {
            public NamedSecretValue fromJson( final String value )
            {
                try
                {
                    final Map<String, NamedSecretData> values = JsonUtil.deserialize( value, new TypeToken<Map<String, NamedSecretData>>()
                    {
                    }.getType() );
                    final Map<String, NamedSecretData> linkedValues = new LinkedHashMap<>( values );
                    return new NamedSecretValue( linkedValues );
                }
                catch ( final Exception e )
                {
                    throw new IllegalStateException(
                            "NamedPasswordValue can not be json de-serialized: " + e.getMessage() );
                }
            }

            public NamedSecretValue fromXmlElement(
                    final PwmSetting pwmSetting,
                    final XmlElement settingElement,
                    final PwmSecurityKey key
            )
                    throws PwmOperationalException, PwmUnrecoverableException
            {
                final Map<String, NamedSecretData> values = new LinkedHashMap<>();
                final List<XmlElement> valueElements = settingElement.getChildren( StoredConfigXmlConstants.XML_ELEMENT_VALUE );

                try
                {
                    for ( final XmlElement value : valueElements )
                    {
                        final Optional<XmlElement> nameElement = value.getChild( ELEMENT_NAME );
                        final Optional<XmlElement> passwordElement = value.getChild( ELEMENT_PASSWORD );
                        if ( nameElement.isPresent() && passwordElement.isPresent() )
                        {
                            final String name = nameElement.get().getText();
                            final String encodedValue = passwordElement.get().getText();
                            final PasswordData passwordData = new PasswordData( SecureEngine.decryptStringValue( encodedValue, key, PwmBlockAlgorithm.CONFIG ) );
                            final List<XmlElement> usages = value.getChildren( ELEMENT_USAGE );
                            final List<String> strUsages = new ArrayList<>();
                            if ( usages != null )
                            {
                                for ( final XmlElement usageElement : usages )
                                {
                                    strUsages.add( usageElement.getText() );
                                }
                            }
                            values.put( name, new NamedSecretData( passwordData, Collections.unmodifiableList( strUsages ) ) );
                        }
                    }
                }
                catch ( final Exception e )
                {
                    final String errorMsg = "unable to decode encrypted password value for setting: " + e.getMessage();
                    final ErrorInformation errorInfo = new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                    throw new PwmOperationalException( errorInfo );
                }
                return new NamedSecretValue( values );
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName )
    {
        throw new IllegalStateException( "password xml output requires hash key" );
    }

    @Override
    public Object toNativeObject( )
    {
        return values;
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
        if ( values == null )
        {
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            return Collections.singletonList( valueElement );
        }
        final List<XmlElement> valuesElement = new ArrayList<>();
        try
        {
            for ( final Map.Entry<String, NamedSecretData> entry : values.entrySet() )
            {
                final String name = entry.getKey();
                final PasswordData passwordData = entry.getValue().getPassword();
                final String encodedValue = SecureEngine.encryptToString( passwordData.getStringValue(), xmlOutputProcessData.getPwmSecurityKey(), PwmBlockAlgorithm.CONFIG );
                final XmlElement newValueElement = XmlFactory.getFactory().newElement( "value" );
                final XmlElement nameElement = XmlFactory.getFactory().newElement( ELEMENT_NAME );
                nameElement.addText( name );
                final XmlElement encodedValueElement = XmlFactory.getFactory().newElement( ELEMENT_PASSWORD );
                encodedValueElement.addText( encodedValue );

                newValueElement.addContent( nameElement );
                newValueElement.addContent( encodedValueElement );

                for ( final String usages : values.get( name ).getUsage() )
                {
                    final XmlElement usageElement = XmlFactory.getFactory().newElement( ELEMENT_USAGE );
                    usageElement.addText( usages );
                    newValueElement.addContent( usageElement );
                }


                valuesElement.add( newValueElement );
            }
        }
        catch ( final Exception e )
        {
            throw new RuntimeException( "missing required AES and SHA1 libraries, or other crypto fault: " + e.getMessage() );
        }
        return Collections.unmodifiableList( valuesElement );
    }

    public String toString( )
    {
        return PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT;
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        final StringBuilder sb = new StringBuilder();
        for ( final Map.Entry<String, NamedSecretData> entry : values.entrySet() )
        {
            final NamedSecretData existingData = entry.getValue();
            sb.append( "Named password '" ).append( entry.getKey() ).append( "' with usage for " );
            sb.append( StringUtil.collectionToString( existingData.getUsage(), "," ) );
            sb.append( "\n" );

        }
        return sb.toString();
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        if ( values == null )
        {
            return null;
        }

        try
        {
            final LinkedHashMap<String, NamedSecretData> copiedValues = new LinkedHashMap<>();
            for ( final Map.Entry<String, NamedSecretData> entry : values.entrySet() )
            {
                final String name = entry.getKey();
                final NamedSecretData existingData = entry.getValue();
                final NamedSecretData newData = new NamedSecretData(
                        PasswordData.forStringValue( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT ),
                        existingData.getUsage()
                );
                copiedValues.put( name, newData );
            }
            return copiedValues;
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new IllegalStateException( e.getErrorInformation().toDebugStr() );
        }
    }

    @Override
    public String valueHash()
    {
        return valueHashSupplier.get();
    }
}
