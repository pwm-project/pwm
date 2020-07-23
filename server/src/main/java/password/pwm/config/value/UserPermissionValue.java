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
import org.apache.commons.lang3.StringUtils;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigXmlConstants;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmOperationalException;
import password.pwm.i18n.Display;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.XmlElement;
import password.pwm.util.java.XmlFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UserPermissionValue extends AbstractValue implements StoredValue
{
    private final List<UserPermission> values;

    private boolean needsXmlUpdate;

    public UserPermissionValue( final List<UserPermission> values )
    {
        this.values = values == null ? Collections.emptyList() : Collections.unmodifiableList( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            public UserPermissionValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new UserPermissionValue( Collections.emptyList() );
                }
                else
                {
                    List<UserPermission> srcList = JsonUtil.deserialize( input, new TypeToken<List<UserPermission>>()
                    {
                    } );
                    srcList = srcList == null ? Collections.emptyList() : srcList;
                    while ( srcList.contains( null ) )
                    {
                        srcList.remove( null );
                    }
                    return new UserPermissionValue( Collections.unmodifiableList( srcList ) );
                }
            }

            public UserPermissionValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
                    throws PwmOperationalException
            {
                final boolean newType = "2".equals(
                        settingElement.getAttributeValue( StoredConfigXmlConstants.XML_ATTRIBUTE_SYNTAX_VERSION ) );
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                final List<UserPermission> values = new ArrayList<>();
                for ( final XmlElement loopValueElement : valueElements )
                {
                    final String value = loopValueElement.getText();
                    if ( value != null && !value.isEmpty() )
                    {
                        if ( newType )
                        {
                            final UserPermission userPermission = JsonUtil.deserialize( value, UserPermission.class );
                            values.add( userPermission );
                        }
                        else
                        {
                            values.add( UserPermission.builder()
                                    .type( UserPermission.Type.ldapQuery )
                                    .ldapQuery( value )
                                    .build() );
                        }
                    }
                }
                final UserPermissionValue userPermissionValue = new UserPermissionValue( values );
                userPermissionValue.needsXmlUpdate = !newType;
                return userPermissionValue;
            }
        };
    }

    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>();
        for ( final UserPermission value : values )
        {
            final XmlElement valueElement = XmlFactory.getFactory().newElement( valueElementName );
            valueElement.addText( JsonUtil.serialize( value ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    public List<UserPermission> toNativeObject( )
    {
        return Collections.unmodifiableList( values );
    }

    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        final List<String> returnObj = new ArrayList<>();
        for ( final UserPermission userPermission : values )
        {
            try
            {
                validateLdapSearchFilter( userPermission.getLdapQuery() );
            }
            catch ( final IllegalArgumentException e )
            {
                returnObj.add( e.getMessage() + " for filter " + userPermission.getLdapQuery() );
            }
        }
        return returnObj;
    }

    public boolean isNeedsXmlUpdate( )
    {
        return needsXmlUpdate;
    }

    private void validateLdapSearchFilter( final String filter )
    {
        if ( filter == null || filter.isEmpty() )
        {
            return;
        }

        final int leftParens = StringUtils.countMatches( filter, "(" );
        final int rightParens = StringUtils.countMatches( filter, ")" );

        if ( leftParens != rightParens )
        {
            throw new IllegalArgumentException( "unbalanced parentheses" );
        }
    }

    @Override
    public int currentSyntaxVersion( )
    {
        return 2;
    }

    public String toDebugString( final Locale locale )
    {
        if ( values != null && !values.isEmpty() )
        {
            final StringBuilder sb = new StringBuilder();
            int counter = 0;
            for ( final UserPermission userPermission : values )
            {
                sb.append( "UserPermission" );
                if ( values.size() > 1 )
                {
                    sb.append( counter );
                }
                sb.append( "-" );
                sb.append( userPermission.getType() == null ? UserPermission.Type.ldapQuery.toString() : userPermission.getType().toString() );
                sb.append( ": [" );
                sb.append( "Profile:" ).append(
                        userPermission.getLdapProfileID() == null
                                ? "All"
                                : userPermission.getLdapProfileID()
                );
                sb.append( " Base:" ).append(
                        userPermission.getLdapBase() == null
                                ? Display.getLocalizedMessage( locale, Display.Value_NotApplicable, null )
                                : userPermission.getLdapBase()
                );
                if ( userPermission.getLdapQuery() != null )
                {
                    sb.append( " Query:" ).append( userPermission.getLdapQuery() );
                }
                sb.append( "]" );
                counter++;
                if ( counter != values.size() )
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
