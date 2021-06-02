/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.config.stored;

import org.jetbrains.annotations.NotNull;
import password.pwm.PwmConstants;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.i18n.Config;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class StoredConfigItemKey implements Serializable, Comparable<StoredConfigItemKey>
{
    public enum RecordType
    {
        SETTING( "Setting" ),
        LOCALE_BUNDLE ( "Localization" ),
        PROPERTY ( "Property" ),;

        private final String label;

        RecordType( final String label )
        {
            this.label = label;
        }

        public String getLabel()
        {
            return label;
        }
    }

    private final RecordType recordType;
    private final String recordID;
    private final String profileID;

    private static final long serialVersionUID = 1L;

    private static final Comparator<StoredConfigItemKey> COMPARATOR = makeComparator();

    private StoredConfigItemKey( final RecordType recordType, final String recordID, final String profileID )
    {
        Objects.requireNonNull( recordType, "recordType can not be null" );
        Objects.requireNonNull( recordID, "recordID can not be null" );

        this.recordType = recordType;
        this.recordID = recordID;
        this.profileID = profileID;
    }

    public RecordType getRecordType()
    {
        return recordType;
    }


    public String getRecordID()
    {
        return recordID;
    }

    public String getProfileID()
    {
        return profileID;
    }

    static StoredConfigItemKey fromSetting( final PwmSetting pwmSetting, final String profileID )
    {
        return new StoredConfigItemKey( RecordType.SETTING, pwmSetting.getKey(), profileID );
    }

    static StoredConfigItemKey fromLocaleBundle( final PwmLocaleBundle localeBundle, final String key )
    {
        return new StoredConfigItemKey( RecordType.LOCALE_BUNDLE, localeBundle.getKey(), key );
    }

    static StoredConfigItemKey fromConfigurationProperty( final ConfigurationProperty configurationProperty )
    {
        return new StoredConfigItemKey( RecordType.PROPERTY, configurationProperty.getKey(), null );
    }

    public boolean isRecordType( final RecordType recordType )
    {
        return recordType != null && Objects.equals( getRecordType(), recordType );
    }

    public boolean isValid()
    {
        try
        {
            validate();
            return true;
        }
        catch ( final IllegalStateException e )
        {
            /* ignore */
        }
        return false;
    }

    public void validate()
    {
        switch ( recordType )
        {
            case SETTING:
            {
                final PwmSetting pwmSetting = this.toPwmSetting();
                final boolean hasProfileID = !StringUtil.isEmpty( profileID );
                if ( pwmSetting.getCategory().hasProfiles() && !hasProfileID )
                {
                    throw new IllegalStateException( "profileID is required for setting " + pwmSetting.getKey() );
                }
                else if ( !pwmSetting.getCategory().hasProfiles() && hasProfileID )
                {
                    throw new IllegalStateException( "profileID is not required for setting " + pwmSetting.getKey() );
                }
            }
            break;

            case LOCALE_BUNDLE:
            {
                Objects.requireNonNull( profileID, "profileID is required when recordType is LOCALE_BUNDLE" );
                final PwmLocaleBundle pwmLocaleBundle = toLocaleBundle();
                if ( !pwmLocaleBundle.getDisplayKeys().contains( profileID ) )
                {
                    throw new IllegalStateException( "key '" + profileID + "' is unrecognized for locale bundle " + pwmLocaleBundle.name() );
                }
            }
            break;

            case PROPERTY:
                break;

            default:
                JavaHelper.unhandledSwitchStatement( recordType );
        }
    }

    public String getLabel( final Locale locale )
    {
        final String separator = LocaleHelper.getLocalizedMessage( locale, Config.Display_SettingNavigationSeparator, null );

        switch ( recordType )
        {
            case SETTING:
                return recordType.getLabel() + separator + toPwmSetting().toMenuLocationDebug( profileID, locale );

            case PROPERTY:
                return recordType.getLabel() + separator + this.getRecordID();

            case LOCALE_BUNDLE:
                return recordType.getLabel()
                        + separator
                        + this.getRecordID()
                        + separator
                        + this.getProfileID();

            default:
                JavaHelper.unhandledSwitchStatement( recordType );
        }

        throw new IllegalStateException(  );
    }

    public PwmSetting toPwmSetting()
    {
        if ( getRecordType() != RecordType.SETTING )
        {
            throw new IllegalStateException( "attempt to read pwmSetting key for non-setting ConfigItemKey" );
        }

        return PwmSetting.forKey( this.recordID ).orElseThrow( () -> new IllegalStateException(
                "attempt to read ConfigItemKey with unknown setting key '" + getRecordID() + "'" ) );
    }

    public PwmLocaleBundle toLocaleBundle()
    {
        if ( getRecordType() != RecordType.LOCALE_BUNDLE )
        {
            throw new IllegalStateException( "attempt to read PwmLocaleBundle key for non-locale ConfigItemKey" );
        }

        return PwmLocaleBundle.forKey( this.recordID )
                .orElseThrow( () -> new IllegalStateException( "unexpected key value for locale bundle: " + this.recordID ) );
    }

    public ConfigurationProperty toConfigurationProperty()
    {
        if ( getRecordType() != RecordType.PROPERTY )
        {
            throw new IllegalStateException( "attempt to read ConfigurationProperty key for non-config property ConfigItemKey" );
        }

        return ConfigurationProperty.valueOf( recordID );
    }

    @Override
    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }
        final StoredConfigItemKey that = ( StoredConfigItemKey ) o;
        return Objects.equals( recordType, that.recordType )
                && Objects.equals( recordID, that.recordID )
                && Objects.equals( profileID, that.profileID );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( recordType, recordID, profileID );
    }

    @Override
    public int compareTo( @NotNull final StoredConfigItemKey o )
    {
        return COMPARATOR.compare( this, o );
    }

    @Override
    public String toString()
    {
        return getLabel( PwmConstants.DEFAULT_LOCALE );
    }

    public PwmSettingSyntax getSyntax()
    {
        switch ( getRecordType() )
        {
            case SETTING:
                return toPwmSetting().getSyntax();

            case PROPERTY:
                return PwmSettingSyntax.STRING;

            case LOCALE_BUNDLE:
                return PwmSettingSyntax.LOCALIZED_STRING_ARRAY;

            default:
                JavaHelper.unhandledSwitchStatement( getRecordType() );
                throw new IllegalStateException();
        }
    }

    public static Set<StoredConfigItemKey> filterBySettingSyntax( final PwmSettingSyntax pwmSettingSyntax, final Set<StoredConfigItemKey> input )
    {
        return Collections.unmodifiableSet( filterByType( RecordType.SETTING, input )
                .stream()
                .filter( ( k ) -> k.toPwmSetting().getSyntax() == pwmSettingSyntax )
                .collect( Collectors.toSet() ) );
    }

    public static Set<StoredConfigItemKey> filterByType( final RecordType recordType, final Set<StoredConfigItemKey> input )
    {
        if ( JavaHelper.isEmpty( input ) )
        {
            return Collections.emptySet();
        }

        return input.stream().filter( ( k ) -> k.isRecordType( recordType ) ).collect( Collectors.toUnmodifiableSet() );
    }

    public static Comparator<? super StoredConfigItemKey> comparator()
    {
        return COMPARATOR;
    }

    private static Comparator<StoredConfigItemKey> makeComparator()
    {
        final Comparator<StoredConfigItemKey> typeComparator = Comparator.comparing(
                StoredConfigItemKey::getRecordType,
                Comparator.nullsLast( Comparator.naturalOrder() ) );

        final Comparator<StoredConfigItemKey> recordComparator = ( o1, o2 ) ->
        {
            if ( Objects.equals( o1.getRecordType(), o2.getRecordType() )
                    && o1.isRecordType( RecordType.SETTING ) )
            {
                final Comparator<PwmSetting> pwmSettingComparator = PwmSetting.menuLocationComparator( );
                return pwmSettingComparator.compare( o1.toPwmSetting(), o2.toPwmSetting() );
            }
            else
            {
                return o1.getRecordID().compareTo( o2.getRecordID() );
            }
        };

        final Comparator<StoredConfigItemKey> profileComparator = Comparator.comparing( StoredConfigItemKey::getProfileID,
                Comparator.nullsLast( Comparator.naturalOrder() ) );

        return typeComparator
                .thenComparing( recordComparator )
                .thenComparing( profileComparator );
    }


}
