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

import org.jrivard.xmlchai.XmlElement;
import org.jrivard.xmlchai.XmlFactory;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StringArrayValue extends AbstractValue implements StoredValue
{
    private final List<String> values;

    private StringArrayValue( final PwmSetting pwmSetting, final List<String> values )
    {
        final List<String> copiedValues = new ArrayList<>( CollectionUtil.stripNulls( values ) );

        if ( pwmSetting != null && pwmSetting.getFlags().contains( PwmSettingFlag.Sorted ) )
        {
            Collections.sort( copiedValues );
        }

        this.values = List.copyOf( copiedValues );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public StringArrayValue fromJson( final PwmSetting pwmSetting, final String input )
            {
                if ( StringUtil.isEmpty( input ) )
                {
                    return new StringArrayValue( pwmSetting, Collections.emptyList() );
                }
                else
                {
                    return new StringArrayValue( pwmSetting, JsonFactory.get().deserializeStringList( input ) );
                }
            }

            @Override
            public StringArrayValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final List<String> values = settingElement.getChildren( StoredConfigXmlConstants.XML_ELEMENT_VALUE ).stream()
                        .map( XmlElement::getText )
                        .flatMap( Optional::stream )
                        .collect( Collectors.toList() );

                return new StringArrayValue( pwmSetting, values );
            }
        };
    }

    public static StringArrayValue create( final List<String> values )
    {
        return new StringArrayValue( null, values );
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>( values.size() );
        for ( final String value : this.values )
        {
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            valueElement.setText( value );
            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public List<String> toNativeObject( )
    {
        return values;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( values == null || values.size() < 1 || values.get( 0 ).length() < 1 )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        for ( final String loopValue : values )
        {
            final Matcher matcher = pattern.matcher( loopValue );
            if ( loopValue.length() > 0 && !matcher.matches() )
            {
                return Collections.singletonList( "incorrect value format for value '" + loopValue + "'" );
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        if ( values != null && !values.isEmpty() )
        {
            final StringBuilder sb = new StringBuilder();
            for ( final Iterator<String> valueIterator = values.iterator(); valueIterator.hasNext(); )
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
