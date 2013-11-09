/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import password.pwm.i18n.Display;
import password.pwm.i18n.Message;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
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
    public static final String BUILD_TIME           = readBuildInfoBundle("build.time");
    public static final String BUILD_NUMBER         = readBuildInfoBundle("build.number");
    public static final String BUILD_TYPE           = readBuildInfoBundle("build.type");
    public static final String BUILD_NAME           = readBuildInfoBundle("build.name");
    public static final String BUILD_USER           = readBuildInfoBundle("build.user");
    public static final String BUILD_REVISION       = readBuildInfoBundle("build.revision");
    public static final String BUILD_JAVA_VENDOR    = readBuildInfoBundle("build.java.vendor");
    public static final String BUILD_JAVA_VERSION   = readBuildInfoBundle("build.java.version");
    public static final String PWM_VERSION          = readBuildInfoBundle("pwm.version");

    public static final String SERVLET_VERSION = "v" + PWM_VERSION + " b" + BUILD_NUMBER +
            (BUILD_REVISION.length() > 0 ? " r" + BUILD_REVISION : "") +
            " (" + BUILD_TYPE + ")";

    public static final String CONFIG_FILE_CONTEXT_PARAM = "pwmConfigPath";
    public static final String CONFIG_FILE_FILENAME = readPwmConstantsBundle("configFilename");

    public static final String PWM_APP_NAME = readPwmConstantsBundle("pwm.appName");
    public static final String PWM_URL_HOME = readPwmConstantsBundle("url.pwm-home");
    public static final String PWM_URL_CLOUD = readPwmConstantsBundle("url.pwm-cloud");

    public static final long VERSION_CHECK_FREQUENCEY_MS = Long.parseLong(readPwmConstantsBundle("versionCheckFrequencyMs"));
    public static final long VERSION_CHECK_FAIL_RETRY_MS = Long.parseLong(readPwmConstantsBundle("versionCheckFailRetryMs"));
    public static final long STATISTICS_PUBLISH_FREQUENCY_MS = Long.parseLong(readPwmConstantsBundle("statisticsPublishFrequencyMs"));

    public static final long NEWUSER_PASSWORD_POLICY_CACHE_MS = Long.parseLong(readPwmConstantsBundle("newuserPasswordPolicyCacheMs"));
    public static final int MAX_CONFIG_FILE_CHARS = Integer.parseInt(readPwmConstantsBundle("config.maxFileChars"));

    public static final Locale DEFAULT_LOCALE = new Locale(readPwmConstantsBundle("locale.defaultLocale"));

    public static final String DEFAULT_DATETIME_FORMAT_STR = readPwmConstantsBundle("locale.defaultDateTimeFormat");
    public static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone(readPwmConstantsBundle("locale.defaultTimeZone"));
    public static final DateFormat DEFAULT_DATETIME_FORMAT = new SimpleDateFormat(DEFAULT_DATETIME_FORMAT_STR);
    static {
        DEFAULT_DATETIME_FORMAT.setTimeZone(DEFAULT_TIMEZONE);
    }



    public static final int DEFAULT_WORDLIST_LOADFACTOR = Integer.parseInt(readPwmConstantsBundle("wordlist.loadFactor"));
    public static final int HTTP_PARAMETER_READ_LENGTH = Integer.parseInt(readPwmConstantsBundle("httpParameterMaxReadLength"));
    public static final int HTTP_BODY_READ_LENGTH = Integer.parseInt(readPwmConstantsBundle("httpBodyMaxReadLength"));
    public static final int HTTP_SESSION_VALIDATION_KEY_LENGTH = Integer.parseInt(readPwmConstantsBundle("httpSessionValidationKeyLength"));
    public static final int CONFIG_FILE_SCAN_FREQUENCY = Integer.parseInt(readPwmConstantsBundle("configFileScanFrequency"));
    public static final int CONFIG_BACKUP_ROTATIONS = Integer.parseInt(readPwmConstantsBundle("configFileBackupRotations"));
    public static final int PWMDB_LOGGER_MAX_QUEUE_SIZE = Integer.parseInt(readPwmConstantsBundle("pwmDBLoggerMaxQueueSize"));
    public static final int PWMDB_LOGGER_MAX_DIRTY_BUFFER_MS = Integer.parseInt(readPwmConstantsBundle("pwmDBLoggerMaxDirtyBufferMS"));
    public static final boolean CLEAR_SESSIONS_ON_RESTART = Boolean.parseBoolean(readPwmConstantsBundle("clearSessionsOnRestart"));
    public static final boolean ENABLE_EULA_DISPLAY = Boolean.parseBoolean(readPwmConstantsBundle("enableEulaDisplay"));
    public static final boolean TRIAL_MODE = Boolean.parseBoolean(readBuildInfoBundle("trial.enabled"));
    public static final int TRIAL_MAX_AUTHENTICATIONS = 100;
    public static final int TRIAL_MAX_TOTAL_AUTH = 10000;

    public static final String RECAPTCHA_VALIDATE_URL = readPwmConstantsBundle("recaptchaValidateUrl");

    public static final int USER_COOKIE_MAX_AGE_SECONDS = Integer.parseInt(readPwmConstantsBundle("userCookieMaxAgeSeconds"));

    public static final int SERVER_AJAX_TYPING_CACHE_SIZE = Integer.parseInt(readPwmConstantsBundle("server.ajaxTypingCacheSize"));

    public static final int DATABASE_ACCESSOR_KEY_LENGTH = Integer.parseInt(readPwmConstantsBundle("databaseAccessor.keyLength"));

    public static final long TOKEN_REMOVAL_DELAY_MS = Long.parseLong(readPwmConstantsBundle("token.removalDelayMS"));
    public static final int TOKEN_PURGE_BATCH_SIZE = Integer.parseInt(readPwmConstantsBundle("token.purgeBatchSize"));
    public static final int TOKEN_MAX_UNIQUE_CREATE_ATTEMPTS = Integer.parseInt(readPwmConstantsBundle("token.maxUniqueCreateAttempts"));

    public static final int PASSWORD_UPDATE_CYCLE_DELAY_MS = Integer.parseInt(readPwmConstantsBundle("passwordUpdateCycleDelayMS"));
    public static final int PASSWORD_UPDATE_INITIAL_DELAY_MS = Integer.parseInt(readPwmConstantsBundle("passwordUpdateInitialDelayMS"));

    public static final String TOKEN_KEY_PWD_CHG_DATE = "lastPwdChange";
    public static final String UNCONFIGURED_URL_VALUE = "[UNCONFIGURED_URL]";
    public static final float JAVA_MINIMUM_VERSION = (float)1.6;

    public static final String HTTP_HEADER_BASIC_AUTH = readPwmConstantsBundle("httpHeaderAuthorization");
    public static final String HTTP_BASIC_AUTH_PREFIX = readPwmConstantsBundle("httpHeaderAuthorizationBasic");
    public static final String HTTP_HEADER_X_FORWARDED_FOR = readPwmConstantsBundle("httpHeaderXForwardedFor");
    public static final String HTTP_HEADER_REST_CLIENT_KEY = readPwmConstantsBundle("httpRestClientKey");
    public static final String HTTP_BASIC_AUTH_DECODE_CHARSET = readPwmConstantsBundle("httpBasicAuthDecodeCharset");

    public static final String DEFAULT_BAD_PASSWORD_ATTEMPT = readPwmConstantsBundle("defaultBadPasswordAttempt");

    public static final String CONTEXT_ATTR_CONTEXT_MANAGER = "ContextManager";
    public static final String CONTEXT_ATTR_RESOURCE_CACHE = "ResourceFileServlet-Cache";
    public static final String SESSION_ATTR_PWM_SESSION = "PwmSession";
    public static final String REQUEST_ATTR_ORIGINAL_URI = "OriginalUri";

    public static final String REQUEST_ATTR_SHOW_LOCALE = "pwm.showLocale";
    public static final String REQUEST_ATTR_SHOW_IDLE = "pwm.showIdle";
    public static final String REQUEST_ATTR_HIDE_THEME = "pwm.hideTheme";

    public static final String DEFAULT_BUILD_CHECKSUM_FILENAME = "BuildChecksum.properties";

    public static final String LOG_REMOVED_VALUE_REPLACEMENT = readPwmConstantsBundle("log.removedValue");

    public static final String URL_JSP_LOGIN = "WEB-INF/jsp/login.jsp";
    public static final String URL_JSP_LOGIN_PW_ONLY = "WEB-INF/jsp/login-passwordonly.jsp";
    public static final String URL_JSP_LOGOUT = "WEB-INF/jsp/logout.jsp";
    public static final String URL_JSP_SUCCESS = "WEB-INF/jsp/success.jsp";
    public static final String URL_JSP_ERROR = "WEB-INF/jsp/error.jsp";
    public static final String URL_JSP_INIT = "WEB-INF/jsp/init.jsp";
    public static final String URL_JSP_PASSWORD_CHANGE = "WEB-INF/jsp/changepassword.jsp";
    public static final String URL_JSP_PASSWORD_FORM = "WEB-INF/jsp/changepassword-form.jsp";
    public static final String URL_JSP_PASSWORD_CHANGE_WAIT = "WEB-INF/jsp/changepassword-wait.jsp";
    public static final String URL_JSP_PASSWORD_AGREEMENT = "WEB-INF/jsp/changepassword-agreement.jsp";
    public static final String URL_JSP_SETUP_RESPONSES = "WEB-INF/jsp/setupresponses.jsp";
    public static final String URL_JSP_SETUP_HELPDESK_RESPONSES = "WEB-INF/jsp/setupresponses-helpdesk.jsp";
    public static final String URL_JSP_SETUP_OTP_SECRET_EXISTING = "WEB-INF/jsp/setupotpsecret-existing.jsp";
    public static final String URL_JSP_SETUP_OTP_SECRET = "WEB-INF/jsp/setupotpsecret.jsp";
    public static final String URL_JSP_SETUP_OTP_SECRET_TEST = "WEB-INF/jsp/setupotpsecret-test.jsp";
    public static final String URL_JSP_CONFIRM_RESPONSES = "WEB-INF/jsp/setupresponses-confirm.jsp";
    public static final String URL_JSP_RECOVER_PASSWORD_SEARCH = "WEB-INF/jsp/forgottenpassword-search.jsp";
    public static final String URL_JSP_RECOVER_PASSWORD_RESPONSES = "WEB-INF/jsp/forgottenpassword-responses.jsp";
    public static final String URL_JSP_RECOVER_PASSWORD_CHOICE = "WEB-INF/jsp/forgottenpassword-choice.jsp";
    public static final String URL_JSP_RECOVER_PASSWORD_ENTER_CODE = "WEB-INF/jsp/forgottenpassword-entercode.jsp";
    public static final String URL_JSP_FORGOTTEN_USERNAME = "WEB-INF/jsp/forgottenusername-search.jsp";
    public static final String URL_JSP_ACTIVATE_USER = "WEB-INF/jsp/activateuser.jsp";
    public static final String URL_JSP_ACTIVATE_USER_AGREEMENT = "WEB-INF/jsp/activateuser-agreement.jsp";
    public static final String URL_JSP_ACTIVATE_USER_ENTER_CODE = "WEB-INF/jsp/activateuser-entercode.jsp";
    public static final String URL_JSP_UPDATE_ATTRIBUTES = "WEB-INF/jsp/updateprofile.jsp";
    public static final String URL_JSP_UPDATE_ATTRIBUTES_AGREEMENT = "WEB-INF/jsp/updateprofile-agreement.jsp";
    public static final String URL_JSP_UPDATE_ATTRIBUTES_CONFIRM = "WEB-INF/jsp/updateprofile-confirm.jsp";
    public static final String URL_JSP_NEW_USER = "WEB-INF/jsp/newuser.jsp";
    public static final String URL_JSP_NEW_USER_ENTER_CODE = "WEB-INF/jsp/newuser-entercode.jsp";
    public static final String URL_JSP_NEW_USER_WAIT = "WEB-INF/jsp/newuser-wait.jsp";
    public static final String URL_JSP_NEW_USER_AGREEMENT = "WEB-INF/jsp/newuser-agreement.jsp";
    public static final String URL_JSP_GUEST_REGISTRATION = "WEB-INF/jsp/guest-create.jsp";
    public static final String URL_JSP_GUEST_UPDATE = "WEB-INF/jsp/guest-update.jsp";
    public static final String URL_JSP_GUEST_UPDATE_SEARCH = "WEB-INF/jsp/guest-search.jsp";
    public static final String URL_JSP_SHORTCUT = "WEB-INF/jsp/shortcut.jsp";
    public static final String URL_JSP_PASSWORD_WARN = "private/passwordwarn.jsp";
    public static final String URL_JSP_CAPTCHA = "WEB-INF/jsp/captcha.jsp";
    public static final String URL_JSP_PEOPLE_SEARCH = "WEB-INF/jsp/peoplesearch.jsp";
    public static final String URL_JSP_PEOPLE_SEARCH_DETAIL = "WEB-INF/jsp/peoplesearch-detail.jsp";
    public static final String URL_JSP_CONFIG_MANAGER_EDITOR = "WEB-INF/jsp/configeditor.jsp";
    public static final String URL_JSP_CONFIG_MANAGER_EDITOR_SETTINGS = "fragment/configeditor-settings.jsp";
    public static final String URL_JSP_CONFIG_MANAGER_EDITOR_LOCALEBUNDLE = "fragment/configeditor-localeBundle.jsp";
    public static final String URL_JSP_CONFIG_MANAGER_LOGVIEW = "WEB-INF/jsp/logview.jsp";
    public static final String URL_JSP_CONFIG_MANAGER_MODE_CONFIGURATION = "WEB-INF/jsp/configmanager.jsp";
    public static final String URL_JSP_CONFIG_MANAGER_LOGIN = "WEB-INF/jsp/configmanager-login.jsp";
    public static final String URL_JSP_CONFIG_GUIDE = "WEB-INF/jsp/configguide-%1%.jsp";
    public static final String URL_JSP_HELPDESK_SEARCH = "WEB-INF/jsp/helpdesk.jsp";
    public static final String URL_JSP_HELPDESK_DETAIL = "WEB-INF/jsp/helpdesk-detail.jsp";

    public static final String URL_SERVLET_LOGIN = "Login";
    public static final String URL_SERVLET_LOGOUT = "Logout";
    public static final String URL_SERVLET_CHANGE_PASSWORD = "ChangePassword";
    public static final String URL_SERVLET_UPDATE_PROFILE = "UpdateProfile";
    public static final String URL_SERVLET_SETUP_RESPONSES = "SetupResponses";
    public static final String URL_SERVLET_SETUP_OTP_SECRET = "SetupOtpSecret";
    public static final String URL_SERVLET_RECOVER_PASSWORD = "ForgottenPassword";
    public static final String URL_SERVLET_NEW_USER = "NewUser";
    public static final String URL_SERVLET_GUEST_REGISTRATION = "GuestRegistration";
    public static final String URL_SERVLET_GUEST_UPDATE = "GuestUpdate";
    public static final String URL_SERVLET_CAPTCHA = "Captcha";
    public static final String URL_SERVLET_COMMAND = "CommandServlet";
    public static final String URL_SERVLET_CONFIG_MANAGER = "ConfigManager";
    public static final String URL_SERVLET_CONFIG_GUIDE = "ConfigGuide";

    public static final String PARAM_ACTION_REQUEST = "processAction";
    public static final String PARAM_VERIFICATION_KEY = "session_verification_key";
    public static final String PARAM_RESPONSE_PREFIX = "PwmResponse_R_";
    public static final String PARAM_QUESTION_PREFIX = "PwmResponse_Q_";
    public static final String PARAM_FORM_ID = "pwmFormID";
    public static final String PARAM_OTP_TOKEN = "otpToken";
    public static final String PARAM_TOKEN = readPwmConstantsBundle("paramName.token");

    public static final String VALUE_REPLACEMENT_USERNAME = "%USERNAME%";
    public static final String EMAIL_REGEX_MATCH = readPwmConstantsBundle("emailRegexMatch");

    // don't worry.  look over there.
    public static final String[] X_AMB_HEADER = new String[]{
            "bonjour!",
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
    };
    
    public final static int TOTP_PAST_INTERVALS = 1;    // Allows one older TOTP token - compensate for clock out of sync
    public final static int TOTP_FUTURE_INTERVALS = 1;  // Allows one newer TOTP token - compensate for clock out of sync
    public final static int TOTP_INTERVAL = 30;         // 30 second interval
    public final static int OTP_TOKEN_LENGTH = 6;
    public final static int OTP_RECOVERY_TOKEN_LENGTH = 8;
    public final static int OTP_RECOVERY_TOKEN_COUNT = 5;

    private static String readPwmConstantsBundle(final String key) {
        return  ResourceBundle.getBundle(PwmConstants.class.getName()).getString(key);
    }

    private static String readBuildInfoBundle(final String key) {
        return  ResourceBundle.getBundle("password.pwm.BuildInformation").getString(key);
    }

// -------------------------- ENUMERATIONS --------------------------


    public static enum EDITABLE_LOCALE_BUNDLES {
        DISPLAY(Display.class),
        ERRORS(password.pwm.i18n.Error.class),
        MESSAGE(Message.class),
        ;

        private final Class theClass;

        EDITABLE_LOCALE_BUNDLES(final Class theClass) {
            this.theClass = theClass;
        }

        public Class getTheClass() {
            return theClass;
        }
    }
}

