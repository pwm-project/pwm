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

package password.pwm.util;

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiFactory;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Answer;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.*;
import com.novell.ldapchai.util.SearchHelper;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpProtocolParams;
import password.pwm.*;
import password.pwm.bean.EmailItemBean;
import password.pwm.bean.SessionStateBean;
import password.pwm.bean.SmsItemBean;
import password.pwm.bean.UserInfoBean;
import password.pwm.config.Configuration;
import password.pwm.config.FormConfiguration;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.servlet.ResourceFileServlet;
import password.pwm.util.operations.UserDataReader;
import password.pwm.util.stats.Statistic;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.X509TrustManager;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.lang.reflect.Method;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A collection of static methods used throughout PWM
 *
 * @author Jason D. Rivard
 */
public class Helper {
// ------------------------------ FIELDS ------------------------------

    private static final PwmLogger LOGGER = PwmLogger.getLogger(Helper.class);

    // -------------------------- STATIC METHODS --------------------------

    private Helper() {
    }

    public static ChaiProvider createChaiProvider(
            final Configuration config,
            final List<String> ldapURLs,
            final String userDN,
            final String userPassword,
            final int idleTimeoutMs
    )
            throws ChaiUnavailableException {
        final ChaiConfiguration chaiConfig = createChaiConfiguration(config, ldapURLs, userDN, userPassword, idleTimeoutMs);
        LOGGER.trace("creating new chai provider using config of " + chaiConfig.toString());
        return ChaiProviderFactory.createProvider(chaiConfig);
    }

    public static ChaiProvider createChaiProvider(
            final Configuration config,
            final String userDN,
            final String userPassword,
            final int idleTimeoutMs
    )
            throws ChaiUnavailableException
    {
        final List<String> ldapURLs = config.readSettingAsStringArray(PwmSetting.LDAP_SERVER_URLS);
        final ChaiConfiguration chaiConfig = createChaiConfiguration(config, ldapURLs, userDN, userPassword, idleTimeoutMs);
        LOGGER.trace("creating new chai provider using config of " + chaiConfig.toString());
        return ChaiProviderFactory.createProvider(chaiConfig);
    }

    public static ChaiConfiguration createChaiConfiguration(
            final Configuration config,
            final List<String> ldapURLs,
            final String userDN,
            final String userPassword,
            final int idleTimeoutMs
    )
    {

        final ChaiConfiguration chaiConfig = new ChaiConfiguration(ldapURLs, userDN, userPassword);

        chaiConfig.setSetting(ChaiSetting.PROMISCUOUS_SSL, Boolean.toString(config.readSettingAsBoolean(PwmSetting.LDAP_PROMISCUOUS_SSL)));
        chaiConfig.setSetting(ChaiSetting.EDIRECTORY_ENABLE_NMAS, Boolean.toString(config.readSettingAsBoolean(PwmSetting.EDIRECTORY_ENABLE_NMAS)));

        chaiConfig.setSetting(ChaiSetting.CR_CHAI_STORAGE_ATTRIBUTE, config.readSettingAsString(PwmSetting.CHALLENGE_USER_ATTRIBUTE));
        chaiConfig.setSetting(ChaiSetting.CR_ALLOW_DUPLICATE_RESPONSES, Boolean.toString(config.readSettingAsBoolean(PwmSetting.CHALLENGE_ALLOW_DUPLICATE_RESPONSES)));
        chaiConfig.setSetting(ChaiSetting.CR_CASE_INSENSITIVE, Boolean.toString(config.readSettingAsBoolean(PwmSetting.CHALLENGE_CASE_INSENSITIVE)));
        chaiConfig.setSetting(ChaiSetting.CR_CHAI_SALT_COUNT, Integer.toString(PwmConstants.RESPONSES_HASH_LOOP_COUNT));

        chaiConfig.setSetting(ChaiSetting.CR_DEFAULT_FORMAT_TYPE, Answer.FormatType.SHA1_SALT.toString());
        final String storageMethodString = config.readSettingAsString(PwmSetting.CHALLENGE_STORAGE_HASHED);
        try {
            final Answer.FormatType formatType = Answer.FormatType.valueOf(storageMethodString);
            chaiConfig.setSetting(ChaiSetting.CR_DEFAULT_FORMAT_TYPE, formatType.toString());
        } catch (Exception e) {
            LOGGER.error("unknown CR storage format type '" + storageMethodString + "' ");
        }

        final X509Certificate[] ldapServerCerts = config.readSettingAsCertificate(PwmSetting.LDAP_SERVER_CERTS);
        if (ldapServerCerts != null && ldapServerCerts.length > 0) {
            final X509TrustManager tm = new X509Utils.PwmTrustManager(ldapServerCerts);
            chaiConfig.setTrustManager(new X509TrustManager[]{tm});
        }

        // if possible, set the ldap timeout.
        if (idleTimeoutMs > 0) {
            chaiConfig.setSetting(ChaiSetting.WATCHDOG_ENABLE, "true");
            chaiConfig.setSetting(ChaiSetting.WATCHDOG_IDLE_TIMEOUT, Long.toString(idleTimeoutMs));
            chaiConfig.setSetting(ChaiSetting.WATCHDOG_CHECK_FREQUENCY, Long.toString(60 * 1000));
        } else {
            chaiConfig.setSetting(ChaiSetting.WATCHDOG_ENABLE, "false");
        }

        // write out any configured values;
        final List<String> rawValues = config.readSettingAsStringArray(PwmSetting.LDAP_CHAI_SETTINGS);
        final Map<String, String> configuredSettings = Configuration.convertStringListToNameValuePair(rawValues, "=");
        for (final String key : configuredSettings.keySet()) {
            final ChaiSetting theSetting = ChaiSetting.forKey(key);
            if (theSetting == null) {
                LOGGER.error("ignoring unknown chai setting '" + key + "'");
            } else {
                chaiConfig.setSetting(theSetting, configuredSettings.get(key));
            }
        }

        // set ldap referrals
        chaiConfig.setSetting(ChaiSetting.LDAP_FOLLOW_REFERRALS,String.valueOf(config.readSettingAsBoolean(PwmSetting.LDAP_FOLLOW_REFERRALS)));

        // enable wire trace;
        if (config.readSettingAsBoolean(PwmSetting.LDAP_ENABLE_WIRE_TRACE)) {
            chaiConfig.setSetting(ChaiSetting.WIRETRACE_ENABLE, "true");
        }

        return chaiConfig;
    }

