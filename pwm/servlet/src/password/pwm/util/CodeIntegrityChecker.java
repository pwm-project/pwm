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

package password.pwm.util;

import com.google.gson.GsonBuilder;
import password.pwm.AppProperty;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.StringArrayValue;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.health.HealthMessage;
import password.pwm.i18n.LocaleHelper;
import password.pwm.i18n.Message;
import password.pwm.ws.server.rest.bean.HealthRecord;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;

public class CodeIntegrityChecker {
    final static private PwmLogger LOGGER = PwmLogger.getLogger(CodeIntegrityChecker.class);
    final static private boolean debugFlag = false;

    static public class Stats {
        Set<String> examinedResourceBundles = new TreeSet<>();
        int totalLocales;
        int totalLocaleKeys;
        int presentLocalizations;
        int missingLocalizations;
        int totalLocalizationSlots;
        Map<String,String> perLocale_percentLocalizations = new TreeMap<>();
        Map<String,Integer> perLocale_presentLocalizations = new TreeMap<>();
        Map<String,Integer> perLocale_missingLocalizations = new TreeMap<>();
    }

    private static final List<Class> LOCALE_CHECK_CLASSES = new ArrayList<>(Arrays.asList(new Class[] {
            //password.pwm.i18n.Admin.class,
            //password.pwm.i18n.Config.class,
            password.pwm.i18n.Display.class,
            password.pwm.i18n.Error.class,
            //password.pwm.i18n.Health.class,
            password.pwm.i18n.Message.class,
    }));

    private static final Map<Method,Object[]> CHECK_ENUM_METHODS = new LinkedHashMap<>();
    static {
        try {
            CHECK_ENUM_METHODS.put(PwmSetting.class.getMethod("getDescription", Locale.class), new Object[]{
                    PwmConstants.DEFAULT_LOCALE
            });
            CHECK_ENUM_METHODS.put(PwmSetting.class.getMethod("getDefaultValue", PwmSetting.Template.class), new Object[]{
                    PwmSetting.Template.DEFAULT
            });


            CHECK_ENUM_METHODS.put(AppProperty.class.getMethod("getDefaultValue"), new Object[]{
            });

            CHECK_ENUM_METHODS.put(PwmError.class.getMethod("getLocalizedMessage", Locale.class, Configuration.class, String[].class), new Object[]{
                    PwmConstants.DEFAULT_LOCALE, null, null
            });
            CHECK_ENUM_METHODS.put(Message.class.getMethod("getLocalizedMessage", Locale.class, Configuration.class, String[].class), new Object[]{
                    PwmConstants.DEFAULT_LOCALE, null, null
            });
        } catch (NoSuchMethodException e) {
            final String message = CodeIntegrityChecker.class.getSimpleName() + " error setting up static check components: " + e.getMessage();
            System.err.print(message);
            System.out.print(message);
            System.exit(-1);
        }
    }

    public CodeIntegrityChecker() {
    }

    public Set<password.pwm.health.HealthRecord> checkResources() {
        Stats stats = new Stats();
        final Set<password.pwm.health.HealthRecord> returnSet = new TreeSet<>();
        returnSet.addAll(checkEnumMethods());
        returnSet.addAll(checkLocalesOnBundle(stats));
        return returnSet;
    }

    public Stats getStats() {
        final Stats stats = new Stats();
        checkLocalesOnBundle(stats);
        return stats;
    }

    private Set<password.pwm.health.HealthRecord> checkEnumMethods() {
        final Set<password.pwm.health.HealthRecord> returnSet = new LinkedHashSet<>();
        for (final Method method : CHECK_ENUM_METHODS.keySet()) {
            final Object[] arguments = CHECK_ENUM_METHODS.get(method);
            returnSet.addAll(checkEnumMethods(method,arguments));
        }
        return returnSet;
    }

