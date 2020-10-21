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

package password.pwm.config.value;

import password.pwm.PwmConstants;
import password.pwm.bean.EmailItemBean;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.config.value.data.NamedSecretData;
import password.pwm.config.value.data.RemoteWebServiceConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.util.PasswordData;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ValueTypeConverter
{
    private static final PwmLogger LOGGER = PwmLogger.forClass(  ValueTypeConverter.class );

    private ValueTypeConverter()
    {
    }

    public static long valueToLong( final StoredValue value )
    {
        if ( !( value instanceof NumericValue ) )
        {
            throw new IllegalArgumentException( "setting value is not readable as number" );
        }
        return ( long ) value.toNativeObject();
    }

    public static List<Long> valueToLongArray( final StoredValue value )
    {
        if ( !( value instanceof NumericArrayValue ) )
        {
            throw new IllegalArgumentException( "setting value is not readable as number array" );
        }
        return ( List<Long> ) value.toNativeObject();
    }

    public static String valueToString( final StoredValue value )
    {
        if ( value == null )
        {
            return null;
        }
        if ( ( !( value instanceof StringValue ) ) && ( !( value instanceof BooleanValue ) ) )
        {
            throw new IllegalArgumentException( "setting value is not readable as string" );
        }
        final Object nativeObject = value.toNativeObject();
        if ( nativeObject == null )
        {
            return null;
        }
        return nativeObject.toString();
    }

    public static PasswordData valueToPassword( final StoredValue value )
    {
        if ( value == null )
        {
            return null;
        }
        if ( ( !( value instanceof PasswordValue ) ) )
        {
            throw new IllegalArgumentException( "setting value is not readable as password" );
        }
        final Object nativeObject = value.toNativeObject();
        if ( nativeObject == null )
        {
            return null;
        }
        return ( PasswordData ) nativeObject;
    }

    public static Map<String, NamedSecretData> valueToNamedPassword( final StoredValue value )
    {
        if ( value == null )
        {
            return null;
        }
        if ( ( !( value instanceof NamedSecretValue ) ) )
        {
            throw new IllegalArgumentException( "setting value is not readable as named password" );
        }
        final Object nativeObject = value.toNativeObject();
        if ( nativeObject == null )
        {
            return null;
        }
        return ( Map<String, NamedSecretData> ) nativeObject;
    }

    public static List<RemoteWebServiceConfiguration> valueToRemoteWebServiceConfiguration( final StoredValue value )
    {
        if ( value == null )
        {
            return null;
        }
        if ( ( !( value instanceof RemoteWebServiceValue ) ) )
        {
            throw new IllegalArgumentException( "setting value is not readable as named password" );
        }
        final Object nativeObject = value.toNativeObject();
        if ( nativeObject == null )
        {
            return null;
        }
        return ( List<RemoteWebServiceConfiguration> ) nativeObject;
    }

    public static List<ActionConfiguration> valueToAction( final PwmSetting setting, final StoredValue storedValue )
    {
        if ( PwmSettingSyntax.ACTION != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read ACTION value for setting: " + setting.toString() );
        }

        return ( List<ActionConfiguration> ) storedValue.toNativeObject();
    }

    public static List<X509Certificate> valueToX509Certificates( final PwmSetting setting, final StoredValue storedValue  )
    {
        if ( PwmSettingSyntax.X509CERT != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read X509CERT value for setting: " + setting.toString() );
        }

        return ( (X509CertificateValue) storedValue ).asX509Certificates();
    }

    public static List<FormConfiguration> valueToForm( final StoredValue value )
    {
        if ( value == null )
        {
            return null;
        }

        if ( value instanceof CustomLinkValue )
        {
            return ( List<FormConfiguration> ) value.toNativeObject();
        }

        if ( ( !( value instanceof FormValue ) ) )
        {
            throw new IllegalArgumentException( "setting value is not readable as form" );
        }

        return ( List<FormConfiguration> ) value.toNativeObject();
    }

    public static List<String> valueToStringArray( final StoredValue value )
    {
        if ( !( value instanceof StringArrayValue ) )
        {
            throw new IllegalArgumentException( "setting value is not readable as string array" );
        }

        final List<String> results = new ArrayList<>( ( List<String> ) value.toNativeObject() );
        for ( final Iterator iter = results.iterator(); iter.hasNext(); )
        {
            final Object loopString = iter.next();
            if ( loopString == null || loopString.toString().length() < 1 )
            {
                iter.remove();
            }
        }
        return results;
    }

    public static List<UserPermission> valueToUserPermissions( final StoredValue value )
    {
        if ( value == null )
        {
            return Collections.emptyList();
        }

        if ( !( value instanceof UserPermissionValue ) )
        {
            throw new IllegalArgumentException( "setting value is not readable as string array" );
        }

        final List<UserPermission> results = new ArrayList<>( ( List<UserPermission> ) value.toNativeObject() );
        for ( final Iterator iter = results.iterator(); iter.hasNext(); )
        {
            final Object loopString = iter.next();
            if ( loopString == null || loopString.toString().length() < 1 )
            {
                iter.remove();
            }
        }
        return results;
    }

    public static boolean valueToBoolean( final StoredValue value )
    {
        if ( !( value instanceof BooleanValue ) )
        {
            throw new IllegalArgumentException( "may not read BOOLEAN value for setting" );
        }

        return ( Boolean ) value.toNativeObject();
    }

    public static String valueToLocalizedString( final StoredValue value, final Locale locale )
    {
        if ( !( value instanceof LocalizedStringValue ) )
        {
            throw new IllegalArgumentException( "may not read LOCALIZED_STRING or LOCALIZED_TEXT_AREA values for setting" );
        }

        final Map<String, String> availableValues = ( Map<String, String> ) value.toNativeObject();
        final Map<Locale, String> availableLocaleMap = new LinkedHashMap<>();
        for ( final Map.Entry<String, String> entry : availableValues.entrySet() )
        {
            final String localeStr = entry.getKey();
            availableLocaleMap.put( LocaleHelper.parseLocaleString( localeStr ), entry.getValue() );
        }
        final Locale matchedLocale = LocaleHelper.localeResolver( locale, availableLocaleMap.keySet() );

        return availableLocaleMap.get( matchedLocale );
    }

    public static List<String> valueToLocalizedStringArray( final StoredValue value, final Locale locale )
    {
        if ( !( value instanceof LocalizedStringArrayValue ) )
        {
            throw new IllegalArgumentException( "may not read LOCALIZED_STRING_ARRAY value" );
        }
        final Map<String, List<String>> storedValues = ( Map<String, List<String>> ) value.toNativeObject();
        final Map<Locale, List<String>> availableLocaleMap = new LinkedHashMap<>();
        for ( final Map.Entry<String, List<String>> entry : storedValues.entrySet() )
        {
            final String localeStr = entry.getKey();
            availableLocaleMap.put( LocaleHelper.parseLocaleString( localeStr ), entry.getValue() );
        }
        final Locale matchedLocale = LocaleHelper.localeResolver( locale, availableLocaleMap.keySet() );

        return availableLocaleMap.get( matchedLocale );
    }

    public static <E extends Enum<E>> E valueToEnum( final PwmSetting setting, final StoredValue value, final Class<E> enumClass )
    {
        if ( PwmSettingSyntax.SELECT != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read SELECT enum value for setting: " + setting.toString() );
        }

        final String strValue = ( String ) value.toNativeObject();
        return JavaHelper.readEnumFromString( enumClass, strValue ).orElse( null );
    }

    public static Map<Locale, EmailItemBean> valueToLocalizedEmail( final PwmSetting setting, final StoredValue storedValue )
    {
        if ( PwmSettingSyntax.EMAIL != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read EMAIL value for setting: " + setting.toString() );
        }

        final Map<String, EmailItemBean> storedValues =  ( Map<String, EmailItemBean> ) storedValue.toNativeObject();
        final Map<Locale, EmailItemBean> availableLocaleMap = new LinkedHashMap<>();
        for ( final Map.Entry<String, EmailItemBean> entry : storedValues.entrySet() )
        {
            final String localeStr = entry.getKey();
            availableLocaleMap.put( LocaleHelper.parseLocaleString( localeStr ), entry.getValue() );
        }

        return Collections.unmodifiableMap( availableLocaleMap );
    }

    public static Map<FileValue.FileInformation, FileValue.FileContent> valueToFile( final PwmSetting setting, final StoredValue storedValue )
    {
        if ( PwmSettingSyntax.FILE != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read file value for setting: " + setting.toString() );
        }

        if ( !( storedValue instanceof FileValue ) )
        {
            throw new IllegalArgumentException( "unexpected value type '" + storedValue.getClass().getName() + " ' is not expected file value" );
        }

        return ( Map ) storedValue.toNativeObject();
    }

    public static <E extends Enum<E>> Set<E> valueToOptionList( final PwmSetting setting, final StoredValue value, final Class<E> enumClass )
    {
        if ( PwmSettingSyntax.OPTIONLIST != setting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read optionlist value for setting: " + setting.toString() );
        }

        final Set<String> strValues = ( Set<String> ) value.toNativeObject();
        return JavaHelper.readEnumSetFromStringCollection( enumClass, strValues );
    }

    public static List<String> valueToProfileID( final PwmSetting profileSetting, final StoredValue storedValue )
    {
        if ( PwmSettingSyntax.PROFILE != profileSetting.getSyntax() )
        {
            throw new IllegalArgumentException( "may not read profile value for setting: " + profileSetting.toString() );
        }

        final List<String> profiles = ValueTypeConverter.valueToStringArray( storedValue );

        final List<String> returnSet = profiles
                .stream()
                .distinct()
                .filter( ( profile ) -> !StringUtil.isEmpty( profile ) )
                .collect( Collectors.toCollection( ArrayList::new ) );

        if ( returnSet.isEmpty() )
        {
            returnSet.add( PwmConstants.PROFILE_ID_DEFAULT );
        }

        return Collections.unmodifiableList( returnSet );
    }
}
