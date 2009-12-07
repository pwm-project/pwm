/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.config;

import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.CrFactory;
import com.novell.ldapchai.exception.ChaiValidationException;
import com.novell.ldapchai.util.StringHelper;
import password.pwm.Constants;
import password.pwm.PwmPasswordPolicy;
import password.pwm.util.PwmLogger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.net.URI;
import java.text.DateFormat;
import java.util.*;

/**
 * Read the PWM configuration.
 *
 * @author Jason D. Rivard
 */
public class ConfigReader {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(ConfigReader.class.getName());

    //private ServletContext servletContext;
    private final Configuration globalConfig;
    private final ClassLoader classLoader;
    private final File configFile;
    private final Map<Locale, LocalizedConfiguration> localeConfigCache = new HashMap<Locale, LocalizedConfiguration>();

    private final long loadTime = System.currentTimeMillis();

// -------------------------- STATIC METHODS --------------------------

    public static boolean convertStrToBoolean(
            final String str
    )
    {
        return str != null && str.length() >= 1 &&
                (str.equalsIgnoreCase("true") ||
                        str.equalsIgnoreCase("1") ||
                        str.equalsIgnoreCase("yes") ||
                        str.equalsIgnoreCase("y"));
    }

    public static int convertStrToInt(
            final String str,
            final String defaultValue
    )
    {
        int value = 0;
        try {
            value = Integer.parseInt(defaultValue);
        } catch (Exception e) {
            //oh well
        }
        return convertStrToInt(str, value);
    }

// --------------------------- CONSTRUCTORS ---------------------------

    public ConfigReader(final File configFile)
    {
        this.configFile = configFile;

        classLoader = new ConfigClassLoader(configFile);
        globalConfig = this.readGlobalConfig();
    }

    private Configuration readGlobalConfig()
    {
        LOGGER.trace("reading non-localized settings in " + configFile.getAbsolutePath());

        final Configuration config = new Configuration();

        for (final PwmSetting setting : PwmSetting.values()) {
            final String value = readStr(setting.getKey());
            config.setSetting(setting, value);
            {
                final StringBuilder sb = new StringBuilder();
                sb.append("reading setting '").append(setting.getKey()).append("' value: ");
                sb.append(setting.isConfidential() ? "***removed***" : config.toString(setting));
                LOGGER.trace(sb.toString());
            }
        }


        {  // read maps (not attribute maps)
            config.loginContexts = StringHelper.tokenizeString(readStr("loginContexts"), ";;;", ":::");
            config.activateUserWriteAttributes = StringHelper.tokenizeString(readStr("activateUser.writeAttributes"), ",", "=");
            config.updateAttributesWriteAttributes = StringHelper.tokenizeString(readStr("updateAttributes.writeAttributes"), ",", "=");
            config.newUserWriteAttributes = StringHelper.tokenizeString(readStr("newUser.writeAttributes"), ",", "=");
        }


        {   //read password policy settings
            final Map<String,String> passwordPolicySettings = new HashMap<String,String>();
            for (final PwmPasswordRule rule : PwmPasswordRule.values()) {
                if (rule.getPwmConfigName() != null) {
                    String value = readStr(rule.getPwmConfigName(),rule.getDefaultValue());
                    {
                        final StringBuilder sb = new StringBuilder();
                        sb.append("reading setting '").append(rule.getPwmConfigName()).append("' value: ");
                        sb.append(value);
                        LOGGER.trace(sb.toString());
                    }

                    switch(rule) {
                        case DisallowedAttributes:
                            value = StringHelper.stringCollectionToString(StringHelper.tokenizeString(value,","),"\n");
                            break;
                        case DisallowedValues:
                            value = StringHelper.stringCollectionToString(StringHelper.tokenizeString(value,";;;"),"\n");
                            break;
                    }
                    passwordPolicySettings.put(rule.getKey(), value);

                }
            }

            
            config.globalPasswordPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(passwordPolicySettings);

        }
        return config;
    }


