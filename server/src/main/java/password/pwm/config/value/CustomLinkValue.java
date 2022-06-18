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
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.data.CustomLinkConfiguration;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CustomLinkValue extends AbstractValue implements StoredValue
{
    private final List<CustomLinkConfiguration> values;

    public CustomLinkValue( final List<CustomLinkConfiguration> values )
    {
        this.values = values == null ? Collections.emptyList() : Collections.unmodifiableList( CollectionUtil.stripNulls( values ) );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public CustomLinkValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new CustomLinkValue( Collections.emptyList() );
                }
                else
                {
                    final List<CustomLinkConfiguration> srcList = JsonFactory.get().deserializeList( input, CustomLinkConfiguration.class );
                    return new CustomLinkValue( srcList );
                }
            }

            @Override
            public CustomLinkValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                final List<CustomLinkConfiguration> values = new ArrayList<>();
                for ( final XmlElement loopValueElement  : valueElements )
                {
                    loopValueElement.getText().ifPresent( value -> values.add( JsonFactory.get().deserialize( value, CustomLinkConfiguration.class ) ) );
                }
                return new CustomLinkValue( values );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>( values.size() );
        for ( final CustomLinkConfiguration value : values )
        {
            final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );
            valueElement.setText( JsonFactory.get().serialize( value ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public List<CustomLinkConfiguration> toNativeObject( )
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

        final Set<String> seenNames = new HashSet<>( values.size() );
        for ( final CustomLinkConfiguration loopConfig : values )
        {
            if ( seenNames.contains( loopConfig.getName().toLowerCase() ) )
            {
                return Collections.singletonList( "each form name must be unique: " + loopConfig.getName() );
            }
            seenNames.add( loopConfig.getName().toLowerCase() );
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        if ( values != null && !values.isEmpty() )
        {
            final StringBuilder sb = new StringBuilder();
            for ( final CustomLinkConfiguration formRow : values )
            {
                sb.append( "Link Name:" ).append( formRow.getName() ).append( '\n' );
                sb.append( " Type:" ).append( formRow.getType() );
                sb.append( '\n' );
                sb.append( " Description:" ).append( JsonFactory.get().serializeMap( formRow.getLabels() ) ).append( '\n' );
                sb.append( " New Window:" ).append( formRow.isCustomLinkNewWindow() ).append( '\n' );
                sb.append( " Url:" ).append( formRow.getCustomLinkUrl() ).append( '\n' );
            }
            return sb.toString();
        }
        else
        {
            return "";
        }
    }

}
