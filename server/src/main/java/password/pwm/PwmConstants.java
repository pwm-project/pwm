/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm;

import org.apache.commons.csv.CSVFormat;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.secure.PwmBlockAlgorithm;
import password.pwm.util.secure.PwmHashAlgorithm;

import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TimeZone;

/**
 * Constant values used throughout the servlet.
 *
 * @author Jason D. Rivard
 */
public abstract class PwmConstants {
// ------------------------------ FIELDS ------------------------------

    // ------------------------- PUBLIC CONSTANTS -------------------------
    public static final String BUILD_TIME           = readBuildInfoBundle("build.time", Instant.now().toString());
    public static final String BUILD_NUMBER         = readBuildInfoBundle("build.number","0");
    public static final String BUILD_TYPE           = readBuildInfoBundle("build.type","");
    public static final String BUILD_USER           = readBuildInfoBundle("build.user",System.getProperty("user.name"));
    public static final String BUILD_REVISION       = readBuildInfoBundle("build.revision","0");
    public static final String BUILD_JAVA_VENDOR    = readBuildInfoBundle("build.java.vendor");
    public static final String BUILD_JAVA_VERSION   = readBuildInfoBundle("build.java.version");
    public static final String BUILD_VERSION        = readBuildInfoBundle("build.version","");

    private static final String MISSING_VERSION_STRING = readPwmConstantsBundle("missingVersionString");
    public static final String SERVLET_VERSION;

    static {
        final String servletVersion =
                (BUILD_VERSION.length() > 0 ? "v" + BUILD_VERSION : "") +
                        (BUILD_NUMBER.length() > 0 ? " b" + BUILD_NUMBER : "") +
                        (BUILD_REVISION.length() > 0 ? " r" + BUILD_REVISION : "") +
                        (BUILD_TYPE.length() > 0 ? " (" + BUILD_TYPE + ")" : "").trim();

        SERVLET_VERSION = servletVersion.isEmpty()
                ? MISSING_VERSION_STRING
                : servletVersion;
    }

    public static final String CHAI_API_VERSION = com.novell.ldapchai.ChaiConstant.CHAI_API_VERSION;

    public static final String DEFAULT_CONFIG_FILE_FILENAME = readPwmConstantsBundle("defaultConfigFilename");

    public static final String PWM_APP_NAME = readPwmConstantsBundle("pwm.appName");
    public static final String PWM_URL_HOME = readPwmConstantsBundle("url.pwm-home");
    public static final String PWM_URL_CLOUD = readPwmConstantsBundle("url.pwm-cloud");

    public static final String PWM_APP_NAME_VERSION = PWM_APP_NAME + " " + SERVLET_VERSION;

    public static final long VERSION_CHECK_FREQUENCEY_MS = Long.parseLong(readPwmConstantsBundle("versionCheckFrequencyMs"));
    public static final long VERSION_CHECK_FAIL_RETRY_MS = Long.parseLong(readPwmConstantsBundle("versionCheckFailRetryMs"));
    public static final long STATISTICS_PUBLISH_FREQUENCY_MS = Long.parseLong(readPwmConstantsBundle("statisticsPublishFrequencyMs"));

    public static final String CONFIGMANAGER_INTRUDER_USERNAME = "ConfigurationManagerLogin";

    public static final Locale DEFAULT_LOCALE = new Locale(readPwmConstantsBundle("locale.defaultLocale"));
    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF8");

    public static final CSVFormat DEFAULT_CSV_FORMAT = CSVFormat.DEFAULT;

    public static final String DEFAULT_DATETIME_FORMAT_STR = readPwmConstantsBundle("locale.defaultDateTimeFormat");
    public static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone(readPwmConstantsBundle("locale.defaultTimeZone"));

    public static final String APPLICATION_PATH_INFO_FILE = readPwmConstantsBundle("applicationPathInfoFile");

    public static final boolean ENABLE_EULA_DISPLAY = Boolean.parseBoolean(readPwmConstantsBundle("enableEulaDisplay"));
    public static final boolean TRIAL_MODE = Boolean.parseBoolean(readPwmConstantsBundle("trial"));
    public static final int TRIAL_MAX_AUTHENTICATIONS = 100;
    public static final int TRIAL_MAX_TOTAL_AUTH = 10000;


