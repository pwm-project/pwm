/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2023 The PWM Project
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

package password.pwm.util.i18n;

import password.pwm.PwmConstants;
import password.pwm.util.java.JavaHelper;

import java.io.Serializable;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

public class LocaleComparators
{
    private static final Comparator<Locale> LOCALE_COMPARATOR
            = new LocaleComparator( PwmConstants.DEFAULT_LOCALE );
    private static final Comparator<Locale> LOCALE_COMPARATOR_DEFAULT_FIRST
            = new LocaleComparator( PwmConstants.DEFAULT_LOCALE, Flag.DefaultFirst );
    private static final Comparator<String> STR_COMPARATOR
            = new StringLocaleComparator( LOCALE_COMPARATOR );
    private static final Comparator<String> STR_COMPARATOR_DEFAULT_FIRST
            = new StringLocaleComparator( LOCALE_COMPARATOR_DEFAULT_FIRST );

    // string with high ascii sort value
    private static final String FIRST_POSITION_PSEUDO_VALUE = "!!!";

    public enum Flag
    {
        /** Always sort default locale to first position. */
        DefaultFirst
    }

    public static Comparator<Locale> localeComparator( final Flag... flag )
    {
        return localeComparator( PwmConstants.DEFAULT_LOCALE, flag );
    }

    public static Comparator<Locale> localeComparator( final Locale comparisonLocale, final Flag... flag )
    {
        if ( Objects.equals( comparisonLocale, PwmConstants.DEFAULT_LOCALE ) )
        {
            if ( JavaHelper.enumArrayContainsValue( flag, Flag.DefaultFirst ) )
            {
                return LOCALE_COMPARATOR_DEFAULT_FIRST;
            }
            else
            {
                return LOCALE_COMPARATOR;
            }
        }
        return new LocaleComparator( comparisonLocale, flag );
    }

    public static Comparator<String> stringLocaleComparator( final Flag... flag )
    {
        return stringLocaleComparator( PwmConstants.DEFAULT_LOCALE, flag );
    }

    public static Comparator<String> stringLocaleComparator( final Locale comparisonLocale, final Flag... flag )
    {
        if ( Objects.equals( comparisonLocale, PwmConstants.DEFAULT_LOCALE ) )
        {
            if ( JavaHelper.enumArrayContainsValue( flag, Flag.DefaultFirst ) )
            {
                return STR_COMPARATOR_DEFAULT_FIRST;
            }
            else
            {
                return STR_COMPARATOR;
            }
        }
        return new StringLocaleComparator( new LocaleComparator( comparisonLocale, flag ) );
    }

    private static class LocaleComparator implements Comparator<Locale>, Serializable
    {
        private final boolean defaultFirst;
        private final Locale comparisonLocale;

        LocaleComparator( final Locale comparisonLocale, final Flag... flag )
        {
            this.defaultFirst = JavaHelper.enumArrayContainsValue( flag, Flag.DefaultFirst );
            this.comparisonLocale = comparisonLocale;
        }

        @Override
        public int compare( final Locale o1, final Locale o2 )
        {
            final String name1 = defaultFirst && Objects.equals( o1, PwmConstants.DEFAULT_LOCALE )
                    ? FIRST_POSITION_PSEUDO_VALUE
                    : o1.getDisplayName( comparisonLocale );

            final String name2 = defaultFirst && Objects.equals( o2, PwmConstants.DEFAULT_LOCALE )
                    ? FIRST_POSITION_PSEUDO_VALUE
                    : o2.getDisplayName( comparisonLocale );

            return name1.compareToIgnoreCase( name2 );
        }
    }

    private static class StringLocaleComparator implements Comparator<String>, Serializable
    {
        private final Comparator<Locale> localeComparator;

        StringLocaleComparator( final Comparator<Locale> localeComparator )
        {
            this.localeComparator = localeComparator;
        }

        @Override
        public int compare( final String o1, final String o2 )
        {
            final Locale locale1 = LocaleHelper.parseLocaleString( o1 );
            final Locale locale2 = LocaleHelper.parseLocaleString( o2 );
            return localeComparator.compare( locale1, locale2 );
        }
    }
}