    public static String readLdapUserIDValue(
            final PwmApplication pwmApplication,
            final ChaiUser theUser
    )
            throws ChaiUnavailableException, ChaiOperationException
    {
        final Configuration config = pwmApplication.getConfig();
        final String uIDattr = config.getUsernameAttribute();
        return theUser.readStringAttribute(uIDattr);
    }


    public static String readLdapGuidValue(
            final PwmApplication pwmApplication,
            final String userDN
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {

        final Configuration config = pwmApplication.getConfig();
        final ChaiProvider proxyChaiProvider = pwmApplication.getProxyChaiProvider();
        final String GUIDattributeName = config.readSettingAsString(PwmSetting.LDAP_GUID_ATTRIBUTE);
        if ("DN".equalsIgnoreCase(GUIDattributeName)) {
            return userDN;
        }

        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, proxyChaiProvider);
        if ("VENDORGUID".equals(GUIDattributeName)) {
            try {
                final String guidValue = theUser.readGUID();
                if (guidValue != null && guidValue.length() > 1) {
                    LOGGER.trace("read VENDORGUID value for user " + userDN + ": " + guidValue);
                } else {
                    LOGGER.trace("unable to find a VENDORGUID value for user " + userDN);
                }
                return guidValue;
            } catch (Exception e) {
                LOGGER.warn("unexpected error while reading vendor GUID value for user " + userDN + ", error: " + e.getMessage());
                return null;
            }
        }

        try {
            final String guidValue = theUser.readStringAttribute(GUIDattributeName);
            if (guidValue != null && guidValue.length() > 0) {
                return guidValue;
            }

            if (!config.readSettingAsBoolean(PwmSetting.LDAP_GUID_AUTO_ADD)) {
                LOGGER.warn("user " + userDN + " does not have a valid GUID");
                return null;
            }
        } catch (ChaiOperationException e) {
            LOGGER.warn("unexpected error while reading attribute GUID value for user " + userDN + " from '" + GUIDattributeName + "', error: " + e.getMessage());
            return null;
        }

        LOGGER.trace("assigning new GUID to user " + userDN);

        final List<String> baseContexts = config.readSettingAsStringArray(PwmSetting.LDAP_CONTEXTLESS_ROOT);
        int attempts = 0;
        while (attempts < 10) {
            // generate a guid
            final String newGUID;
            {
                final StringBuilder sb = new StringBuilder();
                sb.append(Long.toHexString(System.currentTimeMillis()).toUpperCase());
                while (sb.length() < 12) {
                    sb.insert(0, "0");
                }
                sb.insert(0, PwmRandom.getInstance().alphaNumericString(20).toUpperCase());
                newGUID = sb.toString();
            }


            try {
                // check if it is unique
                final SearchHelper searchHelper = new SearchHelper(ChaiProvider.SEARCH_SCOPE.SUBTREE);
                searchHelper.setFilter(GUIDattributeName, newGUID);
                searchHelper.setMaxResults(1);
                searchHelper.setAttributes(GUIDattributeName);
                for (final String baseContext : baseContexts) {
                    final Map<String, Map<String,String>> result = proxyChaiProvider.search(baseContext, searchHelper);
                    if (result.isEmpty()) {
                        try {
                            // write it to the directory
                            proxyChaiProvider.writeStringAttribute(userDN, GUIDattributeName, Collections.singleton(newGUID), false);
                            LOGGER.info("added GUID value '" + newGUID + "' to user " + userDN);
                            return newGUID;
                        } catch (ChaiOperationException e) {
                            LOGGER.warn("error writing GUID value to user attribute " + GUIDattributeName + " : " + e.getMessage() + ", cannot write GUID value to user " + userDN);
                            return null;
                        }
                    }
                }
            } catch (ChaiOperationException e) {
                LOGGER.warn("unexpected error while searching GUID attribute " + GUIDattributeName + " for uniqueness: " + e.getMessage() + ", cannot write GUID value to user " + userDN);
            }
            attempts++;
        }
        return null;
    }

