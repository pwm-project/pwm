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

package password.pwm.config.value.data;

import lombok.Builder;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmDataValidationException;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.i18n.Display;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Value
@Builder( toBuilder = true )
public class FormConfiguration implements Serializable
{
    public enum Type
    {
        text,
        email,
        number,
        password,
        random,
        tel,
        hidden,
        date,
        datetime,
        time,
        week,
        month,
        url,
        select,
        userDN,
        checkbox,
        photo,
    }

    public enum Source
    {
        ldap,
        remote,
        bogus,
    }

    @Builder.Default
    private String name = "";

    @Builder.Default
    private int minimumLength = 0;

    @Builder.Default
    private int maximumLength = 255;

    @Builder.Default
    private Type type = Type.text;

    @Builder.Default
    private Source source = Source.ldap;

    private boolean required;
    private boolean confirmationRequired;
    private boolean readonly;
    private boolean unique;
    private boolean multivalue;

    @Builder.Default
    private Map<String, String> labels = Collections.singletonMap( "", "" );

    @Builder.Default
    private Map<String, String> regexErrors = Collections.singletonMap( "", "" );

    @Builder.Default
    private Map<String, String> description = Collections.singletonMap( "", "" );

    @Builder.Default
    private String regex = "";

    @Builder.Default
    private String placeholder = "";

    @Builder.Default
    private String javascript = "";

    @Builder.Default
    private Map<String, String> selectOptions = Collections.emptyMap();

    @Builder.Default
    private List<String> mimeTypes = Arrays.asList(
            "image/gif",
            "image/png",
            "image/jpeg",
            "image/bmp",
            "image/webp"
    );

    @Builder.Default
    private int maximumSize = 65000;

