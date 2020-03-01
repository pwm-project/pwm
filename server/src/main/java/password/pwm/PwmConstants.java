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

package password.pwm;

import com.novell.ldapchai.ChaiConstant;
import org.apache.commons.csv.CSVFormat;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Constant values used throughout the servlet.
 *
 * @author Jason D. Rivard
 */
public abstract class PwmConstants
{
    public static final Map<String, String> BUILD_MANIFEST = readBuildManifest();

    public static final String BUILD_TIME = BUILD_MANIFEST.getOrDefault( "Implementation-Build-Timestamp", "n/a" );
    public static final String BUILD_NUMBER = BUILD_MANIFEST.getOrDefault( "Implementation-Build", "0" );
    public static final String BUILD_REVISION = BUILD_MANIFEST.getOrDefault( "Implementation-Revision", "0" );
    public static final String BUILD_JAVA_VENDOR = BUILD_MANIFEST.getOrDefault( "Implementation-Build-Java-Vendor", "0" );
    public static final String BUILD_JAVA_VERSION = BUILD_MANIFEST.getOrDefault( "Implementation-Build-Java-Version", "0" );
    public static final String BUILD_VERSION = BUILD_MANIFEST.getOrDefault( "Implementation-Version", "0" );

    private static final String MISSING_VERSION_STRING = readPwmConstantsBundle( "missingVersionString" );
    public static final String SERVLET_VERSION;

    static
    {
        final String servletVersion = "v" + BUILD_VERSION
                        + " b" + BUILD_NUMBER
                        + " r" + BUILD_REVISION;

        SERVLET_VERSION = servletVersion.isEmpty()
                ? MISSING_VERSION_STRING
                : servletVersion;
    }

    public static final String CHAI_API_VERSION = com.novell.ldapchai.ChaiConstant.CHAI_API_VERSION;

    public static final String DEFAULT_CONFIG_FILE_FILENAME = readPwmConstantsBundle( "defaultConfigFilename" );
    public static final String DEFAULT_PROPERTIES_CONFIG_FILE_FILENAME = readPwmConstantsBundle( "defaultPropertiesConfigFilename" );

    public static final String PWM_APP_NAME = readPwmConstantsBundle( "pwm.appName" );
    public static final String PWM_VENDOR_NAME = readPwmConstantsBundle( "pwm.vendorName" );
    public static final String PWM_URL_HOME = readPwmConstantsBundle( "url.pwm-home" );

    public static final String PWM_APP_NAME_VERSION = PWM_APP_NAME + " " + SERVLET_VERSION;

    public static final String CONFIGMANAGER_INTRUDER_USERNAME = "ConfigurationManagerLogin";

    public static final Locale DEFAULT_LOCALE = new Locale( readPwmConstantsBundle( "locale.defaultLocale" ) );
    public static final Charset DEFAULT_CHARSET = Charset.forName( "UTF8" );
    public static final List<String> HIGHLIGHT_LOCALES = StringUtil.splitAndTrim( readPwmConstantsBundle( "locale.highlightList" ), "," );

    public static final CSVFormat DEFAULT_CSV_FORMAT = CSVFormat.DEFAULT;

    public static final String DEFAULT_DATETIME_FORMAT_STR = readPwmConstantsBundle( "locale.defaultDateTimeFormat" );
    public static final TimeZone DEFAULT_TIMEZONE = TimeZone.getTimeZone( readPwmConstantsBundle( "locale.defaultTimeZone" ) );

    public static final String APPLICATION_PATH_INFO_FILE = readPwmConstantsBundle( "applicationPathInfoFile" );

    public static final boolean ENABLE_EULA_DISPLAY = Boolean.parseBoolean( readPwmConstantsBundle( "enableEulaDisplay" ) );
    public static final boolean TRIAL_MODE = Boolean.parseBoolean( readPwmConstantsBundle( "trial" ) );
    public static final int TRIAL_MAX_AUTHENTICATIONS = 100;
    public static final int TRIAL_MAX_TOTAL_AUTH = 10000;

    public static final int XML_OUTPUT_LINE_WRAP_LENGTH = 120;

    public static final String LDAP_AD_PASSWORD_POLICY_CONTROL_ASN = "1.2.840.113556.1.4.2066";
    public static final String PROFILE_ID_ALL = "all";
    public static final String PROFILE_ID_DEFAULT = "default";

    public static final String TOKEN_KEY_PWD_CHG_DATE = "_lastPwdChange";

    public static final String HTTP_BASIC_AUTH_PREFIX = readPwmConstantsBundle( "httpHeaderAuthorizationBasic" );

    public static final String DEFAULT_BAD_PASSWORD_ATTEMPT = readPwmConstantsBundle( "defaultBadPasswordAttempt" );

    public static final String CONTEXT_ATTR_CONTEXT_MANAGER = "ContextManager";

    public static final String SESSION_ATTR_PWM_SESSION = "PwmSession";
    public static final String SESSION_ATTR_BEANS = "SessionBeans";
    public static final String SESSION_ATTR_PWM_APP_NONCE = "PwmApplication-Nonce";

    public static final String REQUEST_ATTR_FORGOTTEN_PW_USERINFO_CACHE = "ForgottenPw-UserInfoCache";
    public static final String REQUEST_ATTR_FORGOTTEN_PW_AVAIL_TOKEN_DEST_CACHE = "ForgottenPw-AvailableTokenDestCache";
    public static final String REQUEST_ATTR_PWM_APPLICATION = "PwmApplication";

    public static final String LOG_REMOVED_VALUE_REPLACEMENT = readPwmConstantsBundle( "log.removedValue" );

