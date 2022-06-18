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
            @Override
            public OptionListValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new OptionListValue( Collections.emptySet() );
                }
                else
                {
                    List<String> srcList = JsonFactory.get().deserializeStringList( input );
                    srcList = srcList == null ? Collections.emptyList() : srcList;
                    srcList = CollectionUtil.stripNulls( srcList );
                    return new OptionListValue( Set.copyOf( srcList ) );
                }
            }

            @Override
            public OptionListValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                final Set<String> values = new TreeSet<>();
                for ( final XmlElement loopValueElement : valueElements )
                {
                    loopValueElement.getText().ifPresent( value -> values.add( value.trim() ) );
                }
                return new OptionListValue( Collections.unmodifiableSet( values ) );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>( values.size() );
        for ( final String value : values )
        {
            final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );
            valueElement.setText( value );
            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public Set<String> toNativeObject( )
    {
        return Collections.unmodifiableSet( values );
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        return Collections.emptyList();
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        final StringBuilder sb = new StringBuilder();
        for ( final Iterator valueIterator = values.iterator(); valueIterator.hasNext(); )
        {
            sb.append( valueIterator.next() );
            if ( valueIterator.hasNext() )
            {
                sb.append( '\n' );
            }
        }
        return sb.toString();
    }
}
