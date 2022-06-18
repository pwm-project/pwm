/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlElement;
import password.pwm.bean.EmailItemBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;

public class EmailValue extends AbstractValue implements StoredValue
{
    //key is locale identifier
    private final Map<String, EmailItemBean> values;

    EmailValue( final Map<String, EmailItemBean> values )
    {
        this.values = values == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap( CollectionUtil.stripNulls( values ) );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public EmailValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new EmailValue( Collections.emptyMap() );
                }
                else
                {
                    final Map<String, EmailItemBean> srcMap = JsonFactory.get().deserializeMap( input, String.class, EmailItemBean.class );
                    return new EmailValue( srcMap );
                }
            }

            @Override
            public EmailValue fromXmlElement(
                    final PwmSetting pwmSetting,
                    final XmlElement settingElement,
                    final PwmSecurityKey input
            )
            {
                final Map<String, EmailItemBean> values = new TreeMap<>();
                {
                    final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                    for ( final XmlElement loopValueElement : valueElements )
                    {
                        loopValueElement.getText().ifPresent( value ->
                        {
                            final String localeValue = loopValueElement.getAttribute( "locale" ).orElse( "" );
                            values.put( localeValue, JsonFactory.get().deserialize( value, EmailItemBean.class ) );
                        } );
                    }
                }
                return new EmailValue( values );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>( values.size() );
        for ( final Map.Entry<String, EmailItemBean> entry : values.entrySet() )
        {
            final String localeValue = entry.getKey();
            final EmailItemBean emailItemBean = entry.getValue();
            final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );
            if ( localeValue.length() > 0 )
            {
                valueElement.setAttribute( "locale", localeValue );
            }
            valueElement.setText( JsonFactory.get().serialize( emailItemBean ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public Map<String, EmailItemBean> toNativeObject( )
    {
        return Collections.unmodifiableMap( values );
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        final int maxBodyChars = 500_000;

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
            final Supplier<String> localeMsg = () -> loopLocale.length() > 0 ? " for locale " + loopLocale : "";

            if ( emailItemBean.getSubject() == null || emailItemBean.getSubject().length() < 1 )
            {
                return Collections.singletonList( "subject field is required " + localeMsg.get() );
            }

            if ( emailItemBean.getFrom() == null || emailItemBean.getFrom().length() < 1 )
            {
                return Collections.singletonList( "from field is required" + localeMsg.get() );
            }

            if ( StringUtil.isEmpty( emailItemBean.getBodyPlain() ) )
            {
                return Collections.singletonList( "plain body field is required" + localeMsg.get() );
            }
            else if ( emailItemBean.getBodyPlain().length() > maxBodyChars )
            {
                return Collections.singletonList( "plain body field is too large" + localeMsg.get()
                        + ", chars=" + emailItemBean.getBodyPlain().length() + ", max=" + maxBodyChars );
            }

            if ( emailItemBean.getBodyHtml() != null && emailItemBean.getBodyHtml().length() > maxBodyChars )
            {
                return Collections.singletonList( "html body field is too large" + localeMsg.get()
                        + ", chars=" + emailItemBean.getBodyHtml().length() + ", max=" + maxBodyChars );
            }
        }

        return Collections.emptyList();
    }

    @Override
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
            sb.append( "  To:" ).append( emailItemBean.getTo() ).append( '\n' );
            sb.append( "From:" ).append( emailItemBean.getFrom() ).append( '\n' );
            sb.append( "Subj:" ).append( emailItemBean.getSubject() ).append( '\n' );
            sb.append( "Body:" ).append( emailItemBean.getBodyPlain() ).append( '\n' );
            sb.append( "Html:" ).append( emailItemBean.getBodyHtml() ).append( '\n' );
        }
        return sb.toString();
    }
}
