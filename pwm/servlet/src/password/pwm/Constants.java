/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2010 The PWM Project
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

import java.util.ResourceBundle;

/**
 * Constant values used throughout the servlet.
 *
 * @author Jason D. Rivard
 */
public abstract class Constants {
// ------------------------------ FIELDS ------------------------------

    // ------------------------- PUBLIC CONSTANTS -------------------------
    public static final String BUILD_NUMBER = ResourceBundle.getBundle("password.pwm.BuildInformation").getString("build.number");
    public static final String PWM_VERSION = ResourceBundle.getBundle("password.pwm.BuildInformation").getString("pwm.version");

    public static final String SERVLET_VERSION = "v" + PWM_VERSION + " b" + BUILD_NUMBER;

    public static final int INTRUDER_RETENTION_TIME = 5 * 24 * 60 * 60 * 1000;
    public static final int MAX_EMAIL_QUEUE_SIZE = 1000;
    public static final int MAX_LDAP_IDLE_TIME = 90000;

    public static final String HTTP_HEADER_BASIC_AUTH = "Authorization";
    public static final String HTTP_BASIC_AUTH_PREFIX = "Basic ";
    public static final String HTTP_HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    public static final String HTTP_HEADER_PWM_SHORTCUT = "X-PWM-Shortcut";


    public static final String REQUEST_CONFIG_MAP = "ConfigMap";

    public static final String CONTEXT_ATTR_CONTEXT_MANAGER = "ContextManager";
    public static final String CONTEXT_ATTR_STATUS_BEAN = "StatusBean";

    public static final String SESSION_ATTR_PWM_SESSION = "PwmSession";

    public static final String DEFAULT_LOG4JCONFIG_FILENAME = "log4jconfig.xml";
    public static final String DEFAULT_WORDLIST_DB_DIR = "pwm-db";
    public static final String DEFAULT_BUILD_CHECKSUM_FILENAME = "BuildChecksum.properties";

    public static final String URL_JSP_LOGIN = "jsp/login.jsp";
    public static final String URL_JSP_LOGOUT = "jsp/logout.jsp";
    public static final String URL_JSP_SUCCESS = "jsp/success.jsp";
    public static final String URL_JSP_ERROR = "jsp/error.jsp";
    public static final String URL_JSP_WAIT = "jsp/wait.jsp";
    public static final String URL_JSP_PASSWORD_CHANGE = "jsp/changepassword.jsp";
    public static final String URL_JSP_SETUP_RESPONSES = "jsp/setupresponses.jsp";
    public static final String URL_JSP_CONFIRM_RESPONSES = "jsp/setupresponses-confirm.jsp";
    public static final String URL_JSP_RECOVER_PASSWORD_SEARCH = "jsp/forgottenpassword-search.jsp";
    public static final String URL_JSP_RECOVER_PASSWORD_RESPONSES = "jsp/forgottenpassword-responses.jsp";
    public static final String URL_JSP_RECOVER_PASSWORD_CHOICE = "jsp/forgottenpassword-choice.jsp";
    public static final String URL_JSP_RECOVER_PASSWORD_ENTER_CODE = "jsp/forgottenpassword-entercode.jsp";
    public static final String URL_JSP_ACTIVATE_USER = "jsp/activateuser.jsp";
    public static final String URL_JSP_UPDATE_ATTRIBUTES = "jsp/updateattributes.jsp";
    public static final String URL_JSP_NEW_USER = "jsp/newuser.jsp";
    public static final String URL_JSP_SHORTCUT = "jsp/shortcut.jsp";
    public static final String URL_JSP_PASSWORD_WARN = "jsp/passwordwarn.jsp";
    public static final String URL_JSP_CAPTCHA = "jsp/captcha.jsp";
    public static final String URL_JSP_CONFIG_MANAGER = "jsp/configmanager.jsp";

    public static final String URL_JSP_USER_INFORMATION = "admin/userinformation.jsp";

    public static final String URL_SERVLET_LOGIN = "Login";
    public static final String URL_SERVLET_LOGOUT = "Logout";
    public static final String URL_SERVLET_CHANGE_PASSWORD = "ChangePassword";
    public static final String URL_SERVLET_UPDATE_ATTRIBUTES = "UpdateAttributes";
    public static final String URL_SERVLET_SETUP_RESPONSES = "SetupResponses";
    public static final String URL_SERVLET_RECOVER_PASSWORD = "ForgottenPassword";
    public static final String URL_SERVLET_NEW_USER = "NewUser";
    public static final String URL_SERVLET_CAPTCHA = "Captcha";
    public static final String URL_SERVLET_COMMAND = "CommandServlet";

    public static final String PARAM_ACTION_REQUEST = "processAction";
    public static final String PARAM_VERIFICATIN_KEY = "session_verificiation_key";
    public static final String PARAM_RESPONSE_PREFIX = "PwmResponse_R_";
    public static final String PARAM_QUESTION_PREFIX = "PwmResponse_Q_";

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
            "Chuck Yeager's password is in plane text", // thx wk
            "As Gregor Samsa awoke one morning from uneasy dreams he found himself transformed in his bed into a gigantic password.",
            "NOTICE: This header is protected by the Digital Millennium Copyright Act of 1996.  Reading this header is strictly forbidden."
    };

    public static final int PASSWORD_UPDATE_CYCLE_DELAY = 1000 * 2;  //milliseconds
    public static final int PASSWORD_UPDATE_INITIAL_DELAY = 1000; //milliseconds

// -------------------------- ENUMERATIONS --------------------------

    public static enum CONTEXT_PARAM {
        CONFIG_FILE("pwmConfigPath"),
        WORDLIST_LOAD_FACTOR("wordlistLoadFactor"),
        WORDLIST_CASE_SENSITIVE("wordlistCaseSensitive"),
        PWMDB_LOCATION("pwmDbLocation"),
        PWMDB_IMPLEMENTATION("pwmDbImplementation"),
        PWMDB_INITSTRING("pwmDbInitString"),
        DISALLOWED_INPUTS("disallowedInputs"),
        SHARED_HISTORY_CLEAN_FREQUENCY("globalHistoryCleanFrequency"),
        ALLOW_URL_SESSIONS("allowUrlSessions"),
        LDAP_NAMING_ATTRIBUTE("ldapNamingAttribute"),
        FORCE_BASIC_AUTH("forceBasicAuth"),
        MIN_PASSWORD_SYNC_WAIT_TIME("minimumPasswordSyncWaitTime"),
        INSTANCE_ID("instanceID"),
        AGGRESIVE_URL_PARSING("aggressiveUrlParsing"),
        ;

        private final String key;

        public String getKey()
        {
            return key;
        }

        CONTEXT_PARAM(final String key)
        {
            this.key = key;
        }
    }
}

