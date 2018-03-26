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
import password.pwm.bean.EmailItemBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class EmailValue extends AbstractValue implements StoredValue
{
    //key is locale identifier
    final Map<String, EmailItemBean> values;

    EmailValue( final Map<String, EmailItemBean> values )
    {
        this.values = values;
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public EmailValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new EmailValue( Collections.emptyMap() );
                }
                else
                {
                    Map<String, EmailItemBean> srcList = JsonUtil.deserialize( input,
                            new TypeToken<Map<String, EmailItemBean>>()
                            {
                            }
                    );

                    srcList = srcList == null ? Collections.emptyMap() : srcList;
                    srcList.remove( null );
                    return new EmailValue( Collections.unmodifiableMap( srcList ) );
                }
            }

            public EmailValue fromXmlElement(
                    final Element settingElement,
                    final PwmSecurityKey input
            )
                    throws PwmOperationalException
            {
                final Map<String, EmailItemBean> values = new TreeMap<>();
                {
                    final List valueElements = settingElement.getChildren( "value" );
                    for ( final Object loopValue : valueElements )
                    {
                        final Element loopValueElement = ( Element ) loopValue;
                        final String value = loopValueElement.getText();
                        if ( value != null && value.length() > 0 )
                        {
                            final String localeValue = loopValueElement.getAttribute(
                                    "locale" ) == null ? "" : loopValueElement.getAttribute( "locale" ).getValue();
                            values.put( localeValue, JsonUtil.deserialize( value, EmailItemBean.class ) );
                        }
                    }
                }
                return new EmailValue( values );
            }
        };
    }

    public List<Element> toXmlValues( final String valueElementName, final PwmSecurityKey pwmSecurityKey  )
    {
        final List<Element> returnList = new ArrayList<>();
        for ( final Map.Entry<String, EmailItemBean> entry : values.entrySet() )
        {
            final String localeValue = entry.getKey();
            final EmailItemBean emailItemBean = entry.getValue();
            final Element valueElement = new Element( valueElementName );
            if ( localeValue.length() > 0 )
            {
                valueElement.setAttribute( "locale", localeValue );
            }
            valueElement.addContent( JsonUtil.serialize( emailItemBean ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    public Map<String, EmailItemBean> toNativeObject( )
    {
        return Collections.unmodifiableMap( values );
    }

    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( values == null || values.isEmpty() || values.values().iterator().next() == null )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        for ( final Map.Entry<String, EmailItemBean> entry : values.entrySet() )
        {
            final String loopLocale = entry.getKey();
            final EmailItemBean emailItemBean = entry.getValue();

            if ( emailItemBean.getSubject() == null || emailItemBean.getSubject().length() < 1 )
            {
                return Collections.singletonList( "subject field is required " + ( loopLocale.length() > 0 ? " for locale " + loopLocale : "" ) );
            }

            if ( emailItemBean.getFrom() == null || emailItemBean.getFrom().length() < 1 )
            {
                return Collections.singletonList( "from field is required" + ( loopLocale.length() > 0 ? " for locale " + loopLocale : "" ) );
            }

            if ( emailItemBean.getBodyPlain() == null || emailItemBean.getBodyPlain().length() < 1 )
            {
                return Collections.singletonList( "plain body field is required" + ( loopLocale.length() > 0 ? " for locale " + loopLocale : "" ) );
            }
        }

        return Collections.emptyList();
    }

    public String toDebugString( final Locale locale )
    {
        if ( values == null )
        {
            return "No Email Item";
        }
        final StringBuilder sb = new StringBuilder();
        for ( final Map.Entry<String, EmailItemBean> entry : values.entrySet() )
        {
            final String localeKey = entry.getKey();
            final EmailItemBean emailItemBean = entry.getValue();
            sb.append( "EmailItem " ).append( LocaleHelper.debugLabel( LocaleHelper.parseLocaleString( localeKey ) ) ).append( ": \n" );
            sb.append( "  To:" ).append( emailItemBean.getTo() ).append( "\n" );
            sb.append( "From:" ).append( emailItemBean.getFrom() ).append( "\n" );
            sb.append( "Subj:" ).append( emailItemBean.getSubject() ).append( "\n" );
            sb.append( "Body:" ).append( emailItemBean.getBodyPlain() ).append( "\n" );
            sb.append( "Html:" ).append( emailItemBean.getBodyHtml() ).append( "\n" );
        }
        return sb.toString();
    }
}
