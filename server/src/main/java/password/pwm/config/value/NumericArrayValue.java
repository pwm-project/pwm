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
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NumericArrayValue extends AbstractValue implements StoredValue
{
    private final List<Long> values;

    public NumericArrayValue( final List<Long> values )
    {
        this.values = values == null ? Collections.emptyList() : Collections.unmodifiableList( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public NumericArrayValue fromJson( final PwmSetting pwmSetting, final String value )
            {
                final long[] longArray = JsonFactory.get().deserialize( value, long[].class );
                final List<Long> list = Arrays.stream( longArray ).boxed().collect( Collectors.toList() );
                return new NumericArrayValue( list );
            }

            @Override
            public NumericArrayValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey input )
            {
                final List<Long> returnList = new ArrayList<>(  );
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                for ( final XmlElement element : valueElements )
                {
                    element.getText().ifPresent( strValue ->
                    {
                        final Long longValue = Long.parseLong( strValue );
                        returnList.add( longValue );
                    } );
                }
                return new NumericArrayValue( returnList );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>( values.size() );
        for ( final Long value : this.values )
        {
            final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );
            valueElement.setText( String.valueOf( value ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public Object toNativeObject( )
    {
        return values;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        return Collections.emptyList();
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        if ( !CollectionUtil.isEmpty( values ) )
        {
            final StringBuilder sb = new StringBuilder();
            for ( final Iterator<Long> valueIterator = values.iterator(); valueIterator.hasNext(); )
            {
                sb.append( valueIterator.next() );
                if ( valueIterator.hasNext() )
                {
                    sb.append( '\n' );
                }
            }
            return sb.toString();
        }
        else
        {
            return "";
        }
    }

}
