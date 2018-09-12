/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package password.pwm.config.value;

import com.google.gson.reflect.TypeToken;
import org.jdom2.CDATA;
import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.JsonUtil;
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
    final Map<String, List<String>> values;

    LocalizedStringArrayValue( final Map<String, List<String>> values )
    {
        this.values = values;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public LocalizedStringArrayValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new LocalizedStringArrayValue( Collections.<String, List<String>>emptyMap() );
                }
                else
                {
                    Map<String, List<String>> srcMap = JsonUtil.deserialize( input, new TypeToken<Map<String, List<String>>>()
                    {
                    } );
                    srcMap = srcMap == null ? Collections.<String, List<String>>emptyMap() : new TreeMap<>( srcMap );
                    return new LocalizedStringArrayValue( Collections.unmodifiableMap( srcMap ) );
                }
            }

            public LocalizedStringArrayValue fromXmlElement( final Element settingElement, final PwmSecurityKey key )
            {
                final List valueElements = settingElement.getChildren( "value" );
                final Map<String, List<String>> values = new TreeMap<>();
                for ( final Object loopValue : valueElements )
                {
                    final Element loopValueElement = ( Element ) loopValue;
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

    public List<Element> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final List<Element> returnList = new ArrayList<>();
        for ( final Map.Entry<String, List<String>> entry : values.entrySet() )
        {
            final String locale = entry.getKey();
            for ( final String value : entry.getValue() )
            {
                final Element valueElement = new Element( valueElementName );
                valueElement.addContent( new CDATA( value ) );
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
