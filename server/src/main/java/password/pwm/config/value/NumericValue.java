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

import org.jdom2.Element;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.util.java.JsonUtil;
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

            public NumericValue fromXmlElement( final Element settingElement, final PwmSecurityKey input )
            {
                final Element valueElement = settingElement.getChild( "value" );
                final String value = valueElement.getText();
                return new NumericValue( Long.parseLong( value ) );
            }
        };
    }

    @Override
    public List<Element> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final Element valueElement = new Element( valueElementName );
        valueElement.addContent( Long.toString( value ) );
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
