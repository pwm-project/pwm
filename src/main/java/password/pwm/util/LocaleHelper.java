/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.util;

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.*;
import password.pwm.config.value.ChallengeValue;
import password.pwm.config.value.StringArrayValue;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.i18n.Display;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.i18n.PwmLocaleBundle;
import password.pwm.util.logging.PwmLogger;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class LocaleHelper {

    private static final PwmLogger LOGGER = PwmLogger.forClass(LocaleHelper.class);

    public static Class classForShortName(final String shortName) {
        if (shortName == null || shortName.isEmpty()) {
            return null;
        }
        final String className = PwmLocaleBundle.class.getPackage().getName() + "." + shortName;
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public static String getLocalizedMessage(final Locale locale, final PwmDisplayBundle key, final Configuration config) {
        return getLocalizedMessage(locale, key.getKey(), config, key.getClass());
    }

    public static String getLocalizedMessage(final Locale locale, final PwmDisplayBundle key, final Configuration config, final String[] args) {
        return getLocalizedMessage(locale, key.getKey(), config, key.getClass(), args);
    }

    public static String getLocalizedMessage(final PwmDisplayBundle key, final PwmRequest pwmRequest, final String... values) {
        return getLocalizedMessage(
                pwmRequest == null ? PwmConstants.DEFAULT_LOCALE : pwmRequest.getLocale(),
                key.getKey(),
                pwmRequest == null ? null : pwmRequest.getConfig(),
                key.getClass(),
                values
        );
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
        if (!PwmDisplayBundle.class.isAssignableFrom(bundleClass)) {
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
        private final Class<? extends PwmDisplayBundle> bundleClass;
        private final Locale locale;

        public DisplayMaker(
                Locale locale,
                Class<? extends PwmDisplayBundle> bundleClass,
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
            final Configuration configuration,
            final Class<? extends PwmDisplayBundle> bundleClass,
            final String key,
            final Locale defaultLocale
    )
    {
        final Map<Locale, String> returnObj = new LinkedHashMap<>();
        final Collection<Locale> localeList = configuration == null
                ? new ArrayList<>(PwmConstants.INCLUDED_LOCALES)
                : new ArrayList<>(configuration.getKnownLocales());

        final String defaultValue = getLocalizedMessage(defaultLocale, key, configuration, bundleClass);
        returnObj.put(defaultLocale, defaultValue);

        for (final Locale loopLocale : localeList) {
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

    public static String booleanString(final boolean input, PwmRequest pwmRequest) {
        final Display key = input ? Display.Value_True : Display.Value_False;

        return pwmRequest == null
                ? Display.getLocalizedMessage(null, key, null)
                : Display.getLocalizedMessage(pwmRequest.getLocale(), key, pwmRequest.getConfig());
    }

    public static String booleanString(final boolean input, Locale locale, Configuration configuration) {
        Display key = input ? Display.Value_True : Display.Value_False;
        return Display.getLocalizedMessage(locale, key, configuration);
    }

    static public class LocaleStats {
        List<Locale> localesExamined = new ArrayList<>();
        int totalKeys;
        int presentSlots;
        int missingSlots;
        int totalSlots;
        String totalPercentage;
        Map<Locale,String> perLocale_percentLocalizations = new LinkedHashMap<>();
        Map<Locale,Integer> perLocale_presentLocalizations = new LinkedHashMap<>();
        Map<Locale,Integer> perLocale_missingLocalizations = new LinkedHashMap<>();
        Map<PwmLocaleBundle,Map<Locale,List<String>>> missingKeys = new LinkedHashMap<>();

        public List<Locale> getLocalesExamined() {
            return localesExamined;
        }

        public int getTotalKeys() {
            return totalKeys;
        }

        public String getTotalPercentage() {
            return totalPercentage;
        }

        public int getPresentSlots() {
            return presentSlots;
        }

        public int getMissingSlots() {
            return missingSlots;
        }

        public int getTotalSlots() {
            return totalSlots;
        }

        public Map<Locale, String> getPerLocale_percentLocalizations() {
            return perLocale_percentLocalizations;
        }

        public Map<Locale, Integer> getPerLocale_presentLocalizations() {
            return perLocale_presentLocalizations;
        }

        public Map<Locale, Integer> getPerLocale_missingLocalizations() {
            return perLocale_missingLocalizations;
        }

        public Map<PwmLocaleBundle, Map<Locale, List<String>>> getMissingKeys() {
            return missingKeys;
        }
    }

    static public class ConfigLocaleStats {
        List<Locale> defaultChallenges = new ArrayList<>();
        Map<Locale,String> description_percentLocalizations = new LinkedHashMap<>();
        Map<Locale,Integer> description_presentLocalizations = new LinkedHashMap<>();
        Map<Locale,Integer> description_missingLocalizations = new LinkedHashMap<>();

        public List<Locale> getDefaultChallenges() {
            return defaultChallenges;
        }

        public Map<Locale, String> getDescription_percentLocalizations() {
            return description_percentLocalizations;
        }

        public Map<Locale, Integer> getDescription_presentLocalizations() {
            return description_presentLocalizations;
        }

        public Map<Locale, Integer> getDescription_missingLocalizations() {
            return description_missingLocalizations;
        }
    }

    public static ConfigLocaleStats getConfigLocaleStats() throws PwmUnrecoverableException, PwmOperationalException {

        final ConfigLocaleStats configLocaleStats = new ConfigLocaleStats();
        {
            final StoredValue storedValue = PwmSetting.CHALLENGE_RANDOM_CHALLENGES.getDefaultValue(PwmSettingTemplate.DEFAULT);
            Map<String, List<ChallengeItemConfiguration>> value = ((ChallengeValue) storedValue).toNativeObject();

            for (String localeStr : value.keySet()) {
                final Locale loopLocale = LocaleHelper.parseLocaleString(localeStr);
                configLocaleStats.getDefaultChallenges().add(loopLocale);
            }
        }

        for (final Locale locale : LocaleInfoGenerator.knownLocales()) {
            configLocaleStats.description_presentLocalizations.put(locale,0);
            configLocaleStats.description_missingLocalizations.put(locale,0);
        }

        for (final PwmSetting pwmSetting : PwmSetting.values()) {
            final String defaultValue = pwmSetting.getDescription(PwmConstants.DEFAULT_LOCALE);
            configLocaleStats.description_presentLocalizations.put(PwmConstants.DEFAULT_LOCALE, configLocaleStats.description_presentLocalizations.get(PwmConstants.DEFAULT_LOCALE) + 1);
            for (final Locale locale : LocaleInfoGenerator.knownLocales()) {
                if (!PwmConstants.DEFAULT_LOCALE.equals(locale)) {
                    final String localeValue = pwmSetting.getDescription(locale);
                    if (defaultValue.equals(localeValue)) {
                        configLocaleStats.description_missingLocalizations.put(PwmConstants.DEFAULT_LOCALE, configLocaleStats.description_missingLocalizations.get(locale) + 1);
                    } else {
                        configLocaleStats.description_presentLocalizations.put(PwmConstants.DEFAULT_LOCALE, configLocaleStats.description_presentLocalizations.get(locale) + 1);
                    }
                }
            }
        }

        for (final Locale locale : LocaleInfoGenerator.knownLocales()) {
            final int totalCount = PwmSetting.values().length;
            final int presentCount = configLocaleStats.getDescription_presentLocalizations().get(locale);
            final Percent percent = new Percent(presentCount, totalCount);
            configLocaleStats.getDescription_percentLocalizations().put(locale, percent.pretty());
        }
        return configLocaleStats;
    }

    public static LocaleStats getStatsForBundles(final Collection<PwmLocaleBundle> bundles) {
        final LocaleStats stats = new LocaleStats();
        LocaleInfoGenerator.checkLocalesOnBundle(stats,bundles);
        return stats;
    }

    private static class LocaleInfoGenerator {
        private static final boolean debugFlag = false;

        private static void checkLocalesOnBundle(
                final LocaleStats stats,
                final Collection<PwmLocaleBundle> bundles
        ) {
            for (final PwmLocaleBundle pwmLocaleBundle : bundles) {
                final Map<Locale,List<String>> missingKeys = checkLocalesOnBundle(pwmLocaleBundle, stats);
                stats.missingKeys.put(pwmLocaleBundle, missingKeys);
            }

            stats.getLocalesExamined().addAll(knownLocales());

            if (stats.getTotalSlots() > 0) {
                Percent percent = new Percent(stats.getPresentSlots(),stats.getTotalSlots());
                stats.totalPercentage = percent.pretty();
            } else {
                stats.totalPercentage = Percent.ZERO.pretty();
            }
        }


        private static Map<Locale,List<String>> checkLocalesOnBundle(
                final PwmLocaleBundle pwmLocaleBundle,
                final LocaleStats stats
        ) {
            final Map<Locale, List<String>> returnMap = new LinkedHashMap<>();
            final int keyCount = pwmLocaleBundle.getKeys().size();
            stats.totalKeys += keyCount;

            for (final Locale locale : knownLocales()) {
                final List<String> missingKeys = missingKeysForBundleAndLocale(pwmLocaleBundle, locale);
                final int missingKeyCount = missingKeys.size();
                final int presentKeyCount = keyCount - missingKeyCount;

                stats.totalSlots += keyCount;
                stats.missingSlots += missingKeyCount;
                stats.presentSlots += presentKeyCount;
                if (!stats.perLocale_missingLocalizations.containsKey(locale)) {
                    stats.perLocale_missingLocalizations.put(locale,0);
                }
                stats.perLocale_missingLocalizations.put(locale, stats.getPerLocale_missingLocalizations().get(locale) + missingKeyCount);
                if (!stats.perLocale_presentLocalizations.containsKey(locale)) {
                    stats.perLocale_presentLocalizations.put(locale,0);
                }
                stats.perLocale_presentLocalizations.put(locale, stats.perLocale_presentLocalizations.get(locale) + presentKeyCount);

                if (keyCount > 0) {
                    Percent percent = new Percent(presentKeyCount, keyCount);
                    stats.perLocale_percentLocalizations.put(locale, percent.pretty(0));
                } else {
                    stats.perLocale_percentLocalizations.put(locale, Percent.ZERO.pretty());
                }

                returnMap.put(locale, missingKeys);
            }
            return returnMap;
        }

        private static List<String> missingKeysForBundleAndLocale(
                final PwmLocaleBundle pwmLocaleBundle,
                final Locale locale
        )
        {
            final List<String> returnList = new ArrayList<>();

            final String bundleFilename = PwmConstants.DEFAULT_LOCALE.equals(locale)
                    ? pwmLocaleBundle.getTheClass().getSimpleName() + ".properties"
                    : pwmLocaleBundle.getTheClass().getSimpleName() + "_" + locale.toString() + ".properties";
            final Properties checkProperties = new Properties();

            try {
                final InputStream stream = pwmLocaleBundle.getTheClass().getResourceAsStream(bundleFilename);
                if (stream == null) {
                    if (debugFlag) {
                        LOGGER.trace("missing resource bundle: bundle=" + pwmLocaleBundle.getTheClass().getName() + ", locale=" + locale.toString());
                    }
                    returnList.addAll(pwmLocaleBundle.getKeys());
                } else {
                    LOGGER.trace("checking file " + bundleFilename);
                    checkProperties.load(stream);
                    for (final String key : pwmLocaleBundle.getKeys()) {
                        if (!checkProperties.containsKey(key)) {
                            if (debugFlag) {
                                LOGGER.trace("missing resource: bundle=" + pwmLocaleBundle.getTheClass().toString() + ", locale=" + locale.toString() + "' key=" + key);
                            }
                            returnList.add(key);
                        }
                    }
                }
            } catch (IOException e) {
                if (debugFlag) {
                    LOGGER.trace("error loading resource bundle for class='" + pwmLocaleBundle.getTheClass().toString() + ", locale=" + locale.toString() + "', error: " + e.getMessage());
                }
            }
            Collections.sort(returnList);
            return returnList;
        }

        private static List<Locale> knownLocales() {
            final List<Locale> knownLocales = new ArrayList<>();
            try {
                final StringArrayValue stringArrayValue = (StringArrayValue) PwmSetting.KNOWN_LOCALES.getDefaultValue(PwmSettingTemplate.DEFAULT);
                final List<String> rawValues = stringArrayValue.toNativeObject();
                final Map<String,String> localeFlagMap = StringUtil.convertStringListToNameValuePair(rawValues, "::");
                for (final String rawValue : localeFlagMap.keySet()) {
                    knownLocales.add(LocaleHelper.parseLocaleString(rawValue));
                }
            } catch (PwmException e) {
                throw new IllegalStateException("error reading default locale list",e);
            }

            final Map<String,Locale> returnMap = new TreeMap<>();

            for (final Locale locale : knownLocales) {
                returnMap.put(locale.getDisplayName(), locale);
            }
            return new ArrayList<>(returnMap.values());
        }
    }

    public static Map<PwmLocaleBundle,Map<String,List<Locale>>> getModifiedKeysInConfig(final Configuration configuration) {
        final Map<PwmLocaleBundle,Map<String,List<Locale>>> returnObj = new LinkedHashMap<>();
        for (final PwmLocaleBundle pwmLocaleBundle : PwmLocaleBundle.values()) {
            for (final String key : pwmLocaleBundle.getKeys()) {
                for (final Locale locale : configuration.getKnownLocales()) {
                    final String defaultValue = LocaleHelper.getLocalizedMessage(locale, key, null, pwmLocaleBundle.getTheClass());
                    final String customizedValue = LocaleHelper.getLocalizedMessage(locale, key, configuration, pwmLocaleBundle.getTheClass());
                    if (defaultValue != null && !defaultValue.equals(customizedValue)) {
                        if (!returnObj.containsKey(pwmLocaleBundle)) {
                            returnObj.put(pwmLocaleBundle,new LinkedHashMap<String, List<Locale>>());
                        }
                        if (!returnObj.get(pwmLocaleBundle).containsKey(key)) {
                            returnObj.get(pwmLocaleBundle).put(key, new ArrayList<Locale>());
                        }

                        returnObj.get(pwmLocaleBundle).get(key).add(locale);
                    }
                }
            }
        }
        return returnObj;
    }
}