    private String readStr(final String name)
    {
        return this.readStr(name, "");
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public Configuration getGlobalConfig()
    {
        return globalConfig;
    }

    public long getLoadTime()
    {
        return loadTime;
    }

// -------------------------- OTHER METHODS --------------------------

    public Set<Locale> getCurrentlyLoadedLocales()
    {
        return Collections.unmodifiableSet(localeConfigCache.keySet());
    }

    public LocalizedConfiguration getLocalizedConfiguration(Locale locale)
    {
        locale = locale == null ? Locale.getDefault() : locale;

        LocalizedConfiguration config = localeConfigCache.get(locale);
        if (config == null) {
            config = readLocaleConfig(locale);
            localeConfigCache.put(locale, config);
        }
        return config;
    }

    private LocalizedConfiguration readLocaleConfig(final Locale locale)
    {
        final LocalizedConfiguration config = new LocalizedConfiguration();

        { //set challenges.
            final String requiredQuestionsConf = readStr("challenge.requiredQuestions", locale);
            final String randomQuestionsConf = readStr("challenge.randomQuestions", locale);

            final List<String> requiredQuestions = StringHelper.tokenizeString(requiredQuestionsConf, ";;;");
            final List<String> randomQuestions = StringHelper.tokenizeString(randomQuestionsConf, ";;;");


            final List<Challenge> challenges = new ArrayList<Challenge>();
            for (String question : requiredQuestions) {
                int minLength = 2;
                int maxLength = 255;

                final String[] s1 = question == null ? new String[0] : question.split(":::");
                if (s1.length > 0) {
                    question = s1[0];
                }
                if (s1.length > 1) {
                    try {
                        minLength = Integer.parseInt(s1[1]);
                    } catch (Exception e) { /* nothing to do */ }
                }
                if (s1.length > 2) {
                    try {
                        maxLength = Integer.parseInt(s1[2]);
                    } catch (Exception e) { /* nothing to do */ }
                }

                boolean adminDefined = true;
                if (question != null && question.equalsIgnoreCase("%user%")) {
                    question = null;
                    adminDefined = false;
                }

                challenges.add(CrFactory.newChallenge(true, question, minLength, maxLength, adminDefined));
            }

            for (String question : randomQuestions) {
                boolean adminDefined = true;
                if (question != null && question.equalsIgnoreCase("%user%")) {
                    question = null;
                    adminDefined = false;
                }
                challenges.add(CrFactory.newChallenge(false, question, 2, 255, adminDefined));
            }

            int minimumRands = ConfigReader.convertStrToInt(readStr("challenge.minRandomRequired", locale), 0);
            if (minimumRands > randomQuestions.size()) {
                minimumRands = randomQuestions.size();
            }

            try {
                config.challengeSet = CrFactory.newChallengeSet(challenges, locale, minimumRands, "pwm-defined " + Constants.SERVLET_VERSION);
            } catch (ChaiValidationException e) {
                LOGGER.warn("invalid challenge set configuration: " + e.getMessage());
            }
        }

        {
            final String from = readStr("password.emailFrom", locale);
            final String subject = readStr("password.emailSubject", locale);
            final String body = readStr("password.emailBody", locale);
            config.changePasswordEmail = new EmailInfo(null, from, body, subject);
        }

        {
            final String from = readStr("newUser.emailFrom", locale);
            final String subject = readStr("newUser.emailSubject", locale);
            final String body = readStr("newUser.emailBody", locale);
            config.newUserEmail = new EmailInfo(null, from, body, subject);
        }

        config.newUserCreationAttributes = readParameterConfigSetting(readStr("newUser.attributes", locale));
        config.activateUserAttributes = readParameterConfigSetting(readStr("activateUser.attributes", locale));
        config.updateAttributesAttributes = readParameterConfigSetting(readStr("updateAttributes.attributes", locale));
        config.challengeRequiredAttributes = readParameterConfigSetting(readStr("challenge.requiredAttributes", locale));

        {
            final List<ShortcutItem> settings = new ArrayList<ShortcutItem>();
            for (int i = 1; i <= 100; i++) {
                final String rawSetting = readStr("shortcut.query." + i);
                if (rawSetting != null && rawSetting.length() > 0) {
                    try {
                        final String[] splitSettings = rawSetting.split(";;;");
                        final ShortcutItem item = new ShortcutItem(
                                splitSettings[0],
                                URI.create(splitSettings[1]),
                                splitSettings[2],
                                splitSettings[3]
                        );
                        settings.add(item);
                    } catch (Exception e) {
                        LOGGER.warn("skipping mailformed setting for 'shortcut.query" + i + "', check configuration: " + e.getMessage());
                    }
                }
            }
            config.shortcutItems = Collections.unmodifiableList(settings);
        }

        config.applicationTitle = readStr(PwmSetting.APPLICATION_TILE.getKey(), locale);

        return config;
    }

    public static int convertStrToInt(
            final String str,
            final int defaultValue
    )
    {
        if (str == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static Map<String, ParameterConfig> readParameterConfigSetting(final String configLine)
    {
        // first seperate each parameter into a set.
        final List<String> parameterStringSet = StringHelper.tokenizeString(configLine, ",");

        final Map<String, ParameterConfig> parameterConfigs = new LinkedHashMap<String, ParameterConfig>();

        for (final String attrConfigLine : parameterStringSet) {
            final ParameterConfig loopConfig = ParameterConfig.parseConfigString(attrConfigLine);
            parameterConfigs.put(loopConfig.getAttributeName(), loopConfig);
        }

        return parameterConfigs;
    }

    private String readStr(final String name, final Locale locale)
    {
        return this.readStr(name, "", locale);
    }

    private String readStr(final String name, final String defaultStr)
    {
        return this.readStr(name, defaultStr, Locale.getDefault());
    }

    private String readStr(final String name, final String defaultStr, final Locale locale)
    {
        final ResourceBundle defaultBundle = ResourceBundle.getBundle(configFile.getName(), locale, classLoader);
        String theString;
        try {
            theString = defaultBundle.getString(name);
        } catch (MissingResourceException e) {
            theString = defaultStr;
        }
        return theString == null ? "" : theString;
    }

    public String toDebugString()
    {
        final StringBuilder sb = new StringBuilder();

        sb.append("GlobalConfig: [loadTime: ");
        sb.append(DateFormat.getDateTimeInstance().format(new Date(this.loadTime)));
        sb.append("] [");
        sb.append(this.getGlobalConfig().toString());
        sb.append("] LocalizedConfig for default locale: [");
        sb.append(this.getLocalizedConfiguration(null).toString());
        sb.append("]");

        return sb.toString();
    }

// -------------------------- INNER CLASSES --------------------------

    /**
     * Simple class loader to load ResourceBundle properties for a servlet's
     * WEB-INF directory.  Only the {@link #getResourceAsStream(String)} method
     * is modified to allow resources to be loaded from the WEB-INF directory, (or
     * anywhere in the parent ClassLoader's classpath.
     * <p/>
     * Calls to {@link #loadClass(String)} or {@link #findClass(String)} are not
     * overridden, and will be passed to the parent ClassLoader.  Thus, this classloader
     * doesn't actually change the behavior for normal class loading operations.
     * <p/>
     * Intended for use with {@link java.util.ResourceBundle#getBundle(String,java.util.Locale,ClassLoader)}
     *
     * @author Jason D. Rivard
     */
    public static class ConfigClassLoader extends ClassLoader {
        public final static String WEB_INF_DIR = "WEB-INF";

        private final String webInfPath;

        public ConfigClassLoader(final File forFile)
        {
            super(ConfigClassLoader.class.getClassLoader());
            webInfPath = forFile.getParent();
        }

        public InputStream getResourceAsStream(final String name)
        {
            final String pathName = webInfPath + File.separator + name;
            final File theFile = new File(pathName);
            if (!theFile.exists()) {
                return super.getResourceAsStream(name);
            }
            try {
                return new FileInputStream(theFile);
            } catch (FileNotFoundException e) {
                return null;
            }
        }
    }
}

