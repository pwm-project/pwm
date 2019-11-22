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
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalizedStringArrayValue extends AbstractValue implements StoredValue
{
    private final Map<String, List<String>> values;

    LocalizedStringArrayValue( final Map<String, List<String>> values )
    {
        this.values = values == null ? Collections.emptyMap() : Collections.unmodifiableMap( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public LocalizedStringArrayValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new LocalizedStringArrayValue( Collections.emptyMap() );
                }
                else
                {
                    Map<String, List<String>> srcMap = JsonUtil.deserialize( input, new TypeToken<Map<String, List<String>>>()
                    {
                    } );
                    srcMap = srcMap == null ? Collections.emptyMap() : new TreeMap<>( srcMap );
                    return new LocalizedStringArrayValue( Collections.unmodifiableMap( srcMap ) );
                }
            }

            public LocalizedStringArrayValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                final Map<String, List<String>> values = new TreeMap<>();
                for ( final XmlElement loopValueElement  : valueElements )
                {
                    final String localeString = loopValueElement.getAttributeValue(
                            "locale" ) == null ? "" : loopValueElement.getAttributeValue( "locale" );
                    final String value = loopValueElement.getText();
                    List<String> valueList = values.get( localeString );
                    if ( valueList == null )
                    {
                        valueList = new ArrayList<>();
                        values.put( localeString, valueList );
                    }
                    valueList.add( value );
                }
                return new LocalizedStringArrayValue( values );
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final Map.Entry<String, List<String>> entry : values.entrySet() )
        {
            final String locale = entry.getKey();
            for ( final String value : entry.getValue() )
            {
                final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
                valueElement.addText( value );
                if ( locale != null && locale.length() > 0 )
                {
                    valueElement.setAttribute( "locale", locale );
                }
                returnList.add( valueElement );
            }
        }
        return returnList;
    }

    public Map<String, List<String>> toNativeObject( )
    {
        return Collections.unmodifiableMap( values );
    }

    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( values == null || values.size() < 1 || values.keySet().iterator().next().length() < 1 )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        for ( final List<String> loopValues : values.values() )
        {
            for ( final String loopValue : loopValues )
            {
                if ( loopValue != null && loopValue.length() > 0 )
                {
                    final Matcher matcher = pattern.matcher( loopValue );
                    if ( !matcher.matches() )
                    {
                        return Collections.singletonList( "incorrect value format for value '" + loopValue + "'" );
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        if ( values == null )
        {
            return "";
        }
        final StringBuilder sb = new StringBuilder();
        for ( final Map.Entry<String, List<String>> entry : values.entrySet() )
        {
            final String localeKey = entry.getKey();
            if ( !values.get( localeKey ).isEmpty() )
            {
                sb.append( "Locale: " ).append( LocaleHelper.debugLabel( LocaleHelper.parseLocaleString( localeKey ) ) ).append( "\n" );
                for ( final String value : entry.getValue() )
                {
                    sb.append( "  " ).append( value ).append( "\n" );
                }
            }
        }
        return sb.toString();
    }

}
