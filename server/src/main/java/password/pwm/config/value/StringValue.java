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

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingFlag;
import password.pwm.config.StoredValue;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringValue extends AbstractValue implements StoredValue
{
    protected String value;

    StringValue( )
    {
    }

    public StringValue( final String value )
    {
        this.value = value == null ? "" : value;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public StringValue fromJson( final String input )
            {
                final String newValue = JsonUtil.deserialize( input, String.class );
                return new StringValue( newValue );
            }

            public StringValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
            {
                final XmlElement valueElement = settingElement.getChild( "value" );
                return new StringValue( valueElement == null ? "" : valueElement.getText() );
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
        valueElement.addText( value );
        return Collections.singletonList( valueElement );
    }

    public String toNativeObject( )
    {
        return value;
    }

    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( value == null || value.length() < 1 )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        final Pattern pattern = pwmSetting.getRegExPattern();
        if ( pattern != null && value != null )
        {
            final Matcher matcher = pattern.matcher( value );
            if ( value != null && value.length() > 0 && !matcher.matches() )
            {
                return Collections.singletonList( "incorrect value format for value '" + value + "'" );
            }
        }

        if ( pwmSetting.getFlags().contains( PwmSettingFlag.emailSyntax ) )
        {
            if ( value != null )
            {
                if ( !FormConfiguration.testEmailAddress( null, value ) )
                {
                    return Collections.singletonList( "Invalid email address format: '" + value + "'" );
                }
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString(
            final Locale locale
    )
    {
        return value == null ? "" : value;
    }
}
