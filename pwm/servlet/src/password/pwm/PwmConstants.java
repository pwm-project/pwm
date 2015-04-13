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

package password.pwm;

import org.apache.commons.csv.CSVFormat;
import password.pwm.bean.SessionLabel;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
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
    public static final String BUILD_TIME           = readBuildInfoBundle("build.time",SimpleDateFormat.getDateTimeInstance().format(new Date()));
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

    public static final String CHAI_API_VERSION = com.novell.ldapchai.ChaiConstant.CHAI_API_VERSION + " (" + com.novell.ldapchai.ChaiConstant.CHAI_API_BUILD_INFO + ")";

    public static final String DEFAULT_CONFIG_FILE_FILENAME = readPwmConstantsBundle("defaultConfigFilename");

    public static final String PWM_APP_NAME = readPwmConstantsBundle("pwm.appName");
    public static final String PWM_URL_HOME = readPwmConstantsBundle("url.pwm-home");
    public static final String PWM_URL_CLOUD = readPwmConstantsBundle("url.pwm-cloud");

    public static final String PWM_APP_NAME_VERSION = PWM_APP_NAME + " " + SERVLET_VERSION;

    public static final long VERSION_CHECK_FREQUENCEY_MS = Long.parseLong(readPwmConstantsBundle("versionCheckFrequencyMs"));
    public static final long VERSION_CHECK_FAIL_RETRY_MS = Long.parseLong(readPwmConstantsBundle("versionCheckFailRetryMs"));
    public static final long STATISTICS_PUBLISH_FREQUENCY_MS = Long.parseLong(readPwmConstantsBundle("statisticsPublishFrequencyMs"));

    public static final Locale DEFAULT_LOCALE = new Locale(readPwmConstantsBundle("locale.defaultLocale"));
    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF8");

    public static final CSVFormat DEFAULT_CSV_FORMAT = CSVFormat.DEFAULT;

    public static final String DEFAULT_DATETIME_FORMAT_STR = readPwmConstantsBundle("locale.defaultDateTimeFormat");
    public static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone(readPwmConstantsBundle("locale.defaultTimeZone"));
    public static final DateFormat DEFAULT_DATETIME_FORMAT = new SimpleDateFormat(DEFAULT_DATETIME_FORMAT_STR);
    static {
        DEFAULT_DATETIME_FORMAT.setTimeZone(DEFAULT_TIMEZONE);
    }

    public static final int DEFAULT_WORDLIST_LOADFACTOR = Integer.parseInt(readPwmConstantsBundle("wordlist.loadFactor"));
    public static final int LOCALDB_LOGGER_MAX_QUEUE_SIZE = Integer.parseInt(readPwmConstantsBundle("pwmDBLoggerMaxQueueSize"));
    public static final int LOCALDB_LOGGER_MAX_DIRTY_BUFFER_MS = Integer.parseInt(readPwmConstantsBundle("pwmDBLoggerMaxDirtyBufferMS"));
    public static final boolean ENABLE_EULA_DISPLAY = Boolean.parseBoolean(readPwmConstantsBundle("enableEulaDisplay"));
    public static final boolean TRIAL_MODE = Boolean.parseBoolean(readPwmConstantsBundle("trial"));
    public static final int TRIAL_MAX_AUTHENTICATIONS = 100;
    public static final int TRIAL_MAX_TOTAL_AUTH = 10000;

    private static final String SESSION_LABEL_SESSION_ID = "#";
    public static final SessionLabel REPORTING_SESSION_LABEL = new SessionLabel(SESSION_LABEL_SESSION_ID ,null,"reporting",null,null);
    public static final SessionLabel HEALTH_SESSION_LABEL = new SessionLabel(SESSION_LABEL_SESSION_ID ,null,"health",null,null);
    public static final SessionLabel CLI_SESSION_LABEL= new SessionLabel(SESSION_LABEL_SESSION_ID ,null,"cli",null,null);

    public static final int DATABASE_ACCESSOR_KEY_LENGTH = Integer.parseInt(readPwmConstantsBundle("databaseAccessor.keyLength"));

    public static final String LDAP_AD_PASSWORD_POLICY_CONTROL_ASN = "1.2.840.113556.1.4.2066";
    public static final String PROFILE_ID_ALL = "all";

    public static final String TOKEN_KEY_PWD_CHG_DATE = "_lastPwdChange";
    public static final float JAVA_MINIMUM_VERSION = (float)1.6;

    public static final String HTTP_BASIC_AUTH_PREFIX = readPwmConstantsBundle("httpHeaderAuthorizationBasic");
    public static final String HTTP_HEADER_X_FORWARDED_FOR = readPwmConstantsBundle("httpHeaderXForwardedFor");
    public static final String HTTP_HEADER_REST_CLIENT_KEY = readPwmConstantsBundle("httpRestClientKey");

    public static final String DEFAULT_BAD_PASSWORD_ATTEMPT = readPwmConstantsBundle("defaultBadPasswordAttempt");

    public static final String CONTEXT_ATTR_CONTEXT_MANAGER = "ContextManager";
    public static final String CONTEXT_ATTR_RESOURCE_CACHE = "ResourceFileServlet-Cache";
    public static final String CONTEXT_ATTR_RESOURCE_HIT_AVG = "ResourceFileServlet-HitAvg";

    public static final String SESSION_ATTR_PWM_SESSION = "PwmSession";
    public static final String SESSION_ATTR_CONTEXT_GUID = "ContextInstanceGUID";

    public static enum REQUEST_ATTR {
        PwmErrorInfo,
        PwmRequest,
        OriginalUri,
        AgreementText,
        CompleteText,
        AvailableAuthMethods,
        ConfigurationSummaryOutput,
        PageTitle,

        FormConfiguration,
        FormReadOnly,
        FormShowPasswordFields,
        FormData,

        ConfigFilename,
        ConfigLastModified,
        ConfigHasPassword,
        ApplicationPath,

        CaptchaClientUrl,
        CaptchaIframeUrl,
        CaptchaPublicKey,

        ForgottenPasswordChallengeSet,
        ForgottenPasswordOptionalPageView
    }


    public static final String DOWNLOAD_FILENAME_STATISTICS_CSV = "Statistics.csv";
    public static final String DOWNLOAD_FILENAME_USER_REPORT_SUMMARY_CSV = "UserReportSummary.csv";
    public static final String DOWNLOAD_FILENAME_USER_REPORT_RECORDS_CSV = "UserReportRecords.csv";
    public static final String DOWNLOAD_FILENAME_AUDIT_RECORDS_CSV = "AuditRecords.csv";

    public static final String DEFAULT_BUILD_CHECKSUM_FILENAME = "BuildChecksum.properties";

    public static final String LOG_REMOVED_VALUE_REPLACEMENT = readPwmConstantsBundle("log.removedValue");

    public static enum JSP_URL {

        INIT("init.jsp"),
        ERROR("error.jsp"),
        SUCCESS("success.jsp"),
        APP_UNAVAILABLE("application-unavailable.jsp"),
        ADMIN_DASHBOARD("admin-dashboard.jsp"),
        ADMIN_ANALYSIS("admin-analysis.jsp"),
        ADMIN_ACTIVITY("admin-activity.jsp"),
        ADMIN_TOKEN_LOOKUP("admin-tokenlookup.jsp"),
        ADMIN_LOGVIEW_WINDOW("admin-logview-window.jsp"),
        ADMIN_LOGVIEW("admin-logview.jsp"),
        ADMIN_URLREFERENCE("admin-urlreference.jsp"),
        ACTIVATE_USER("activateuser.jsp"),
        ACTIVATE_USER_AGREEMENT("activateuser-agreement.jsp"),
        ACTIVATE_USER_ENTER_CODE("activateuser-entercode.jsp"),
        LOGIN("login.jsp"),
        LOGIN_PW_ONLY("login-passwordonly.jsp"),
        LOGOUT("logout.jsp"),
        PASSWORD_CHANGE("changepassword.jsp"),
        PASSWORD_FORM("changepassword-form.jsp"),
        PASSWORD_CHANGE_WAIT("changepassword-wait.jsp"),
        PASSWORD_AGREEMENT("changepassword-agreement.jsp"),
        PASSWORD_COMPLETE("changepassword-complete.jsp"),
        PASSWORD_WARN("changepassword-warn.jsp"),
        RECOVER_PASSWORD_SEARCH("forgottenpassword-search.jsp"),
        RECOVER_PASSWORD_RESPONSES("forgottenpassword-responses.jsp"),
        RECOVER_PASSWORD_ATTRIBUTES("forgottenpassword-attributes.jsp"),
        RECOVER_PASSWORD_ACTION_CHOICE("forgottenpassword-actionchoice.jsp"),
        RECOVER_PASSWORD_METHOD_CHOICE("forgottenpassword-method.jsp"),
        RECOVER_PASSWORD_TOKEN_CHOICE("forgottenpassword-tokenchoice.jsp"),
        RECOVER_PASSWORD_ENTER_TOKEN("forgottenpassword-entertoken.jsp"),
        RECOVER_PASSWORD_ENTER_OTP("forgottenpassword-enterotp.jsp"),
        SETUP_RESPONSES("setupresponses.jsp"),
        SETUP_RESPONSES_CONFIRM("setupresponses-confirm.jsp"),
        SETUP_RESPONSES_HELPDESK("setupresponses-helpdesk.jsp"),
        SETUP_RESPONSES_EXISTING("setupresponses-existing.jsp"),
        SETUP_OTP_SECRET_EXISTING("setupotpsecret-existing.jsp"),
        SETUP_OTP_SECRET("setupotpsecret.jsp"),
        SETUP_OTP_SECRET_TEST("setupotpsecret-test.jsp"),
        SETUP_OTP_SECRET_SUCCESS("setupotpsecret-success.jsp"),
        FORGOTTEN_USERNAME("forgottenusername-search.jsp"),
        UPDATE_ATTRIBUTES("updateprofile.jsp"),
        UPDATE_ATTRIBUTES_AGREEMENT("updateprofile-agreement.jsp"),
        UPDATE_ATTRIBUTES_CONFIRM("updateprofile-confirm.jsp"),
        NEW_USER("newuser.jsp"),
        NEW_USER_ENTER_CODE("newuser-entercode.jsp"),
        NEW_USER_WAIT("newuser-wait.jsp"),
        NEW_USER_PROFILE_CHOICE("newuser-profilechoice.jsp"),
        NEW_USER_AGREEMENT("newuser-agreement.jsp"),
        GUEST_REGISTRATION("guest-create.jsp"),
        GUEST_UPDATE("guest-update.jsp"),
        GUEST_UPDATE_SEARCH("guest-search.jsp"),
        SHORTCUT("shortcut.jsp"),
        CAPTCHA("captcha.jsp"),
        PEOPLE_SEARCH("peoplesearch.jsp"),
        CONFIG_MANAGER_EDITOR("configeditor.jsp"),
        CONFIG_MANAGER_EDITOR_SUMMARY("configmanager-summary.jsp"),
        CONFIG_MANAGER_MODE_CONFIGURATION("configmanager.jsp"),
        CONFIG_MANAGER_LOGIN("configmanager-login.jsp"),
        HELPDESK_SEARCH("helpdesk.jsp"),
        HELPDESK_DETAIL("helpdesk-detail.jsp"),

        ;

        private String path;
        private static final String JSP_ROOT_URL = "/WEB-INF/jsp/";

        private JSP_URL(String path) {
            this.path = path;
        }

        public String getPath() {
            return JSP_ROOT_URL + path;
        }
    }

    public static final String URL_JSP_CONFIG_GUIDE = "WEB-INF/jsp/configguide-%1%.jsp";

    public static final String URL_SERVLET_LOGIN = "Login";
    public static final String URL_SERVLET_OAUTH_CONSUMER = "oauth";
    public static final String URL_SERVLET_LOGOUT = "Logout";
    public static final String URL_SERVLET_CHANGE_PASSWORD = "ChangePassword";
    public static final String URL_SERVLET_UPDATE_PROFILE = "UpdateProfile";
    public static final String URL_SERVLET_SETUP_RESPONSES = "SetupResponses";
    public static final String URL_SERVLET_SETUP_OTP_SECRET = "SetupOtp";
    public static final String URL_SERVLET_RECOVER_PASSWORD = "ForgottenPassword";
    public static final String URL_SERVLET_RECOVER_USERNAME = "ForgottenUsername";
    public static final String URL_SERVLET_NEW_USER = "NewUser";
    public static final String URL_SERVLET_USER_ACTIVATION = "ActivateUser";
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
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_CONTEXT = "context";
    public static final String PARAM_LDAP_PROFILE = "ldapProfile";
    public static final String PARAM_SKIP_CAPTCHA = "skipCaptcha";

    public static final String COOKIE_PERSISTENT_CONFIG_LOGIN = "persistentConfigLogin";

    public static final String VALUE_REPLACEMENT_USERNAME = "%USERNAME%";

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
            "passwords are like underwear, changing underwear regularly is a good thing.", //menno
            "daisy, daisy, give me your password do...",
            "it's a wholesome can of software goodness" // thx krowten

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


    public enum HttpHeader {
        Accept("Accept"),
        Connection("Connection"),
        Content_Type("Content-Type"),
        Content_Encoding("Content-Encoding"),
        Location("Location"),
        ContentSecurityPolicy("Content-Security-Policy"),
        Server("Server"),
        Cache_Control("Cache-Control"),
        WWW_Authenticate("WWW-Authenticate"),
        ContentDisposition("content-disposition"),
        ContentTransferEncoding("Content-Transfer-Encoding"),
        Accept_Encoding("Accept-Encoding"),
        Authorization("Authorization"),

        XFrameOptions("X-Frame-Options"),
        XContentTypeOptions("X-Content-Type-Options"),
        XXSSProtection("X-XSS-Protection"),

        XAmb("X-" + PwmConstants.PWM_APP_NAME + "-Amb"),
        XVersion("X-" + PwmConstants.PWM_APP_NAME + "-Version"),
        XInstance("X-" + PwmConstants.PWM_APP_NAME + "-Instance"),
        XSessionID("X-" + PwmConstants.PWM_APP_NAME + "-SessionID"),
        XNoise("X-" + PwmConstants.PWM_APP_NAME + "-Noise"),

        ;

        private final String httpName;

        HttpHeader(String httpName)
        {
            this.httpName = httpName;
        }

        public String getHttpName()
        {
            return httpName;
        }
    }

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

        ContentTypeValue(String headerValue) {
            this.headerValue = headerValue;
        }

        public String getHeaderValue() {
            return headerValue;
        }
    }

    public enum AcceptValue {
        json("application/json"),
        ;

        private String headerValue;

        AcceptValue(String headerValue) {
            this.headerValue = headerValue;
        }

        public String getHeaderValue() {
            return headerValue;
        }
    }
}

