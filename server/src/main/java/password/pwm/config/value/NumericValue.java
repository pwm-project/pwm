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
import password.pwm.config.PwmSettingProperty;
import password.pwm.config.StoredValue;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.Collections;
import java.util.List;

public class NumericValue extends AbstractValue implements StoredValue
{
    long value;

    public NumericValue( final long value )
    {
        this.value = value;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public NumericValue fromJson( final String value )
            {
                return new NumericValue( JsonUtil.deserialize( value, Long.class ) );
            }

            public NumericValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey input )
            {
                final XmlElement valueElement = settingElement.getChild( "value" );
                final String value = valueElement.getText();
                return new NumericValue( normalizeValue( pwmSetting, Long.parseLong( value ) ) );
            }
        };
    }

    private static long normalizeValue( final PwmSetting pwmSetting, final long value )
    {
        final long minValue = Long.parseLong( pwmSetting.getProperties().getOrDefault( PwmSettingProperty.Minimum, "0" ) );
        final long maxValue = Long.parseLong( pwmSetting.getProperties().getOrDefault( PwmSettingProperty.Maximum, "0" ) );

        if ( minValue > 0 && value < minValue )
        {
            return minValue;
        }

        if ( maxValue > 0 && value > maxValue )
        {
            return maxValue;
        }

        return value;
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
        valueElement.addText( Long.toString( value ) );
        return Collections.singletonList( valueElement );
    }

    @Override
    public Object toNativeObject( )
    {
        return value;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        return Collections.emptyList();
    }
}
