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
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringArrayValue extends AbstractValue implements StoredValue
{
    private final List<String> values;

    public StringArrayValue( final List<String> values )
    {
        this.values = values == null ? Collections.emptyList() : Collections.unmodifiableList( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public StringArrayValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new StringArrayValue( Collections.emptyList() );
                }
                else
                {
                    List<String> srcList = JsonUtil.deserializeStringList( input );
                    srcList = srcList == null ? Collections.emptyList() : srcList;
                    while ( srcList.contains( null ) )
                    {
                        srcList.remove( null );
                    }
                    return new StringArrayValue( Collections.unmodifiableList( srcList ) );
                }
            }

            public StringArrayValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final List<XmlElement> valueElements = settingElement.getChildren( StoredConfigXmlConstants.XML_ELEMENT_VALUE );
                final List<String> values = new ArrayList<>();
                for ( final XmlElement loopValueElement : valueElements )
                {
                    final String value = loopValueElement.getText();
                    values.add( value );
                }
                return new StringArrayValue( values );
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final String value : this.values )
        {
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            valueElement.addText( value );
            returnList.add( valueElement );
        }
        return returnList;
    }

    public List<String> toNativeObject( )
    {
        return Collections.unmodifiableList( values );
    }

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

    public String toDebugString( final Locale locale )
    {
        if ( values != null && !values.isEmpty() )
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