    private Set<password.pwm.health.HealthRecord> checkEnumMethods(final Method enumMethod, final Object[] arguments)
    {
        final Set<password.pwm.health.HealthRecord> returnRecords = new LinkedHashSet<>();
        try {
            final Method enumValuesMethod = enumMethod.getDeclaringClass().getMethod("values");
            final Object[] enumValues = (Object[])enumValuesMethod.invoke(null);
            for (final Object enumValue : enumValues) {
                try {
                    enumMethod.invoke(enumValue,arguments);
                } catch (Exception e) {
                    final Throwable cause = e.getCause();
                    final String errorMsg = cause != null ? cause.getMessage() != null ? cause.getMessage() : cause.toString() : e.getMessage();
                    final StringBuilder methodName = new StringBuilder();
                    methodName.append(enumMethod.getDeclaringClass().getName()).append(".").append(enumValue.toString()).append(":").append(enumMethod.getName()).append("(");
                    for (int i = 0; i < enumMethod.getParameterTypes().length; i++) {
                        methodName.append(enumMethod.getParameterTypes()[i].getSimpleName());
                        if (i < (enumMethod.getParameterTypes().length-1)) {
                            methodName.append(",");
                        }
                    }
                    methodName.append(")");
                    returnRecords.add(password.pwm.health.HealthRecord.forMessage(HealthMessage.BrokenMethod,
                            methodName.toString(), errorMsg));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return returnRecords;
    }

    private Set<password.pwm.health.HealthRecord> checkLocalesOnBundle(
            final Stats stats
    ) {
        final Set<password.pwm.health.HealthRecord> returnSet = new LinkedHashSet<>();
        stats.totalLocales = knownLocales().size();
        for (final Class loopClass : LOCALE_CHECK_CLASSES) {
            final Map<Locale,List<String>> missingKeys = checkLocalesOnBundle(loopClass,stats);
            for (final Locale locale : missingKeys.keySet()) {
                for (final String key : missingKeys.get(locale)) {
                    returnSet.add(password.pwm.health.HealthRecord.forMessage(
                            HealthMessage.MissingResource,
                            loopClass.getName(),
                            locale.toString() + " " + locale.getDisplayName(),
                            key
                    ));
                }
            }
        }
        return returnSet;
    }

    private Map<Locale,String> knownLocales() {

        final List<Locale> knownLocales = new ArrayList();
        try {
            final StringArrayValue stringArrayValue = (StringArrayValue)PwmSetting.KNOWN_LOCALES.getDefaultValue(PwmSetting.Template.DEFAULT);
            final List<String> rawValues = stringArrayValue.toNativeObject();
            final Map<String,String> localeFlagMap = Configuration.convertStringListToNameValuePair(rawValues, "::");
            for (final String rawValue : localeFlagMap.keySet()) {
                knownLocales.add(LocaleHelper.parseLocaleString(rawValue));
            }
        } catch (PwmOperationalException e) {
            throw new IllegalStateException("error reading default locale list",e);
        }

        final Map<Locale,String> returnMap = new LinkedHashMap<>();

        Collections.sort(knownLocales,new Comparator<Locale>() {
            public int compare(Locale o1, Locale o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });
        for (final Locale locale : knownLocales) {
            final String descr = locale.toString() + " - " + locale.getDisplayLanguage();
            returnMap.put(locale,descr);
        }
        return returnMap;
    }

    private Map<Locale,List<String>> checkLocalesOnBundle(
            final Class checkClass,
            final Stats stats
    ) {
        final Map<Locale,List<String>> returnMap = new LinkedHashMap<>();
        final ResourceBundle defaultLocaleBundle = ResourceBundle.getBundle(checkClass.getName());

        stats.totalLocaleKeys += defaultLocaleBundle.keySet().size();
        stats.examinedResourceBundles.add(checkClass.toString());
        for (final Locale locale : knownLocales().keySet()) {
            final String localeDescr = knownLocales().get(locale);
            if (!stats.perLocale_missingLocalizations.containsKey(localeDescr)) {
                stats.perLocale_missingLocalizations.put(localeDescr,0);
            }
            if (!stats.perLocale_presentLocalizations.containsKey(localeDescr)) {
                stats.perLocale_presentLocalizations.put(localeDescr, 0);
            }
            final String bundleFilename = PwmConstants.DEFAULT_LOCALE.equals(locale)
                    ? checkClass.getSimpleName() + ".properties"
                    : checkClass.getSimpleName() + "_" + locale.toString() + ".properties";
            final Properties checkProperties = new Properties();
            try {
                final InputStream stream = checkClass.getResourceAsStream(bundleFilename);
                if (stream == null) {
                    if (debugFlag) {
                        LOGGER.trace("missing resource bundle: bundle=" + checkClass.getName() + ", locale=" + locale.toString());
                    }
                } else {
                    LOGGER.trace("checking file " + bundleFilename);
                    checkProperties.load(stream);
                    final List<String> returnList = new ArrayList<>();
                    for (final String key : defaultLocaleBundle.keySet()) {
                        stats.totalLocalizationSlots++;
                        if (!checkProperties.containsKey(key)) {
                            if (debugFlag) {
                                LOGGER.trace("missing resource: bundle=" + checkClass.toString() + ", locale=" + locale.toString() + "' key=" + key);
                            }
                            returnList.add(key);
                            stats.missingLocalizations++;
                            stats.perLocale_missingLocalizations.put(localeDescr,stats.perLocale_missingLocalizations.get(localeDescr) + 1);
                        } else {
                            stats.presentLocalizations++;
                            stats.perLocale_presentLocalizations.put(localeDescr,stats.perLocale_presentLocalizations.get(localeDescr) + 1);
                        }
                    }
                    Collections.sort(returnList);
                    returnMap.put(locale,Collections.unmodifiableList(returnList));
                }
            } catch (IOException e) {
                if (debugFlag) {
                    LOGGER.trace("error loading resource bundle for class='" + checkClass.toString() + ", locale=" + locale.toString() + "', error: " + e.getMessage());
                }
            }
        }
        for (final Locale locale : knownLocales().keySet()) {
            final String localeDescr = knownLocales().get(locale);
            if (stats.perLocale_missingLocalizations.containsKey(localeDescr)
                    && stats.perLocale_presentLocalizations.containsKey(localeDescr)) {
                int total = stats.perLocale_missingLocalizations.get(localeDescr) + stats.perLocale_presentLocalizations.get(localeDescr);
                Percent percent = new Percent(stats.perLocale_presentLocalizations.get(localeDescr),total);
                stats.perLocale_percentLocalizations.put(localeDescr,percent.pretty(0));
            }

        }
        return returnMap;
    }

    public String asPrettyJsonOutput() {
        final Map<String,Object> outputMap = new LinkedHashMap<>();
        outputMap.put("information",
                PwmConstants.PWM_APP_NAME + " " + PwmConstants.SERVLET_VERSION + " IntegrityCheck " + PwmConstants.DEFAULT_DATETIME_FORMAT.format(
                        new Date()));
        outputMap.put("localeInformation", this.getStats());
        {
            final Set<HealthRecord> healthBeans = new LinkedHashSet<>();
            for (final password.pwm.health.HealthRecord record : this.checkEnumMethods()) {
                healthBeans.add(
                        HealthRecord.fromHealthRecord(record, PwmConstants.DEFAULT_LOCALE, null));
            }
            outputMap.put("enumMethodHealthChecks", healthBeans);
        }
        {
            final Set<HealthRecord> healthBeans = new LinkedHashSet<>();
            for (final password.pwm.health.HealthRecord record : this.checkLocalesOnBundle(new Stats())) {
                healthBeans.add(
                        HealthRecord.fromHealthRecord(record, PwmConstants.DEFAULT_LOCALE, null));
            }
            outputMap.put("localeHealthChecks", healthBeans);
        }
        return Helper.getGson(new GsonBuilder().setPrettyPrinting()).toJson(outputMap);
    }
}
