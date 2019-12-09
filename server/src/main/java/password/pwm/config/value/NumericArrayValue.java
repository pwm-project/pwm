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

import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
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
            public NumericArrayValue fromJson( final String value )
            {
                final long[] longArray = JsonUtil.deserialize( value, long[].class );
                final List<Long> list = Arrays.stream( longArray ).boxed().collect( Collectors.toList() );
                return new NumericArrayValue( list );
            }

            public NumericArrayValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey input )
            {
                final List<Long> returnList = new ArrayList<>(  );
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                for ( final XmlElement element : valueElements )
                {
                    final String strValue = element.getText();
                    final Long longValue = Long.parseLong( strValue );
                    returnList.add( longValue );
                }
                return new NumericArrayValue( returnList );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final Long value : this.values )
        {
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            valueElement.addText( String.valueOf( value ) );
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

    public String toDebugString( final Locale locale )
    {
        if ( !JavaHelper.isEmpty( values ) )
        {
            final StringBuilder sb = new StringBuilder();
            for ( final Iterator valueIterator = values.iterator(); valueIterator.hasNext(); )
            {
                sb.append( valueIterator.next() );
                if ( valueIterator.hasNext() )
                {
                    sb.append( "\n" );
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
