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
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
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

public class LocalizedStringValue extends AbstractValue implements StoredValue
{
    final Map<String, String> value;

    public LocalizedStringValue( final Map<String, String> values )
    {
        this.value = Collections.unmodifiableMap( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public LocalizedStringValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new LocalizedStringValue( Collections.emptyMap() );
                }
                else
                {
                    Map<String, String> srcMap = JsonUtil.deserialize( input, new TypeToken<Map<String, String>>()
                    {
                    } );
                    srcMap = srcMap == null ? Collections.emptyMap() : new TreeMap<>( srcMap );
                    return new LocalizedStringValue( Collections.unmodifiableMap( srcMap ) );
                }
            }

            public LocalizedStringValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final List<XmlElement> elements = settingElement.getChildren( "value" );
                final Map<String, String> values = new TreeMap<>();
                for ( final XmlElement loopValueElement : elements )
                {
                    final String localeString = loopValueElement.getAttributeValue( "locale" );
                    final String value = loopValueElement.getText();
                    values.put( localeString == null ? "" : localeString, value );
                }
                return new LocalizedStringValue( values );
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final Map.Entry<String, String> entry : value.entrySet() )
        {
            final String locale = entry.getKey();
            final String loopValue = entry.getValue();
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            valueElement.addText( loopValue );
            if ( locale != null && locale.length() > 0 )
            {
                valueElement.setAttribute( "locale", locale );
            }
            returnList.add( valueElement );
        }
        return returnList;
    }

    public Map<String, String> toNativeObject( )
    {
        return Collections.unmodifiableMap( value );
    }

    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( value == null || value.size() < 1 || value.values().iterator().next().length() < 1 )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        for ( final String loopValue : value.values() )
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
        final StringBuilder sb = new StringBuilder();
        for ( final Map.Entry<String, String> entry : value.entrySet() )
        {
            final String localeKey = entry.getKey();
            if ( value.size() > 1 )
            {
                sb.append( "Locale: " ).append( LocaleHelper.debugLabel( LocaleHelper.parseLocaleString( localeKey ) ) ).append( "\n" );
            }
            sb.append( " " ).append( entry.getValue() ).append( "\n" );
        }
        return sb.toString();
    }
}