    public static final int DATABASE_ACCESSOR_KEY_LENGTH = Integer.parseInt(readPwmConstantsBundle("databaseAccessor.keyLength"));

    public static final String LDAP_AD_PASSWORD_POLICY_CONTROL_ASN = "1.2.840.113556.1.4.2066";
    public static final String PROFILE_ID_ALL = "all";

    public static final String TOKEN_KEY_PWD_CHG_DATE = "_lastPwdChange";
    public static final float JAVA_MINIMUM_VERSION = (float)1.6;

    public static final String HTTP_BASIC_AUTH_PREFIX = readPwmConstantsBundle("httpHeaderAuthorizationBasic");
    public static final String HTTP_HEADER_REST_CLIENT_KEY = readPwmConstantsBundle("httpRestClientKey");

    public static final String DEFAULT_BAD_PASSWORD_ATTEMPT = readPwmConstantsBundle("defaultBadPasswordAttempt");

    public static final String CONTEXT_ATTR_CONTEXT_MANAGER = "ContextManager";
    public static final String CONTEXT_ATTR_RESOURCE_DATA = "ResourceFileServlet-Data";

    public static final String SESSION_ATTR_PWM_SESSION = "PwmSession";
    public static final String SESSION_ATTR_BEANS = "SessionBeans";
    public static final String SESSION_ATTR_PWM_APP_NONCE = "PwmApplication-Nonce";
    public static final String SESSION_ATTR_FORGOTTEN_PW_USERINFO_CACHE = "ForgottenPw-UserInfoCache";

    public static final PwmBlockAlgorithm IN_MEMORY_PASSWORD_ENCRYPT_METHOD = PwmBlockAlgorithm.AES;
    public static final PwmHashAlgorithm SETTING_CHECKSUM_HASH_METHOD = PwmHashAlgorithm.SHA256;


    public static final String LOG_REMOVED_VALUE_REPLACEMENT = readPwmConstantsBundle("log.removedValue");

    public static final Collection<Locale> INCLUDED_LOCALES;

    static {
        final List<Locale> localeList = new ArrayList<>();
        final String inputString = readPwmConstantsBundle("includedLocales");
        final List<String> inputList = JsonUtil.deserializeStringList(inputString);
        for (final String localeKey : inputList) {
            localeList.add(new Locale(localeKey));
        }
        INCLUDED_LOCALES = Collections.unmodifiableCollection(localeList);
    }

    public static final String URL_JSP_CONFIG_GUIDE = "WEB-INF/jsp/configguide-%1%.jsp";

    public static final String URL_PREFIX_PRIVATE = "/private";
    public static final String URL_PREFIX_PUBLIC = "/public";
    public static final String URL_PREFIX_REST = "/rest";


    public static final String PARAM_ACTION_REQUEST = "processAction";
    public static final String PARAM_ACTION_STATE = "actionState";
    public static final String PARAM_RESPONSE_PREFIX = "PwmResponse_R_";
    public static final String PARAM_QUESTION_PREFIX = "PwmResponse_Q_";
    public static final String PARAM_FORM_ID = "pwmFormID";
    public static final String PARAM_SESSION_STATE_INFO = "ssi";

    public static final String PARAM_OTP_TOKEN = "otpToken";
    public static final String PARAM_TOKEN = readPwmConstantsBundle("paramName.token");
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_CONTEXT = "context";
    public static final String PARAM_LDAP_PROFILE = "ldapProfile";
    public static final String PARAM_SKIP_CAPTCHA = "skipCaptcha";
    public static final String PARAM_POST_LOGIN_URL = "posturl";
    public static final String PARAM_FILE_UPLOAD = "fileUpload";
    public static final String PARAM_RECOVERY_OAUTH_RESULT = "roauthResults";
    public static final String PARAM_SIGNED_FORM = "signedForm";

    public static final String COOKIE_PERSISTENT_CONFIG_LOGIN = "persistentConfigLogin";

    public static final String VALUE_REPLACEMENT_USERNAME = "%USERNAME%";

    public static final String RESOURCE_FILE_EULA_TXT = "eula.txt";
    public static final String RESOURCE_FILE_PRIVACY_TXT = "privacy.txt";
    public static final String RESOURCE_FILE_WELCOME_TXT = "welcome.txt";