    public static final Collection<Locale> INCLUDED_LOCALES;

    static
    {
        final List<Locale> localeList = new ArrayList<>();
        final String inputString = readPwmConstantsBundle( "includedLocales" );
        final List<String> inputList = JsonUtil.deserializeStringList( inputString );
        for ( final String localeKey : inputList )
        {
            localeList.add( new Locale( localeKey ) );
        }
        INCLUDED_LOCALES = Collections.unmodifiableCollection( localeList );
    }

    public static final String URL_JSP_CONFIG_GUIDE = "WEB-INF/jsp/configguide-%1%.jsp";

    public static final String URL_PREFIX_PRIVATE = "/private";
    public static final String URL_PREFIX_PUBLIC = "/public";
    public static final String URL_PREFIX_REST = "/rest";


    public static final String PARAM_ACTION_REQUEST = "processAction";
    public static final String PARAM_RESET_TYPE = "resetType";
    public static final String PARAM_ACTION_STATE = "actionState";
    public static final String PARAM_RESPONSE_PREFIX = "PwmResponse_R_";
    public static final String PARAM_QUESTION_PREFIX = "PwmResponse_Q_";
    public static final String PARAM_FORM_ID = "pwmFormID";
    public static final String PARAM_SESSION_STATE_INFO = "ssi";

    public static final String PARAM_OTP_TOKEN = "otpToken";
    public static final String PARAM_TOKEN = readPwmConstantsBundle( "paramName.token" );
    public static final String PARAM_USERNAME = "username";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_CONTEXT = "context";
    public static final String PARAM_LDAP_PROFILE = "ldapProfile";
    public static final String PARAM_SKIP_CAPTCHA = "skipCaptcha";
    public static final String PARAM_POST_LOGIN_URL = "posturl";
    public static final String PARAM_FILE_UPLOAD = "fileUpload";
    public static final String PARAM_RECOVERY_OAUTH_RESULT = "roauthResults";
    public static final String PARAM_SIGNED_FORM = "signedForm";
    public static final String PARAM_USERKEY = "userKey";
    public static final String PARAM_METHOD_CHOICE = "methodChoice";


    public static final String COOKIE_PERSISTENT_CONFIG_LOGIN = "CONFIG-AUTH";

    public static final String VALUE_REPLACEMENT_USERNAME = "%USERNAME%";

    public static final String RESOURCE_FILE_EULA_TXT = "eula.txt";
    public static final String RESOURCE_FILE_PRIVACY_TXT = "privacy.txt";
    public static final String RESOURCE_FILE_WELCOME_TXT = "welcome.txt";

    // don't worry.  look over there.
    public static final List<String> X_AMB_HEADER = Collections.unmodifiableList( Arrays.asList(
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

            // thx wk
            "whatever happened to speech wreck a nation technology?",
            "The Mummy's password is in crypted",
            "The zombie's password is expired",
            "Chuck Yeager's password is in plane text",

            "Password schmassword, I can't even remember my user name...",
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
            "ben makes password software go woooo",

            //nick helm
            "I needed a password eight characters long so I picked Snow White and the Seven Dwarves.",
            "Roses are #FF0000 , Violets are #0000FF, All your password are belongs to us.",
            "I changed my password to \"incorrect\", so whenever i forget, it will tell me \"your password is incorrect\".",

            //menno
            "passwords are like underwear, changing underwear regularly is a good thing.",
            "daisy, daisy, give me your password do...",

            // thx krowten
            "it's a wholesome can of software goodness",

            "this password is an memorial of the richard d. kiel memorial abend"
    ) );


    private static String readPwmConstantsBundle( final String key )
    {
        return ResourceBundle.getBundle( PwmConstants.class.getName() ).getString( key );
    }

    private static Map<String, String> readBuildManifest( )
    {
        final String interestedArchiveNonce = "F84576985F0A176014F751736F7C79B6D9BED842FC48377404FE24A36BF6C2AA";
        final String manifestKeyName = "Archive-UID";
        final String manifestFileName = "META-INF/MANIFEST.MF";

        final Map<String, String> returnMap = new TreeMap<>();
        try
        {
            final Enumeration<URL> resources = ChaiConstant.class.getClassLoader().getResources( manifestFileName );
            while ( resources.hasMoreElements() )
            {
                try ( InputStream inputStream = resources.nextElement().openStream() )
                {
                    final Manifest manifest = new Manifest( inputStream );
                    final Attributes attributes = manifest.getMainAttributes();
                    final String archiveNonce = attributes.getValue( manifestKeyName );
                    try
                    {
                        if ( interestedArchiveNonce.equals( archiveNonce ) )
                        {
                            for ( final Map.Entry<Object, Object> entry : attributes.entrySet() )
                            {
                                final Object keyObject = entry.getKey();
                                final Object valueObject = entry.getValue();
                                if ( keyObject != null && valueObject != null )
                                {
                                    returnMap.put( keyObject.toString(), valueObject.toString() );
                                }
                            }
                        }
                    }
                    catch ( final Throwable t )
                    {
                        System.out.println( t );
                    }
                }
            }
        }
        catch ( final Throwable t )
        {
            System.out.println( t );
        }

        return Collections.unmodifiableMap( returnMap );
    }

    public enum AcceptValue
    {
        json( "application/json" ),
        html( "text/html" ),;

        private String headerValue;

        AcceptValue( final String headerValue )
        {
            this.headerValue = headerValue;
        }

        public String getHeaderValue( )
        {
            return headerValue;
        }
    }
}

