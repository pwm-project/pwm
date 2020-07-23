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

package password.pwm.util.i18n;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.pub.SessionStateInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingTemplateSet;
import password.pwm.config.value.StringArrayValue;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Display;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LocaleHelper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LocaleHelper.class );

    public enum TextDirection
    {
        rtl,
        ltr,
    }

    public static Class classForShortName( final String shortName )
    {
        if ( shortName == null || shortName.isEmpty() )
        {
            return null;
        }
        final String className = PwmLocaleBundle.class.getPackage().getName() + "." + shortName;
        try
        {
            return Class.forName( className );
        }
        catch ( final ClassNotFoundException e )
        {
            return null;
        }
    }

    public static String getLocalizedMessage( final Locale locale, final PwmDisplayBundle key, final Configuration config )
    {
        return getLocalizedMessage( locale, key.getKey(), config, key.getClass() );
    }

    public static String getLocalizedMessage( final Locale locale, final PwmDisplayBundle key, final Configuration config, final String[] args )
    {
        return getLocalizedMessage( locale, key.getKey(), config, key.getClass(), args );
    }

    public static String getLocalizedMessage( final PwmDisplayBundle key, final PwmRequest pwmRequest, final String... values )
    {
        return getLocalizedMessage(
                pwmRequest == null ? PwmConstants.DEFAULT_LOCALE : pwmRequest.getLocale(),
                key.getKey(),
                pwmRequest == null ? null : pwmRequest.getConfig(),
                key.getClass(),
                values
        );
    }

    public static String getLocalizedMessage( final String key, final Configuration config, final Class bundleClass )
    {
        return getLocalizedMessage( PwmConstants.DEFAULT_LOCALE, key, config, bundleClass );
    }

    public static String getLocalizedMessage( final Locale locale, final String key, final Configuration config, final Class bundleClass )
    {
        return getLocalizedMessage( locale, key, config, bundleClass, null );
    }

    public static String getLocalizedMessage(
            final Locale locale,
            final String key,
            final Configuration config,
            final Class bundleClass,
            final String[] values
    )
    {
        String returnValue = null;
        if ( config != null )
        {
            final PwmLocaleBundle pwmLocaleBundle = PwmLocaleBundle.forKey( bundleClass.getName() )
                    .orElseThrow( () -> new IllegalStateException( "unknown locale bundle name '" + bundleClass.getName() + "'" ) );
            final Map<Locale, String> configuredBundle = config.readLocalizedBundle( pwmLocaleBundle, key );
            if ( configuredBundle != null )
            {
                final Locale resolvedLocale = localeResolver( locale, configuredBundle.keySet() );
                returnValue = configuredBundle.get( resolvedLocale );
            }
        }

        if ( returnValue == null || returnValue.isEmpty() )
        {
            final ResourceBundle bundle = getMessageBundle( locale, bundleClass );
            if ( bundle == null )
            {
                final String errorMsg = "missing bundle for " + bundleClass.getName();
                LOGGER.warn( () -> errorMsg );
                return errorMsg;
            }
            try
            {
                returnValue = bundle.getString( key );
            }
            catch ( final MissingResourceException e )
            {
                final String errorMsg = "missing key '" + key + "' for " + bundleClass.getName();
                if ( config != null && config.isDevDebugMode() )
                {
                    LOGGER.warn( () -> errorMsg );
                }
                returnValue = key;
            }
        }

        if ( values != null )
        {
            for ( int i = 0; i < values.length; i++ )
            {
                if ( values[ i ] != null )
                {
                    final String replaceKey = "%" + ( i + 1 ) + "%";
                    returnValue = returnValue.replace( replaceKey, values[ i ] );
                }
            }
        }

        final MacroMachine macroMachine = MacroMachine.forStatic();
        return macroMachine.expandMacros( returnValue );
    }

    private static ResourceBundle getMessageBundle( final Locale locale, final Class bundleClass )
    {
        if ( !PwmDisplayBundle.class.isAssignableFrom( bundleClass ) )
        {
            LOGGER.warn( () -> "attempt to resolve locale for non-DisplayBundleMarker class type " + bundleClass.toString() );
            return null;
        }

        final ResourceBundle messagesBundle;
        if ( locale == null )
        {
            messagesBundle = ResourceBundle.getBundle( bundleClass.getName() );
        }
        else
        {
            messagesBundle = ResourceBundle.getBundle( bundleClass.getName(), locale );
        }

        return messagesBundle;
    }

    public static Locale parseLocaleString( final String localeString )
    {
        if ( localeString == null )
        {
            return PwmConstants.DEFAULT_LOCALE;
        }

        final StringTokenizer st = new StringTokenizer( localeString, "_" );

        if ( !st.hasMoreTokens() )
        {
            return PwmConstants.DEFAULT_LOCALE;
        }

        final String language = st.nextToken();
        if ( !st.hasMoreTokens() )
        {
            return new Locale( language );
        }

        final String country = st.nextToken();
        if ( !st.hasMoreTokens() )
        {
            return new Locale( language, country );
        }

        final String variant = st.nextToken( "" );
        return new Locale( language, country, variant );
    }

    public static Locale localeResolver( final Locale desiredLocale, final Collection<Locale> localePool )
    {
        if ( desiredLocale == null || localePool == null || localePool.isEmpty() )
        {
            return PwmConstants.DEFAULT_LOCALE;
        }

        for ( final Locale loopLocale : localePool )
        {
            if ( loopLocale.getLanguage().equalsIgnoreCase( desiredLocale.getLanguage() ) )
            {
                if ( loopLocale.getCountry().equalsIgnoreCase( desiredLocale.getCountry() ) )
                {
                    if ( loopLocale.getVariant().equalsIgnoreCase( desiredLocale.getVariant() ) )
                    {
                        return loopLocale;
                    }
                }
            }
        }

        for ( final Locale loopLocale : localePool )
        {
            if ( loopLocale.getLanguage().equalsIgnoreCase( desiredLocale.getLanguage() ) )
            {
                if ( loopLocale.getCountry().equalsIgnoreCase( desiredLocale.getCountry() ) )
                {
                    return loopLocale;
                }
            }
        }

        for ( final Locale loopLocale : localePool )
        {
            if ( loopLocale.getLanguage().equalsIgnoreCase( desiredLocale.getLanguage() ) )
            {
                return loopLocale;
            }
        }

        if ( localePool.contains( PwmConstants.DEFAULT_LOCALE ) )
        {
            return PwmConstants.DEFAULT_LOCALE;
        }

        if ( localePool.contains( new Locale( "" ) ) )
        {
            return PwmConstants.DEFAULT_LOCALE;
        }

        return PwmConstants.DEFAULT_LOCALE;
    }

    public static String resolveStringKeyLocaleMap( final Locale desiredLocale, final Map<String, String> inputMap )
    {
        if ( inputMap == null || inputMap.isEmpty() )
        {
            return null;
        }

        final Locale locale = ( desiredLocale == null )
                ? PwmConstants.DEFAULT_LOCALE
                : desiredLocale;

        final Map<Locale, String> localeMap = new LinkedHashMap<>();
        for ( final Map.Entry<String, String> entry : inputMap.entrySet() )
        {
            final String localeStringKey = entry.getKey();
            localeMap.put( parseLocaleString( localeStringKey ), entry.getValue() );
        }

        final Locale selectedLocale = localeResolver( locale, localeMap.keySet() );
        return localeMap.get( selectedLocale );
    }

    public static class DisplayMaker
    {
        private final PwmApplication pwmApplication;
        private final Class<? extends PwmDisplayBundle> bundleClass;
        private final Locale locale;

        public DisplayMaker(
                final Locale locale,
                final Class<? extends PwmDisplayBundle> bundleClass,
                final PwmApplication pwmApplication
        )
        {
            this.locale = locale;
            this.bundleClass = bundleClass;
            this.pwmApplication = pwmApplication;
        }

        public String forKey( final String input, final String... values )
        {
            return LocaleHelper.getLocalizedMessage( locale, input, pwmApplication.getConfig(), bundleClass, values );
        }
    }

    public static Map<Locale, String> getUniqueLocalizations(
            final Configuration configuration,
            final Class<? extends PwmDisplayBundle> bundleClass,
            final String key,
            final Locale defaultLocale
    )
    {
        final Map<Locale, String> returnObj = new LinkedHashMap<>();
        final Collection<Locale> localeList = configuration == null
                ? new ArrayList<>( PwmConstants.INCLUDED_LOCALES )
                : new ArrayList<>( configuration.getKnownLocales() );

        final String defaultValue = getLocalizedMessage( defaultLocale, key, configuration, bundleClass );
        returnObj.put( defaultLocale, defaultValue );

        for ( final Locale loopLocale : localeList )
        {
            final String localizedValue = ResourceBundle.getBundle( bundleClass.getName(), loopLocale ).getString( key );
            if ( !defaultValue.equals( localizedValue ) )
            {
                returnObj.put( loopLocale, localizedValue );
            }
        }

        return Collections.unmodifiableMap( returnObj );
    }

    public static String debugLabel( final Locale locale )
    {
        if ( locale == null || PwmConstants.DEFAULT_LOCALE.equals( locale ) )
        {
            return "default";
        }
        return locale.toLanguageTag();
    }

    public static String booleanString( final boolean input, final PwmRequest pwmRequest )
    {
        final Display key = input ? Display.Value_True : Display.Value_False;

        return pwmRequest == null
                ? Display.getLocalizedMessage( null, key, null )
                : Display.getLocalizedMessage( pwmRequest.getLocale(), key, pwmRequest.getConfig() );
    }

    public static String booleanString( final boolean input, final Locale locale, final Configuration configuration )
    {
        final Display key = input ? Display.Value_True : Display.Value_False;
        return Display.getLocalizedMessage( locale, key, configuration );
    }

    public static String instantString ( final Instant input, final Locale locale, final Configuration configuration )
    {
        if ( input == null )
        {
            return LocaleHelper.getLocalizedMessage( locale, Display.Value_NotApplicable, configuration );
        }
        return JavaHelper.toIsoDate( input );
    }

    public static Map<PwmLocaleBundle, Map<String, List<Locale>>> getModifiedKeysInConfig( final Configuration configuration )
    {
        final Map<PwmLocaleBundle, Map<String, List<Locale>>> returnObj = new LinkedHashMap<>();
        for ( final PwmLocaleBundle pwmLocaleBundle : PwmLocaleBundle.values() )
        {
            for ( final String key : pwmLocaleBundle.getDisplayKeys() )
            {
                for ( final Locale locale : configuration.getKnownLocales() )
                {
                    final String defaultValue = LocaleHelper.getLocalizedMessage( locale, key, null, pwmLocaleBundle.getTheClass() );
                    final String customizedValue = LocaleHelper.getLocalizedMessage( locale, key, configuration, pwmLocaleBundle.getTheClass() );
                    if ( defaultValue != null && !defaultValue.equals( customizedValue ) )
                    {
                        if ( !returnObj.containsKey( pwmLocaleBundle ) )
                        {
                            returnObj.put( pwmLocaleBundle, new LinkedHashMap<>() );
                        }
                        if ( !returnObj.get( pwmLocaleBundle ).containsKey( key ) )
                        {
                            returnObj.get( pwmLocaleBundle ).put( key, new ArrayList<>() );
                        }

                        returnObj.get( pwmLocaleBundle ).get( key ).add( locale );
                    }
                }
            }
        }
        return returnObj;
    }

    public static Locale getLocaleForSessionID( final PwmApplication pwmApplication, final String sessionID )
    {
        if ( pwmApplication != null && !StringUtil.isEmpty( sessionID ) )
        {
            final Iterator<SessionStateInfoBean> sessionInfoIterator = pwmApplication.getSessionTrackService().getSessionInfoIterator();
            while ( sessionInfoIterator.hasNext() )
            {
                final SessionStateInfoBean sessionStateInfoBean = sessionInfoIterator.next();
                if ( StringUtil.nullSafeEquals( sessionStateInfoBean.getLabel(), sessionID ) )
                {
                    if ( sessionStateInfoBean.getLocale() != null )
                    {
                        return sessionStateInfoBean.getLocale();
                    }
                }
            }
        }

        return PwmConstants.DEFAULT_LOCALE;
    }

    public static String getBrowserLocaleString( final Locale locale )
    {
        return locale == null
                ? ""
                : locale.toString().replace( "_", "-" );
    }

    public static Comparator<Locale> localeComparator( final Locale comparisonLocale )
    {
        return ( o1, o2 ) ->
        {
            final String name1 = o1.getDisplayName( comparisonLocale );
            final String name2 = o2.getDisplayName( comparisonLocale );
            return name1.compareToIgnoreCase( name2 );
        };
    }

    public static List<Locale> highLightedLocales()
    {
        final List<String> strValues = PwmConstants.HIGHLIGHT_LOCALES;
        final List<Locale> returnList = strValues.stream().map( LocaleHelper::parseLocaleString ).collect( Collectors.toList() );
        return Collections.unmodifiableList( returnList );

    }

    static List<Locale> knownBuiltInLocales( )
    {
        final List<Locale> knownLocales = new ArrayList<>();

        final StringArrayValue stringArrayValue = ( StringArrayValue ) PwmSetting.KNOWN_LOCALES.getDefaultValue( PwmSettingTemplateSet.getDefault() );
        final List<String> rawValues = stringArrayValue.toNativeObject();
        final Map<String, String> localeFlagMap = StringUtil.convertStringListToNameValuePair( rawValues, "::" );
        for ( final String rawValue : localeFlagMap.keySet() )
        {
            knownLocales.add( LocaleHelper.parseLocaleString( rawValue ) );
        }

        final Map<String, Locale> returnMap = new TreeMap<>();

        for ( final Locale locale : knownLocales )
        {
            returnMap.put( locale.getDisplayName(), locale );
        }
        return new ArrayList<>( returnMap.values() );
    }

    public static String valueBoolean( final Locale locale, final boolean value )
    {
        final PwmDisplayBundle key = value ? Display.Value_True : Display.Value_False;
        return getLocalizedMessage( locale, key, null );
    }

    public static String valueNotApplicable( final Locale locale )
    {
        return getLocalizedMessage( locale, Display.Value_NotApplicable, null );
    }

    public static TextDirection textDirectionForLocale( final PwmApplication pwmApplication, final Locale locale )
    {
        final String rtlRegex = pwmApplication.getConfig().readAppProperty( AppProperty.L10N_RTL_REGEX );
        final Pattern rtlPattern = Pattern.compile( rtlRegex );
        final String languageString = locale.getLanguage();
        return languageString != null && rtlPattern.matcher( languageString ).find()
                ? TextDirection.rtl
                : TextDirection.ltr;
    }

    public static Map<String, String> localeMapToStringMap( final Map<Locale, String> localeStringMap )
    {
        final Map<String, String> returnMap = new LinkedHashMap<>();
        for ( final Map.Entry<Locale, String> entry : localeStringMap.entrySet() )
        {
            returnMap.put( LocaleHelper.getBrowserLocaleString( entry.getKey() ), entry.getValue() );
        }
        return Collections.unmodifiableMap( returnMap );
    }
}
