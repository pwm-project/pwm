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
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class OptionListValue extends AbstractValue implements StoredValue
{
    private final Set<String> values;

    public OptionListValue( final Set<String> values )
    {
        this.values = values == null ? Collections.emptySet() : Collections.unmodifiableSet( new TreeSet<>( values ) );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public OptionListValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new OptionListValue( Collections.emptySet() );
                }
                else
                {
                    Set<String> srcList = JsonUtil.deserialize( input, new TypeToken<Set<String>>()
                    {
                    } );
                    srcList = srcList == null ? Collections.emptySet() : srcList;
                    while ( srcList.contains( null ) )
                    {
                        srcList.remove( null );
                    }
                    return new OptionListValue( Collections.unmodifiableSet( srcList ) );
                }
            }

            public OptionListValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
                    throws PwmOperationalException
            {
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                final Set<String> values = new TreeSet<>();
                for ( final XmlElement loopValueElement : valueElements )
                {
                    final String value = loopValueElement.getText();
                    if ( value != null && !value.trim().isEmpty() )
                    {
                        values.add( value );
                    }
                }
                return new OptionListValue( values );
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final String value : values )
        {
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            valueElement.addText( value );
            returnList.add( valueElement );
        }
        return returnList;
    }

    public Set<String> toNativeObject( )
    {
        return Collections.unmodifiableSet( values );
    }

    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        return Collections.emptyList();
    }

    public String toDebugString( final Locale locale )
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
}