    public static FormConfiguration parseOldConfigString( final String config )
            throws PwmOperationalException
    {
        if ( config == null )
        {
            throw new NullPointerException( "config can not be null" );
        }

        final FormConfiguration.FormConfigurationBuilder builder = FormConfiguration.builder();
        final StringTokenizer st = new StringTokenizer( config, ":" );

        // attribute name
        builder.name( st.nextToken() );

        // label
        builder.labels( Collections.singletonMap( "", st.nextToken() ) );

        // type
        {
            final String typeStr = st.nextToken();
            try
            {
                builder.type( Type.valueOf( typeStr.toLowerCase() ) );
            }
            catch ( final IllegalArgumentException e )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] {
                        "unknown type for form config: " + typeStr,
                } ) );
            }
        }

        //minimum length
        try
        {
            builder.minimumLength( Integer.parseInt( st.nextToken() ) );
        }
        catch ( final NumberFormatException e )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] {
                    "invalid minimum length type for form config: " + e.getMessage(),
            } ) );
        }

        //maximum length
        try
        {
            builder.maximumLength( Integer.parseInt( st.nextToken() ) );
        }
        catch ( final NumberFormatException e )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] {
                    "invalid maximum length type for form config: " + e.getMessage(),
            } ) );
        }

        //required
        builder.required( Boolean.TRUE.toString().equalsIgnoreCase( st.nextToken() ) );

        //confirmation
        builder.confirmationRequired( Boolean.TRUE.toString().equalsIgnoreCase( st.nextToken() ) );

        return builder.build();
    }

    public void validate( ) throws PwmOperationalException
    {
        if ( StringUtil.isEmpty( this.getName() ) )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] {
                    " form field name is required",
            } ) );
        }

        if ( this.getType() == null )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] {
                    " type is required for field " + this.getName(),
            } ) );
        }

        if ( labels == null || this.labels.isEmpty() || this.getLabel( PwmConstants.DEFAULT_LOCALE ) == null || this.getLabel( PwmConstants.DEFAULT_LOCALE ).length() < 1 )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] {
                    " a default label value is required for " + this.getName(),
            } ) );
        }

        if ( this.getRegex() != null && this.getRegex().length() > 0 )
        {
            try
            {
                Pattern.compile( this.getRegex() );
            }
            catch ( final PatternSyntaxException e )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] {
                        " regular expression for '" + this.getName() + " ' is not valid: " + e.getMessage(),
                } ) );
            }
        }

        if ( this.getType() == Type.select )
        {
            if ( this.getSelectOptions() == null || this.getSelectOptions().isEmpty() )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, null, new String[] {
                        " field '" + this.getName() + " ' is type select, but no select options are defined",
                } ) );
            }
        }
    }

    public String getLabel( final Locale locale )
    {
        return LocaleHelper.resolveStringKeyLocaleMap( locale, labels );
    }

    public String getRegexError( final Locale locale )
    {
        return LocaleHelper.resolveStringKeyLocaleMap( locale, regexErrors );
    }

    public String getDescription( final Locale locale )
    {
        return LocaleHelper.resolveStringKeyLocaleMap( locale, description );
    }

    public Source getSource( )
    {
        return source == null ? Source.ldap : source;
    }

    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( !( o instanceof FormConfiguration ) )
        {
            return false;
        }

        final FormConfiguration parameterConfig = ( FormConfiguration ) o;

        return !( name != null ? !name.equals( parameterConfig.name ) : parameterConfig.name != null );
    }

    public int hashCode( )
    {
        return ( name != null ? name.hashCode() : 0 );
    }

    public String toString( )
    {
        final StringBuilder sb = new StringBuilder();

        sb.append( "FormItem: " );
        sb.append( JsonUtil.serialize( this ) );

        return sb.toString();
    }


    public void checkValue( final Configuration config, final String value, final Locale locale )
            throws PwmDataValidationException, PwmUnrecoverableException
    {

        // ignore read only fields
        if ( readonly )
        {
            return;
        }

        //check if value is missing and required.
        if ( required && ( value == null || value.length() < 1 ) )
        {
            final ErrorInformation error = new ErrorInformation( PwmError.ERROR_FIELD_REQUIRED, null, new String[] {
                    getLabel( locale ),
            } );
            throw new PwmDataValidationException( error );
        }

        switch ( type )
        {
            case number:
                if ( value != null && value.length() > 0 )
                {
                    try
                    {
                        new BigInteger( value );
                    }
                    catch ( final NumberFormatException e )
                    {
                        final ErrorInformation error = new ErrorInformation( PwmError.ERROR_FIELD_NOT_A_NUMBER, null, new String[] {
                                getLabel( locale ),
                        } );
                        throw new PwmDataValidationException( error );
                    }
                }
                break;


            case email:
                if ( value != null && value.length() > 0 )
                {
                    if ( !testEmailAddress( config, value ) )
                    {
                        final ErrorInformation error = new ErrorInformation(
                                PwmError.ERROR_FIELD_INVALID_EMAIL,
                                null,
                                new String[]
                                        {
                                                getLabel( locale ),
                                        }
                        );
                        throw new PwmDataValidationException( error );
                    }
                }
                break;

            default:
                // continue for other types
                break;
        }

        if ( type != Type.photo )
        {
            if ( value != null && ( this.getMinimumLength() > 0 ) && ( value.length() > 0 ) && ( value.length() < this.getMinimumLength() ) )
            {
                final ErrorInformation error = new ErrorInformation( PwmError.ERROR_FIELD_TOO_SHORT, null, new String[] {
                        getLabel( locale ),
                } );
                throw new PwmDataValidationException( error );
            }
        }

        if ( type != Type.photo )
        {
            if ( value != null && value.length() > this.getMaximumLength() )
            {
                final ErrorInformation error = new ErrorInformation( PwmError.ERROR_FIELD_TOO_LONG, null, new String[] {
                        getLabel( locale ),
                } );
                throw new PwmDataValidationException( error );
            }
        }

        if ( type != Type.photo )
        {
            if ( value != null && value.length() > 0 && this.getRegex() != null && this.getRegex().length() > 0 )
            {
                if ( !value.matches( this.getRegex() ) )
                {
                    final String configuredErrorMessage = this.getRegexError( locale );
                    final ErrorInformation error = new ErrorInformation(
                            PwmError.ERROR_FIELD_REGEX_NOMATCH,
                            null,
                            configuredErrorMessage,
                            new String[]
                                    {
                                            getLabel( locale ),
                                    }
                    );
                    throw new PwmDataValidationException( error );
                }
            }
        }
    }

    public static List<String> convertToListOfNames( final Collection<FormConfiguration> formConfigurations )
    {
        if ( formConfigurations == null )
        {
            return Collections.emptyList();
        }
        final ArrayList<String> returnList = new ArrayList<>();
        for ( final FormConfiguration formConfiguration : formConfigurations )
        {
            returnList.add( formConfiguration.getName() );
        }
        return returnList;
    }

    /**
     * Return false if an invalid email address is issued.
     *
     * @param config application configuration
     * @param address the email address value to test.
     *
     * @return true if the email address is valid.
     */
    public static boolean testEmailAddress( final Configuration config, final String address )
    {
        final String patternStr;
        if ( config != null )
        {
            patternStr = config.readAppProperty( AppProperty.FORM_EMAIL_REGEX );
        }
        else
        {
            patternStr = AppProperty.FORM_EMAIL_REGEX.getDefaultValue();
        }

        final Pattern pattern = Pattern.compile( patternStr );
        final Matcher matcher = pattern.matcher( address );
        return matcher.matches();
    }

    public String displayValue( final String value, final Locale locale, final Configuration config )
    {
        if ( value == null )
        {
            return LocaleHelper.getLocalizedMessage( locale, Display.Value_NotApplicable, config );
        }

        if ( this.getType() == Type.select )
        {
            if ( this.getSelectOptions() != null )
            {
                for ( final Map.Entry<String, String> entry : selectOptions.entrySet() )
                {
                    final String key = entry.getKey();
                    if ( value.equals( key ) )
                    {
                        final String displayValue = entry.getValue();
                        if ( !StringUtil.isEmpty( displayValue ) )
                        {
                            return displayValue;
                        }
                    }
                }
            }
        }

        return value;
    }
}
