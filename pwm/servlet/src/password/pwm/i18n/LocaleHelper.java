/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.i18n;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.util.logging.PwmLogger;

import java.util.*;

public class LocaleHelper {

    private static final PwmLogger LOGGER = PwmLogger.forClass(LocaleHelper.class);

    public static Class classForShortName(final String shortName) {
        if (shortName == null || shortName.isEmpty()) {
            return null;
        }
        final String className = LocaleHelper.class.getPackage().getName() + "." + shortName;
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static String getLocalizedMessage(final String key, final Configuration config, final Class bundleClass) {
        return getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,key,config,bundleClass);
    }

    public static String getLocalizedMessage(final Locale locale, final String key, final Configuration config, final Class bundleClass) {
        return getLocalizedMessage(locale,key,config,bundleClass,null);
    }

    public static String getLocalizedMessage(final Locale locale, final String key, final Configuration config, final Class bundleClass, final String[] values) {
        String returnValue = null;
        if (config != null) {
            final Map<Locale,String> configuredBundle = config.readLocalizedBundle(bundleClass.getName(),key);
            if (configuredBundle != null) {
                final Locale resolvedLocale = localeResolver(locale, configuredBundle.keySet());
                returnValue = configuredBundle.get(resolvedLocale);
            }
        }

        if (returnValue == null || returnValue.isEmpty()) {
            final ResourceBundle bundle = getMessageBundle(locale, bundleClass);
            if (bundle == null) {
                final String errorMsg = "missing bundle for " + bundleClass.getName();
                LOGGER.warn(errorMsg);
                return errorMsg;
            }
            final String rawValue = bundle.getString(key);
            if (rawValue == null) {
                final String errorMsg = "missing key '" + key + "' for " + bundleClass.getName();
                LOGGER.warn(errorMsg);
                return errorMsg;
            }
            returnValue = rawValue;
        }

        if (values != null) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    final String replaceKey = "%" + (i+1) + "%";
                    returnValue = returnValue.replace(replaceKey,values[i]);
                }
            }
        }
        return returnValue;
    }

    private static ResourceBundle getMessageBundle(final Locale locale, final Class bundleClass) {
        if (!DisplayBundleMarker.class.isAssignableFrom(bundleClass)) {
            LOGGER.warn("attempt to resolve locale for non-DisplayBundleMarker class type " + bundleClass.toString());
            return null;
        }

        final ResourceBundle messagesBundle;
        if (locale == null) {
            messagesBundle = ResourceBundle.getBundle(bundleClass.getName());
        } else {
            messagesBundle = ResourceBundle.getBundle(bundleClass.getName(), locale);
        }

        return messagesBundle;
    }

    public static Locale parseLocaleString(final String localeString) {
        if (localeString == null) {
            return PwmConstants.DEFAULT_LOCALE;
        }

        final StringTokenizer st = new StringTokenizer(localeString, "_");

        if (!st.hasMoreTokens()) {
            return PwmConstants.DEFAULT_LOCALE;
        }

        final String language = st.nextToken();
        if (!st.hasMoreTokens()) {
            return new Locale(language);
        }

        final String country = st.nextToken();
        if (!st.hasMoreTokens()) {
            return new Locale(language, country);
        }

        final String variant = st.nextToken("");
        return new Locale(language, country, variant);
    }

    public static Locale localeResolver(final Locale desiredLocale, final Collection<Locale> localePool) {
        if (desiredLocale == null || localePool == null || localePool.isEmpty()) {
            return null;
        }

        for (final Locale loopLocale : localePool) {
            if (loopLocale.getLanguage().equalsIgnoreCase(desiredLocale.getLanguage())) {
                if (loopLocale.getCountry().equalsIgnoreCase(desiredLocale.getCountry())) {
                    if (loopLocale.getVariant().equalsIgnoreCase(desiredLocale.getVariant())) {
                        return loopLocale;
                    }
                }
            }
        }

        for (final Locale loopLocale : localePool) {
            if (loopLocale.getLanguage().equalsIgnoreCase(desiredLocale.getLanguage())) {
                if (loopLocale.getCountry().equalsIgnoreCase(desiredLocale.getCountry())) {
                    return loopLocale;
                }
            }
        }

        for (final Locale loopLocale : localePool) {
            if (loopLocale.getLanguage().equalsIgnoreCase(desiredLocale.getLanguage())) {
                return loopLocale;
            }
        }

        if (localePool.contains(PwmConstants.DEFAULT_LOCALE)) {
            return PwmConstants.DEFAULT_LOCALE;
        }

        if (localePool.contains(new Locale(""))) {
            return new Locale("");
        }

        return null;
    }

    public static String resolveStringKeyLocaleMap(Locale desiredLocale, final Map<String,String> inputMap) {
        if (inputMap == null || inputMap.isEmpty()) {
            return null;
        }

        if (desiredLocale == null) {
            desiredLocale = PwmConstants.DEFAULT_LOCALE;
        }

        final Map<Locale,String> localeMap = new LinkedHashMap<>();
        for (final String localeStringKey : inputMap.keySet()) {
            localeMap.put(parseLocaleString(localeStringKey),inputMap.get(localeStringKey));
        }

        final Locale selectedLocale = localeResolver(desiredLocale, localeMap.keySet());
        return localeMap.get(selectedLocale);
    }

    public static class DisplayMaker {
        private final PwmApplication pwmApplication;
        private final Class<? extends DisplayBundleMarker> bundleClass;
        private final Locale locale;

        public DisplayMaker(
                Locale locale,
                Class<? extends DisplayBundleMarker> bundleClass,
                PwmApplication pwmApplication
        )
        {
            this.locale = locale;
            this.bundleClass = bundleClass;
            this.pwmApplication = pwmApplication;
        }

        public String forKey(String input, String... values) {
            return LocaleHelper.getLocalizedMessage(locale,input,pwmApplication.getConfig(),bundleClass,values);
        }
    }

    public static Map<Locale,String> getUniqueLocalizations(
            final PwmApplication pwmApplication,
            final Class<? extends DisplayBundleMarker> bundleClass,
            final String key,
            final Locale defaultLocale
    )
    {
        final Map<Locale, String> returnObj = new LinkedHashMap<>();
        final String defaultValue = getLocalizedMessage(defaultLocale, key, pwmApplication.getConfig(), bundleClass);
        returnObj.put(defaultLocale, defaultValue);

        for (final Locale loopLocale : pwmApplication.getConfig().getKnownLocales()) {
            final String localizedValue = ResourceBundle.getBundle(bundleClass.getName(), loopLocale).getString(key);
            if (!defaultValue.equals(localizedValue)) {
                returnObj.put(loopLocale, localizedValue);
            }
        }

        return Collections.unmodifiableMap(returnObj);
    }

    public static String debugLabel(Locale locale) {
        if (locale == null || PwmConstants.DEFAULT_LOCALE.equals(locale)) {
            return "default";
        }
        return locale.toLanguageTag();
    }
}
