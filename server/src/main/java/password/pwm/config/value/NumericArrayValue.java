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
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class NumericArrayValue extends AbstractValue implements StoredValue
{
    List<Long> values;

    public NumericArrayValue( final List<Long> values )
    {
        this.values = values;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public NumericArrayValue fromJson( final String value )
            {
                final long[] longArray = JsonUtil.deserialize( value, long[].class );
                final List<Long> list = Arrays.stream( longArray ).boxed().collect( Collectors.toList() );
                return new NumericArrayValue( list );
            }

            public NumericArrayValue fromXmlElement( final PwmSetting pwmSetting, final Element settingElement, final PwmSecurityKey input )
            {
                final List<Long> returnList = new ArrayList<>(  );
                final List<Element> valueElements = settingElement.getChildren( "value" );
                for ( final Element element : valueElements )
                {
                    final String strValue = element.getText();
                    final Long longValue = Long.parseLong( strValue );
                    returnList.add( longValue );
                }
                return new NumericArrayValue( returnList );
            }
        };
    }

    @Override
    public List<Element> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final List<Element> returnList = new ArrayList<>();
        for ( final Long value : this.values )
        {
            final Element valueElement = new Element( valueElementName );
            valueElement.addContent( String.valueOf( value ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public Object toNativeObject( )
    {
        return values;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        return Collections.emptyList();
    }

    public String toDebugString( final Locale locale )
    {
        if ( !JavaHelper.isEmpty( values ) )
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