    // don't worry.  look over there.
    public static final String[] X_AMB_HEADER = new String[]{
            "bonjour!",
            "something witty!",
            "just like X-Fry, only ambier",
            "mooooooo!",
            "amby wamby",
            "deciphered by leading cryptologists",
            "a retina scanner would be a lot cooler",
            "if you can read this header, your debugging to close",
            "my author wrote this servlet and all I got was this lousy header...",
            "j00s j0ur d4ddy",
            "amb is my her0",
            "dear hax0r: fl33! n0\\/\\/!",
            "if its broke, it's krowten's fault",
            "in the future, you'll just /think/ your password",
            "all passwords super-duper encrypted with double rot13",
            "chance of password=password? 92%",
            "from the next-time-just-phone-it-in dept",
            "this header contains 100% genuine nougat",
            "are passwords really necessary?  can't we all just get along?",
            "That's amazing! I've got the same combination on my luggage!",
            "just because it looks plaintext doesn't mean there isn't a steganographic 1024bit AES key",
            "whatever happened to speech wreck a nation technology?",     // thx wk
            "Password schmassword, I can't even remember my user name...",
            "The Mummy's password is in crypted",   // thx wk
            "The zombie's password is expired", // wk
            "Chuck Yeager's password is in plane text", // thx wk
            "Fruit flies have one time use passwords",
            "As Gregor Samsa awoke one morning from uneasy dreams he found himself transformed in his bed into a gigantic password.",
            "NOTICE: This header is protected by the Digital Millennium Copyright Act of 1996.  Reading this header is strictly forbidden.",
            "Not sure if password == password, or I just _think_ my password == password and any password will work.",
            "This sure is a lot of code to change one measly string.",
            "50,000 lines of code to change one stupid string.  Seems legit.",
            "I don't always manage my passwords; but when I do, I use that password thingy amb developed.",
            "My password is but an egg.",
            "Your password must be scanned by the TSA to ensure the safety of your fellow travelers.  Please take off your password's shoes to continue.",
            "That password really tied the room together dude.",
            "Bite my shiny metal password!",
            "I needed a password eight characters long so I picked Snow White and the Seven Dwarves.", //nick helm
            "Roses are #FF0000 , Violets are #0000FF, All your password are belongs to us.",
            "I changed my password to \"incorrect\", so whenever i forget, it will tell me \"your password is incorrect\".",
            "passwords are like underwear, changing underwear regularly is a good thing.", //menno
            "daisy, daisy, give me your password do...",
            "it's a wholesome can of software goodness", // thx krowten
            "this password is an memorial of the richard d. kiel memorial abend",
    };


    private static String readPwmConstantsBundle(final String key) {
        return  ResourceBundle.getBundle(PwmConstants.class.getName()).getString(key);
    }

    private static String readBuildInfoBundle(final String key) {
        return readBuildInfoBundle(key, null);
    }

    private static String readBuildInfoBundle(final String key, final String defaultValue) {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle("password.pwm.BuildInformation");
        if (resourceBundle.containsKey(key)) {
            return resourceBundle.getString(key);
        }

        return defaultValue;
    }

// -------------------------- ENUMERATIONS --------------------------


    public enum ContentTypeValue {
        json("application/json; charset=" + PwmConstants.DEFAULT_CHARSET),
        zip("application/zip"),
        xml("text/xml; charset=" + PwmConstants.DEFAULT_CHARSET),
        csv("text/csv; charset=" + PwmConstants.DEFAULT_CHARSET),
        javascript("text/javascript; charset=" + PwmConstants.DEFAULT_CHARSET),
        plain("text/plain; charset=" + PwmConstants.DEFAULT_CHARSET),
        html("text/html; charset=" + PwmConstants.DEFAULT_CHARSET),
        form("application/x-www-form-urlencoded; charset=" + PwmConstants.DEFAULT_CHARSET),
        png("image/png"),
        octetstream("application/octet-stream"),
        ;

        private final String headerValue;

        ContentTypeValue(final String headerValue) {
            this.headerValue = headerValue;
        }

        public String getHeaderValue() {
            return headerValue;
        }
    }

    public enum AcceptValue {
        json("application/json"),
        html("text/html"),
        ;

        private String headerValue;

        AcceptValue(final String headerValue) {
            this.headerValue = headerValue;
        }

        public String getHeaderValue() {
            return headerValue;
        }
    }
}

