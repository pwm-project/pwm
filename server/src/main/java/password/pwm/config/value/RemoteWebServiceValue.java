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
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmSecurityKey;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class RemoteWebServiceValue extends AbstractValue implements StoredValue
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( RemoteWebServiceValue.class );

    final List<RemoteWebServiceConfiguration> values;

    public RemoteWebServiceValue( final List<RemoteWebServiceConfiguration> values )
    {
        this.values = List.copyOf( CollectionUtil.stripNulls( values ) );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public RemoteWebServiceValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new RemoteWebServiceValue( Collections.emptyList() );
                }
                else
                {
                    return new RemoteWebServiceValue( JsonFactory.get().deserializeList( input, RemoteWebServiceConfiguration.class ) );
                }
            }

            @Override
            public RemoteWebServiceValue fromXmlElement(
                    final PwmSetting pwmSetting,
                    final XmlElement settingElement,
                    final PwmSecurityKey pwmSecurityKey
            )
                    throws PwmOperationalException
            {
                final List<XmlElement> valueElements = settingElement.getChildren( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                final List<RemoteWebServiceConfiguration> values = new ArrayList<>();
                for ( final XmlElement loopValueElement : valueElements )
                {
                    final Optional<String> value = loopValueElement.getText();
                    if ( value.isPresent() )
                    {
                        final RemoteWebServiceConfiguration parsedValue = JsonFactory.get().deserialize( value.get(), RemoteWebServiceConfiguration.class );
                        final Optional<String> decodedValue = StoredValueEncoder.decode(
                                parsedValue.getPassword(),
                                StoredValueEncoder.Mode.ENCODED,
                                pwmSecurityKey
                        );
                        decodedValue.ifPresent( ( s ) -> values.add( parsedValue.toBuilder().password( s ).build() ) );
                    }
                }
                return new RemoteWebServiceValue( values );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final RemoteWebServiceConfiguration value : values )
        {
            final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );

            String encodedValue = value.getPassword();
            try
            {
                encodedValue = StoredValueEncoder.encode(
                        value.getPassword(),
                        xmlOutputProcessData.getStoredValueEncoderMode(),
                        xmlOutputProcessData.getPwmSecurityKey() );
            }
            catch ( final PwmOperationalException e )
            {
                LOGGER.warn( () -> "error decoding stored pw value: " + e.getMessage() );
            }

            final RemoteWebServiceConfiguration clonedValue = value.toBuilder().password( encodedValue ).build();
            valueElement.setText( JsonFactory.get().serialize( clonedValue ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public List<RemoteWebServiceConfiguration> toNativeObject( )
    {
        return values;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( values == null || values.size() < 1 || values.get( 0 ) == null )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        final Set<String> seenNames = new HashSet<>();
        for ( final RemoteWebServiceConfiguration item : values )
        {
            if ( seenNames.contains( item.getName().toLowerCase() ) )
            {
                return Collections.singletonList( "each action name must be unique: " + item.getName().toLowerCase() );
            }
            seenNames.add( item.getName().toLowerCase() );
        }


        return Collections.emptyList();
    }

    public RemoteWebServiceConfiguration forName( final String name )
    {
        if ( name == null )
        {
            return null;
        }
        for ( final RemoteWebServiceConfiguration config : values )
        {
            if ( name.equals( config.getName() ) )
            {
                return config;
            }
        }
        return null;
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return ( Serializable ) makeDebugJsonObject( locale );
    }

    private List<RemoteWebServiceConfiguration> makeDebugJsonObject( final Locale locale )
    {
        final ArrayList<RemoteWebServiceConfiguration> output = new ArrayList<>();
        for ( final RemoteWebServiceConfiguration remoteWebServiceConfiguration : values )
        {
            if ( StringUtil.notEmpty( remoteWebServiceConfiguration.getPassword() ) )
            {
                output.add( remoteWebServiceConfiguration.toBuilder().password( PwmConstants.LOG_REMOVED_VALUE_REPLACEMENT ).build() );
            }
            else
            {
                output.add( remoteWebServiceConfiguration );
            }
        }
        return output;
    }
    
    @Override
    public String toDebugString( final Locale locale )
    {
        return JsonFactory.get().serializeCollection( this.makeDebugJsonObject( locale ) );
    }

}
