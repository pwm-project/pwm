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
import org.jdom2.Element;
import password.pwm.config.CustomLinkConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class CustomLinkValue extends AbstractValue implements StoredValue
{
    final List<CustomLinkConfiguration> values;

    public CustomLinkValue( final List<CustomLinkConfiguration> values )
    {
        this.values = values;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public CustomLinkValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new CustomLinkValue( Collections.emptyList() );
                }
                else
                {
                    List<CustomLinkConfiguration> srcList = JsonUtil.deserialize( input, new TypeToken<List<CustomLinkConfiguration>>()
                    {
                    } );
                    srcList = srcList == null ? Collections.emptyList() : srcList;
                    while ( srcList.contains( null ) )
                    {
                        srcList.remove( null );
                    }
                    return new CustomLinkValue( Collections.unmodifiableList( srcList ) );
                }
            }

            public CustomLinkValue fromXmlElement( final Element settingElement, final PwmSecurityKey key )
                    throws PwmOperationalException
            {
                final List valueElements = settingElement.getChildren( "value" );
                final List<CustomLinkConfiguration> values = new ArrayList<>();
                for ( final Object loopValue : valueElements )
                {
                    final Element loopValueElement = ( Element ) loopValue;
                    final String value = loopValueElement.getText();
                    if ( value != null && value.length() > 0 && loopValueElement.getAttribute( "locale" ) == null )
                    {
                        values.add( JsonUtil.deserialize( value, CustomLinkConfiguration.class ) );
                    }
                }
                return new CustomLinkValue( values );
            }
        };
    }

    public List<Element> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final List<Element> returnList = new ArrayList<>();
        for ( final CustomLinkConfiguration value : values )
        {
            final Element valueElement = new Element( valueElementName );
            valueElement.addContent( JsonUtil.serialize( value ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    public List<CustomLinkConfiguration> toNativeObject( )
    {
        return Collections.unmodifiableList( values );
    }

    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( values == null || values.size() < 1 || values.get( 0 ) == null )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        final Set<String> seenNames = new HashSet<>();
        for ( final CustomLinkConfiguration loopConfig : values )
        {
            if ( seenNames.contains( loopConfig.getName().toLowerCase() ) )
            {
                return Collections.singletonList( "each form name must be unique: " + loopConfig.getName() );
            }
            seenNames.add( loopConfig.getName().toLowerCase() );
        }

        return Collections.emptyList();
    }

    public String toDebugString( final Locale locale )
    {
        if ( values != null && !values.isEmpty() )
        {
            final StringBuilder sb = new StringBuilder();
            for ( final CustomLinkConfiguration formRow : values )
            {
                sb.append( "Link Name:" ).append( formRow.getName() ).append( "\n" );
                sb.append( " Type:" ).append( formRow.getType() );
                sb.append( "\n" );
                sb.append( " Description:" ).append( JsonUtil.serializeMap( formRow.getLabels() ) ).append( "\n" );
                sb.append( " New Window:" ).append( formRow.isCustomLinkNewWindow() ).append( "\n" );
                sb.append( " Url:" ).append( formRow.getCustomLinkUrl() ).append( "\n" );
            }
            return sb.toString();
        }
        else
        {
            return "";
        }
    }

}