    /**
     * Append auxClasses    configured in the PWM configuration to the ldap user object.
     *
     * @param userDN     userDN userDN of the user to add to
     * @param pwmSession Current pwmSession, used for logging
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException
     *          if the ldap server is unavailable
     */
    public static void addConfiguredUserObjectClass(
            final String userDN,
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        final Set<String> newObjClasses = new HashSet<String>(pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.AUTO_ADD_OBJECT_CLASSES));
        if (newObjClasses.isEmpty()) {
            return;
        }
        final ChaiUser theUser = ChaiFactory.createChaiUser(userDN, pwmApplication.getProxyChaiProvider());
        addUserObjectClass(theUser, newObjClasses, pwmSession);
    }

    private static void addUserObjectClass(final ChaiUser theUser, final Set<String> newObjClasses, final PwmSession pwmSession)
            throws ChaiUnavailableException {
        String auxClass = null;
        try {
            final Set<String> existingObjClasses = theUser.readMultiStringAttribute(ChaiConstant.ATTR_LDAP_OBJECTCLASS);
            newObjClasses.removeAll(existingObjClasses);

            for (final String newObjClass : newObjClasses) {
                auxClass = newObjClass;
                theUser.addAttribute(ChaiConstant.ATTR_LDAP_OBJECTCLASS, auxClass);
                LOGGER.info(pwmSession, "added objectclass '" + auxClass + "' to user " + theUser.getEntryDN());
            }
        } catch (ChaiOperationException e) {
            final StringBuilder errorMsg = new StringBuilder();

            errorMsg.append("error adding objectclass '").append(auxClass).append("' to user ");
            errorMsg.append(theUser.getEntryDN());
            errorMsg.append(": ");
            errorMsg.append(e.toString());

            LOGGER.error(pwmSession, errorMsg.toString());
        }
    }

    public static String md5sum(final String input)
            throws IOException {
        if (input == null || input.length() < 1) {
            return null;
        }
        return md5sum(new ByteArrayInputStream(input.getBytes()));
    }

    public static String md5sum(final File theFile)
            throws IOException {
        return md5sum(new FileInputStream(theFile));
    }

    public static String md5sum(final InputStream is)
            throws IOException {
        return checksum(is, "MD5");
    }

    public static String checksum(final InputStream is, String algorithmName)
            throws IOException {

        final InputStream bis = is instanceof BufferedInputStream ? is : new BufferedInputStream(is);

        final MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance(algorithmName);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }

        final byte[] buffer = new byte[1024];
        int length;
        while (true) {
            length = bis.read(buffer, 0, buffer.length);
            if (length == -1) {
                break;
            }
            messageDigest.update(buffer, 0, length);
        }
        bis.close();

        final byte[] bytes = messageDigest.digest();

        return byteArrayToHexString(bytes);
    }

    /**
     * Convert a byte[] array to readable string format. This makes the "hex" readable
     *
     * @param in byte[] buffer to convert to string format
     * @return result String buffer in String format
     */
    public static String byteArrayToHexString(final byte in[]) {
        final String pseudo[] = {"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "A", "B", "C", "D", "E", "F"};

        if (in == null || in.length <= 0) {
            return "";
        }

        final StringBuilder out = new StringBuilder(in.length * 2);

        for (final byte b : in) {
            byte ch = (byte) (b & 0xF0);    // strip off high nibble
            ch = (byte) (ch >>> 4);         // shift the bits down
            ch = (byte) (ch & 0x0F);        // must do this is high order bit is on!
            out.append(pseudo[(int) ch]);   // convert the nibble to a String Character
            ch = (byte) (b & 0x0F);         // strip off low nibble
            out.append(pseudo[(int) ch]);   // convert the nibble to a String Character
        }

        return out.toString();
    }

    /**
     * Pause the calling thread the specified amount of time.
     *
     * @param sleepTimeMS - a time duration in milliseconds
     * @return time actually spent sleeping
     */
    public static long pause(final long sleepTimeMS) {
        final long startTime = System.currentTimeMillis();
        do {
            try {
                final long sleepTime = sleepTimeMS - (System.currentTimeMillis() - startTime);
                Thread.sleep(sleepTime > 0 ? sleepTime : 5);
            } catch (InterruptedException e) {
                //who cares
            }
        } while ((System.currentTimeMillis() - startTime) < sleepTimeMS);

        return System.currentTimeMillis() - startTime;
    }

    public static void invokeExternalChangeMethods(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication,
            final String userDN,
            final String oldPassword,
            final String newPassword) throws PwmUnrecoverableException {
        final List<String> externalMethods = pwmApplication.getConfig().readSettingAsStringArray(PwmSetting.EXTERNAL_CHANGE_METHODS);

        // process any configured external change password methods configured.
        for (final String classNameString : externalMethods) {
            if (classNameString != null && classNameString.length() > 0) {
                try {
                    // load up the class and get an instance.
                    final Class<?> theClass = Class.forName(classNameString);
                    final ExternalChangeMethod externalClass = (ExternalChangeMethod) theClass.newInstance();

                    // invoke the passwordChange method;
                    final boolean success = externalClass.passwordChange(pwmApplication, userDN, oldPassword, newPassword);

                    if (success) {
                        LOGGER.info(pwmSession, "externalPasswordMethod '" + classNameString + "' was successfull");
                    } else {
                        LOGGER.warn(pwmSession, "externalPasswordMethod '" + classNameString + "' was not successfull");
                    }
                } catch (ClassCastException e) {
                    LOGGER.warn(pwmSession, "configured external class " + classNameString + " is not an instance of " + ExternalChangeMethod.class.getName());
                } catch (ClassNotFoundException e) {
                    LOGGER.warn(pwmSession, "unable to load configured external class: " + classNameString + " " + e.getMessage() + "; perhaps the class is not in the classpath?");
                } catch (IllegalAccessException e) {
                    LOGGER.warn(pwmSession, "unable to load configured external class: " + classNameString + " " + e.getMessage());
                } catch (InstantiationException e) {
                    LOGGER.warn(pwmSession, "unable to load configured external class: " + classNameString + " " + e.getMessage());
                }
            }
        }
    }

    public static List<Integer> invokeExternalJudgeMethods(
            final Configuration config,
            //final PwmSession pwmSession,
            final String password)  {
        final List<String> externalMethods = config.readSettingAsStringArray(PwmSetting.EXTERNAL_JUDGE_METHODS);
        final List<Integer> returnList = new ArrayList<Integer>();

        // process any configured external change password methods configured.
        for (final String classNameString : externalMethods) {
            if (classNameString != null && classNameString.length() > 0) {
                try {
                    // load up the class and get an instance.
                    final Class<?> theClass = Class.forName(classNameString);
                    final ExternalJudgeMethod externalClass = (ExternalJudgeMethod) theClass.newInstance();

                    // invoke the passwordChange method;
                    final int result = externalClass.judgePassword(config, password);
                    LOGGER.trace("externalJudgeMethod '" + classNameString + "' returned a value of " + result);
                    returnList.add(result);
                } catch (ClassCastException e) {
                    LOGGER.error("configured external class " + classNameString + " is not an instance of " + ExternalChangeMethod.class.getName());
                } catch (ClassNotFoundException e) {
                    LOGGER.error("unable to load configured external class: " + classNameString + " " + e.getMessage() + "; perhaps the class is not in the classpath?");
                } catch (IllegalAccessException e) {
                    LOGGER.error("unable to load configured external class: " + classNameString + " " + e.getMessage());
                } catch (InstantiationException e) {
                    LOGGER.error("unable to load configured external class: " + classNameString + " " + e.getMessage());
                }
            }
        }

        return returnList;
    }

    public static boolean testEmailAddress(final String address) {
        final Pattern pattern = Pattern.compile(PwmConstants.EMAIL_REGEX_MATCH);
        final Matcher matcher = pattern.matcher(address);
        return matcher.matches();
    }

    public static boolean testUserMatchQueryString(
            final ChaiProvider provider,
            final String objectDN,
            final String queryString
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        if (objectDN == null || objectDN.length() < 1) {
            return true;
        }

        if (queryString == null || queryString.length() < 1) {
            return true;
        }

        try {
            final Map<String, Map<String,String>> results = provider.search(objectDN, queryString, Collections.<String>emptySet(), ChaiProvider.SEARCH_SCOPE.SUBTREE);

            if (results == null || results.size() != 1) {
                return false;
            }

            final String returnedDN = Helper.trimString(results.keySet().iterator().next(), ",");

            if (returnedDN.equals(objectDN)) {
                return true;
            }
        } catch (ChaiOperationException e) {
            LOGGER.error("error testing match query string: " + queryString, e);
        }

        return false;
    }

    /**
     * Strips specified characters of the beginning and end of a string.  Similar to
     * {@link String#trim()}, except the caller can specify an arbitrary character instead
     * of just whitespace chars.
     *
     * @param str   String to operate on
     * @param chars A String containing characters to remove from beginning/end
     * @return the (possibly) modifed str value
     */
    public static String trimString(
            final String str,
            final String chars
    ) {
        if (chars == null || chars.length() < 1) {
            return str;
        }

        final StringBuilder sb = new StringBuilder(str);

        for (int i = 0; i < chars.length(); i++) {
            if (sb.charAt(0) == chars.charAt(i)) {
                sb.delete(0, 1);
            }

            if (sb.charAt(sb.length() - 1) == chars.charAt(i)) {
                sb.delete(sb.length() - 2, sb.length() - 1);
            }
        }

        return sb.toString();
    }




    /**
     * Writes a Map of form values to ldap onto the supplied user object.
     * The map key must be a string of attribute names.
     * <p/>
     * Any ldap operation exceptions are not reported (but logged).
     *
     * @param pwmSession       for looking up session info
     * @param theUser          User to write to
     * @param formValues       A map with {@link password.pwm.config.FormConfiguration} keys and String values.
     * @throws ChaiUnavailableException if the directory is unavailable
     * @throws PwmOperationalException if their is an unexpected ldap problem
     */
    public static void writeFormValuesToLdap(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final Map<FormConfiguration,String> formValues,
            final boolean expandPwmMacros
    )
            throws ChaiUnavailableException, PwmOperationalException
    {
        final Map<String,String> tempMap = new HashMap<String,String>();

        for (final FormConfiguration formItem : formValues.keySet()) {
            if (!formItem.isReadonly()) {
                tempMap.put(formItem.getName(),formValues.get(formItem));
            }
        }

        writeMapToLdap(pwmApplication, theUser, tempMap, pwmSession.getUserInfoBean(), expandPwmMacros);
    }

    /**
     * Writes a Map of values to ldap onto the supplied user object.
     * The map key must be a string of attribute names.
     * <p/>
     * Any ldap operation exceptions are not reported (but logged).
     *
     * @param theUser          User to write to
     * @param valueMap       A map with String keys and String values.
     * @throws ChaiUnavailableException if the directory is unavailable
     * @throws PwmOperationalException if their is an unexpected ldap problem
     */
    public static void writeMapToLdap(
            final PwmApplication pwmApplication,
            final ChaiUser theUser,
            final Map<String,String> valueMap,
            final UserInfoBean userInfoBean,
            final boolean expandPwmMacros
    )
            throws PwmOperationalException, ChaiUnavailableException
    {
        final Map<String,String> currentValues;
        try {
            currentValues = theUser.readStringAttributes(valueMap.keySet());
        } catch (ChaiOperationException e) {
            final String errorMsg = "error reading existing values on user " + theUser.getEntryDN() + " prior to replacing values, error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
            final PwmOperationalException newException = new PwmOperationalException(errorInformation);
            newException.initCause(e);
            throw newException;
        }

        // krowten made me do this shit
        for (final String attrName : valueMap.keySet()) {
            String attrValue = valueMap.get(attrName) != null ? valueMap.get(attrName) : "";
            if (expandPwmMacros) {
                attrValue = MacroMachine.expandMacros(attrValue, pwmApplication, userInfoBean, null);
            }
            if (!attrValue.equals(currentValues.get(attrName))) {
                if (attrValue.length() > 0) {
                    try {
                        theUser.writeStringAttribute(attrName, attrValue);
                        LOGGER.info("set attribute on user " + theUser.getEntryDN() + " (" + attrName + "=" + attrValue + ")");
                    } catch (ChaiOperationException e) {
                        final String errorMsg = "error setting '" + attrName + "' attribute on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                        final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                        final PwmOperationalException newException = new PwmOperationalException(errorInformation);
                        newException.initCause(e);
                        throw newException;
                    }
                } else {
                    if (currentValues.get(attrName) != null && currentValues.get(attrName).length() > 0) {
                        try {
                            theUser.deleteAttribute(attrName, null);
                            LOGGER.info("deleted attribute value on user " + theUser.getEntryDN() + " (" + attrName + ")");
                        } catch (ChaiOperationException e) {
                            final String errorMsg = "error removing '" + attrName + "' attribute value on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                            final PwmOperationalException newException = new PwmOperationalException(errorInformation);
                            newException.initCause(e);
                            throw newException;
                        }
                    }
                }
            } else {
                LOGGER.debug("skipping attribute modify for attribute '" + attrName + "', no change in value");
            }
        }
    }

    public static String binaryArrayToHex(final byte[] buf) {
        final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();
        final char[] chars = new char[2 * buf.length];
        for (int i = 0; i < buf.length; ++i) {
            chars[2 * i] = HEX_CHARS[(buf[i] & 0xF0) >>> 4];
            chars[2 * i + 1] = HEX_CHARS[buf[i] & 0x0F];
        }
        return new String(chars);
    }

    public static long getFileDirectorySize(final File dir) {
        long size = 0;
        try {
            if (dir.isFile()) {
                size = dir.length();
            } else {
                final File[] subFiles = dir.listFiles();

                for (final File file : subFiles) {
                    if (file.isFile()) {
                        size += file.length();
                    } else {
                        size += getFileDirectorySize(file);
                    }

                }
            }
        } catch (NullPointerException e) {
            // file was deleted before file size could be read
        }

        return size;
    }

    public static String formatDiskSize(final long diskSize) {
        final float COUNT = 1000;
        if (diskSize < 1) {
            return "n/a";
        }

        if (diskSize == 0) {
            return "0";
        }

        final NumberFormat nf = NumberFormat.getInstance();
        nf.setMaximumFractionDigits(2);

        if (diskSize > COUNT * COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT / COUNT));
            sb.append(" GB");
            return sb.toString();
        }

        if (diskSize > COUNT * COUNT) {
            final StringBuilder sb = new StringBuilder();
            sb.append(nf.format(diskSize / COUNT / COUNT));
            sb.append(" MB");
            return sb.toString();
        }

        return NumberFormat.getInstance().format(diskSize) + " bytes";
    }


    public static File figureFilepath(final String filename, final File suggestedPath)
    {
        if (filename == null || filename.length() < 1) {
            return null;
        }

        if ((new File(filename)).isAbsolute()) {
            return new File(filename);
        }

        return new File(suggestedPath + File.separator + filename);
    }

    public static Boolean fileExists(final String filename) {
        if (filename != null) {
            File file = new File(filename);
            return file.exists() && file.isFile();
        }
        return false;
    }

    public static Boolean directoryExists(final String dirname) {
        if (dirname != null) {
            File directory = new File(dirname);
            return directory.exists() && directory.isDirectory();
        }
        return false;
    }

    public static String readFileAsString(final File filePath, final long maxLength, final String charset)
            throws IOException {
        final StringBuilder fileData = new StringBuilder();

        final BufferedReader reader = new BufferedReader(
                charset == null ?
                        new InputStreamReader(new FileInputStream(filePath)) :
                        new InputStreamReader(new FileInputStream(filePath), "UTF8"));

        char[] buf = new char[1024];
        int numRead;
        int charsRead = 0;
        while ((numRead = reader.read(buf)) != -1 && (charsRead < maxLength)) {
            final String readData = String.valueOf(buf, 0, numRead);
            fileData.append(readData);
            buf = new char[1024];
            charsRead += numRead;
        }
        reader.close();
        return fileData.toString();
    }

    public static void writeFileAsString(final File filePath, final String output, final String charset)
            throws IOException {
        final OutputStreamWriter osw =
                charset == null ?
                        new OutputStreamWriter(new FileOutputStream(filePath, false)) :
                        new OutputStreamWriter(new FileOutputStream(filePath, false), charset);

        osw.write(output);
        osw.flush();
        osw.close();
    }

    public static String replaceAllPatterns(final String input, final Properties fields) {
        String output = input;
        Enumeration names = fields.propertyNames();
        while (names.hasMoreElements()) {
            final String key = (String) names.nextElement();
            final String fieldName = "%"+key+"%";
            final String fieldValue = fields.getProperty(key);
            output = output.replaceAll(fieldName, fieldValue);
        }
        return output;
    }

    public static long diskSpaceRemaining(final File file) {
        try {
            final Method getFreeSpaceMethod = File.class.getMethod("getFreeSpace");
            final Object rawResult = getFreeSpaceMethod.invoke(file);
            return (Long) rawResult;
        } catch (NoSuchMethodException e) {
            /* no error, pre java 1.6 doesn't have this method */
        } catch (Exception e) {
            LOGGER.debug("error reading file space remaining for " + file.toString() + ",: " + e.getMessage());
        }
        return -1;
    }

    public static HttpClient getHttpClient(final Configuration configuration)
    {
        final DefaultHttpClient httpClient = new DefaultHttpClient();

        final String strValue = configuration.readSettingAsString(PwmSetting.HTTP_PROXY_URL);
        if (strValue != null && strValue.length() > 0) {
            final URI proxyURI = URI.create(strValue);

            final String host = proxyURI.getHost();
            final int port = proxyURI.getPort();
            final HttpHost proxy = new HttpHost(host,port);
            httpClient.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);

            final String username = proxyURI.getUserInfo();
            if (username != null && username.length() > 0) {
                final String password = (username.contains(":")) ? username.split(":")[1] : "";
                final UsernamePasswordCredentials passwordCredentials = new UsernamePasswordCredentials(username,password);
                httpClient.getCredentialsProvider().setCredentials (new AuthScope(host, port),passwordCredentials);
            }
        }
        final String userAgent = "PWM " + PwmConstants.SERVLET_VERSION;
        httpClient.getParams().setParameter(HttpProtocolParams.USER_AGENT, userAgent);
        return httpClient;
    }

    static public String buildPwmFormID(final SessionStateBean ssBean) {
        return ssBean.getSessionVerificationKey() + Long.toString(ssBean.getRequestCounter(),36);
    }

    public static String resolveStringKeyLocaleMap(Locale desiredLocale, final Map<String,String> inputMap) {
        if (inputMap == null || inputMap.isEmpty()) {
            return null;
        }

        if (desiredLocale == null) {
            desiredLocale = PwmConstants.DEFAULT_LOCALE;
        }

        final Map<Locale,String> localeMap = new LinkedHashMap<Locale, String>();
        for (final String localeStringKey : inputMap.keySet()) {
            localeMap.put(parseLocaleString(localeStringKey),inputMap.get(localeStringKey));
        }

        final Locale selectedLocale = localeResolver(desiredLocale, localeMap.keySet());
        return localeMap.get(selectedLocale);
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

    public static class SimpleTextCrypto {

        public static String encryptValue(final String value, final SecretKey key)
                throws PwmUnrecoverableException
        {
            return encryptValue(value, key, false);
        }

        public static String encryptValue(final String value, final SecretKey key, final boolean urlSafe)
                throws PwmUnrecoverableException
        {
            try {
                if (value == null || value.length() < 1) {
                    return "";
                }

                final Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, key, cipher.getParameters());
                final byte[] encrypted = cipher.doFinal(value.getBytes());
                return urlSafe ?  Base64Util.encodeBytes(encrypted, Base64Util.URL_SAFE | Base64Util.GZIP) : Base64Util.encodeBytes(encrypted);
            } catch (Exception e) {
                final String errorMsg = "unexpected error performing simple crypt operation: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                LOGGER.error(errorInformation.toDebugStr());
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        public static String decryptValue(final String value, final SecretKey key)
                throws PwmUnrecoverableException
        {
            return decryptValue(value, key, false);
        }

        public static String decryptValue(final String value, final SecretKey key, final boolean urlSafe)
                throws PwmUnrecoverableException
        {
            try {
                if (value == null || value.length() < 1) {
                    return "";
                }

                final byte[] decoded = urlSafe ? Base64Util.decode(value, Base64Util.URL_SAFE | Base64Util.GZIP): Base64Util.decode(value);
                final Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, key);
                final byte[] decrypted = cipher.doFinal(decoded);
                return new String(decrypted);
            } catch (Exception e) {
                final String errorMsg = "unexpected error performing simple decrypt operation: " + e.getMessage();
                final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_UNKNOWN, errorMsg);
                throw new PwmUnrecoverableException(errorInformation);
            }
        }

        public static SecretKey makeKey(final String text)
                throws NoSuchAlgorithmException, UnsupportedEncodingException {
            final MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(text.getBytes("iso-8859-1"), 0, text.length());
            final byte[] key = new byte[16];
            System.arraycopy(md.digest(), 0, key, 0, 16);
            return new SecretKeySpec(key, "AES");
        }
    }

    public static String calcEtagUserString(final PwmApplication pwmApplication, final PwmSession pwmSession) {
        if (pwmApplication == null || pwmSession == null) {
            return "";
        }

        final SessionStateBean sessionStateBean = pwmSession.getSessionStateBean();
        if (sessionStateBean == null) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        sb.append(Long.toString(pwmApplication.getStartupTime().getTime(),36));
        sb.append("-");
        sb.append(String.valueOf(sessionStateBean.getLocale()));
        if (sessionStateBean.isAuthenticated()) {
            sb.append("-");
            try {
                sb.append(md5sum(pwmSession.getUserInfoBean().getUserDN()));
            } catch (IOException e) {
                //nothing doin
            }
        }
        return sb.toString();
    }

    public static String figureForwardURL(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession,
            final HttpServletRequest req
    ) {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        String redirectURL = ssBean.getForwardURL();
        if (redirectURL == null || redirectURL.length() < 1) {
            redirectURL = pwmApplication.getConfig().readSettingAsString(PwmSetting.URL_FORWARD);
        }

        if (redirectURL == null || redirectURL.length() < 1) {
            redirectURL = req.getContextPath();
        }

        return redirectURL;
    }

    public static String figureLogoutURL(
            final PwmApplication pwmApplication,
            final PwmSession pwmSession
    ) {
        final SessionStateBean ssBean = pwmSession.getSessionStateBean();
        return ssBean.getLogoutURL() == null ? pwmApplication.getConfig().readSettingAsString(PwmSetting.URL_LOGOUT) : ssBean.getLogoutURL();
    }

    public static int figureLdapConnectionCount(final PwmApplication pwmApplication, final ContextManager contextManager) {
        int counter = 0;
        try {
            if (pwmApplication.getProxyChaiProvider().isConnected()) {
                counter++;
            }

            for (final PwmSession loopSession : contextManager.getPwmSessions()) {
                if (loopSession != null) {
                    if (loopSession.getSessionManager().hasActiveLdapConnection()) {
                        counter++;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("unexpected error counting ldap connections: " + e.getMessage());
        }
        return counter;
    }

    public static HashMap splitStringToMap(final String input) {
        HashMap result = new HashMap();
        if (input != null) {
            final List<String> items = Arrays.asList(input.split(";"));
            for (final Iterator<String> it = items.iterator(); it.hasNext(); ) {
                String item = it.next();
                String[] parts = item.split(":", 2);
                if (parts.length > 0) {
                    String left = parts[0];
                    String right = null;
                    if (parts.length == 2) {
                        right = parts[1];
                    }
                    result.put(left, right);
                }
            }
        }
        return result;
    }

    public static class TokenSender {
        public static void sendToken(
                final PwmApplication pwmApplication,
                final UserInfoBean userInfoBean,
                final UserDataReader userDataReader,
                final EmailItemBean configuredEmailSetting,
                final PwmSetting.MessageSendMethod tokenSendMethod,
                final String emailAddress,
                final String smsNumber,
                final String smsMessage,
                final String tokenKey
        )
                throws PwmUnrecoverableException, ChaiUnavailableException {
            final Configuration config = pwmApplication.getConfig();
            final boolean success;
            switch (tokenSendMethod) {
                case NONE:
                    // should never get here
                    throw new PwmUnrecoverableException(PwmError.ERROR_UNKNOWN);
                case BOTH:
                    // Send both email and SMS, success if one of both succeeds
                    final boolean suc1 = sendEmailToken(pwmApplication, userInfoBean, userDataReader, configuredEmailSetting, emailAddress, tokenKey);
                    final boolean suc2 = sendSmsToken(pwmApplication, userInfoBean, userDataReader, smsNumber, smsMessage, tokenKey);
                    success = suc1 || suc2;
                    break;
                case EMAILFIRST:
                    // Send email first, try SMS if email is not available
                    success = sendEmailToken(pwmApplication, userInfoBean, userDataReader, configuredEmailSetting, emailAddress, tokenKey) ||
                            sendSmsToken(pwmApplication, userInfoBean, userDataReader, smsNumber, smsMessage, tokenKey);
                    break;
                case SMSFIRST:
                    // Send SMS first, try email if SMS is not available
                    success = sendSmsToken(pwmApplication, userInfoBean, userDataReader, smsNumber, smsMessage, tokenKey) ||
                            sendEmailToken(pwmApplication, userInfoBean, userDataReader, configuredEmailSetting, emailAddress, tokenKey);
                    break;
                case SMSONLY:
                    // Only try SMS
                    success = sendSmsToken(pwmApplication, userInfoBean, userDataReader, smsNumber, smsMessage, tokenKey);
                    break;
                case EMAILONLY:
                default:
                    // Only try email
                    success = sendEmailToken(pwmApplication, userInfoBean, userDataReader, configuredEmailSetting, emailAddress, tokenKey);
                    break;
            }
            if (!success) {
                throw new PwmUnrecoverableException(new ErrorInformation(PwmError.ERROR_TOKEN_MISSING_CONTACT));
            }
            pwmApplication.getStatisticsManager().incrementValue(Statistic.RECOVERY_TOKENS_SENT);
        }

        private static Boolean sendEmailToken(
                final PwmApplication pwmApplication,
                final UserInfoBean userInfoBean,
                final UserDataReader userDataReader,
                final EmailItemBean configuredEmailSetting,
                final String toAddress,
                final String tokenKey
        )
                throws PwmUnrecoverableException, ChaiUnavailableException
        {
            if (toAddress == null || toAddress.length() < 1) {
                return false;
            }

            pwmApplication.sendEmailUsingQueue(new EmailItemBean(
                    toAddress,
                    configuredEmailSetting.getFrom(),
                    configuredEmailSetting.getSubject(),
                    configuredEmailSetting.getBodyPlain().replace("%TOKEN%", tokenKey),
                    configuredEmailSetting.getBodyHtml().replace("%TOKEN%", tokenKey)
            ), userInfoBean, userDataReader);
            LOGGER.debug("token email added to send queue for " + toAddress);
            return true;
        }

        private static Boolean sendSmsToken(
                final PwmApplication pwmApplication,
                final UserInfoBean userInfoBean,
                final UserDataReader userDataReader,
                final String smsNumber,
                final String smsMessage,
                final String tokenKey
        )
                throws PwmUnrecoverableException, ChaiUnavailableException
        {
            final Configuration config = pwmApplication.getConfig();
            String senderId = config.readSettingAsString(PwmSetting.SMS_SENDER_ID);
            if (senderId == null) { senderId = ""; }

            if (smsNumber == null || smsNumber.length() < 1) {
                return false;
            }

            final String modifiedMessage = smsMessage.replaceAll("%TOKEN%", tokenKey);

            final Integer maxlen = ((Long) config.readSettingAsLong(PwmSetting.SMS_MAX_TEXT_LENGTH)).intValue();
            pwmApplication.sendSmsUsingQueue(new SmsItemBean(smsNumber, senderId, modifiedMessage, maxlen), userInfoBean, userDataReader);
            LOGGER.debug("token SMS added to send queue for " + smsNumber);
            return true;
        }
    }
}
