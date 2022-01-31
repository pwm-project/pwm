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
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.stored.XmlOutputProcessData;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.secure.PwmSecurityKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class FormValue extends AbstractValue implements StoredValue
{
    private final List<FormConfiguration> values;

    public FormValue( final List<FormConfiguration> values )
    {
        this.values = values == null ? Collections.emptyList() : Collections.unmodifiableList( values );
    }

    public static StoredValueFactory factory( )
    {
        return new StoredValueFactory()
        {
            @Override
            public FormValue fromJson( final String input )
            {
                if ( input == null )
                {
                    return new FormValue( Collections.emptyList() );
                }
                else
                {
                    List<FormConfiguration> srcList = JsonFactory.get().deserializeList( input, FormConfiguration.class );
                    srcList = srcList == null ? Collections.emptyList() : srcList;
                    while ( srcList.contains( null ) )
                    {
                        srcList.remove( null );
                    }
                    return new FormValue( Collections.unmodifiableList( srcList ) );
                }
            }

            @Override
            public FormValue fromXmlElement( final PwmSetting pwmSetting, final XmlElement settingElement, final PwmSecurityKey key )
                    throws PwmOperationalException
            {
                final boolean oldType = PwmSettingSyntax.LOCALIZED_STRING_ARRAY.toString().equals(
                        settingElement.getAttribute( "syntax" ).orElse( "" ) );
                final List<XmlElement> valueElements = settingElement.getChildren( "value" );
                final List<FormConfiguration> values = new ArrayList<>();
                for ( final XmlElement loopValueElement  : valueElements )
                {
                    final Optional<String> value = loopValueElement.getText();
                    if ( value.isPresent() && loopValueElement.getAttribute( "locale" ).isEmpty() )
                    {
                        if ( oldType )
                        {
                            values.add( FormConfiguration.parseOldConfigString( value.get() ) );
                        }
                        else
                        {
                            values.add( JsonFactory.get().deserialize( value.get(), FormConfiguration.class ) );
                        }
                    }
                }
                return new FormValue( values );
            }
        };
    }

    @Override
    public List<XmlElement> toXmlValues( final String valueElementName, final XmlOutputProcessData xmlOutputProcessData )
    {
        final List<XmlElement> returnList = new ArrayList<>( values.size() );
        for ( final FormConfiguration value : values )
        {
            final XmlElement valueElement = XmlChai.getFactory().newElement( valueElementName );
            valueElement.setText( JsonFactory.get().serialize( value ) );
            returnList.add( valueElement );
        }
        return returnList;
    }

    @Override
    public List<FormConfiguration> toNativeObject( )
    {
        return values;
    }

    @Override
    public List<String> validateValue( final PwmSetting pwmSetting )
    {
        if ( pwmSetting.isRequired() )
        {
            if ( values == null || values.size() < 1 || values.get( 0 ) == null )
            {
                return Collections.singletonList( "required value missing" );
            }
        }

        final Set<String> seenNames = new HashSet<>( values.size() );
        for ( final FormConfiguration loopConfig : values )
        {
            if ( seenNames.contains( loopConfig.getName().toLowerCase() ) )
            {
                return Collections.singletonList( "each form name must be unique: " + loopConfig.getName() );
            }
            seenNames.add( loopConfig.getName().toLowerCase() );
        }

        for ( final FormConfiguration loopConfig : values )
        {
            try
            {
                loopConfig.validate();
            }
            catch ( final PwmOperationalException e )
            {
                return Collections.singletonList( "format error: " + e.getErrorInformation().toDebugStr() );
            }
        }

        return Collections.emptyList();
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        if ( CollectionUtil.isEmpty( values ) )
        {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for ( final FormConfiguration formRow : values )
        {
            sb.append( "FormItem Name:" ).append( formRow.getName() ).append( '\n' );
            sb.append( " Type:" ).append( formRow.getType() );
            sb.append( " Min:" ).append( formRow.getMinimumLength() );
            sb.append( " Max:" ).append( formRow.getMaximumLength() );
            sb.append( " ReadOnly:" ).append( formRow.isReadonly() );
            sb.append( " Required:" ).append( formRow.isRequired() );
            sb.append( " Confirm:" ).append( formRow.isConfirmationRequired() );
            sb.append( " Unique:" ).append( formRow.isUnique() );
            sb.append( " Multi-Value:" ).append( formRow.isMultivalue() );
            sb.append( " Source:" ).append( formRow.getSource() );
            sb.append( '\n' );
            sb.append( " Label:" ).append( JsonFactory.get().serializeStringMap( formRow.getLabels() ) ).append( '\n' );
            sb.append( " Description:" ).append( JsonFactory.get().serializeStringMap( formRow.getDescription() ) ).append( '\n' );
            if ( formRow.getType() == FormConfiguration.Type.select && CollectionUtil.isEmpty( formRow.getSelectOptions() ) )
            {
                sb.append( " Select Options: " ).append( JsonFactory.get().serializeStringMap( formRow.getSelectOptions() ) ).append( '\n' );
            }
            if ( StringUtil.notEmpty( formRow.getRegex() ) )
            {
                sb.append( " Regex:" ).append( formRow.getRegex() )
                        .append( " Regex Error:" ).append( JsonFactory.get().serializeStringMap( formRow.getRegexErrors() ) );
            }
            if ( formRow.getType() == FormConfiguration.Type.photo )
            {
                sb.append( " MimeTypes: " ).append( StringUtil.collectionToString( formRow.getMimeTypes() ) ).append( '\n' );
                sb.append( " MaxSize: " ).append( formRow.getMaximumSize() ).append( '\n' );
            }
        }

        return sb.toString();
    }
}
