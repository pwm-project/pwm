/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.ldap;

import com.novell.ldapchai.ChaiConstant;
import com.novell.ldapchai.ChaiEntry;
import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.cr.Answer;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import com.novell.ldapchai.provider.ChaiSetting;
import com.novell.ldapchai.provider.SearchScope;
import com.novell.ldapchai.util.SearchHelper;
import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchEngine;
import password.pwm.svc.cache.CacheKey;
import password.pwm.svc.cache.CachePolicy;
import password.pwm.svc.stats.Statistic;
import password.pwm.svc.stats.StatisticsManager;
import password.pwm.util.PasswordData;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;
import password.pwm.util.secure.X509Utils;

import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class LdapOperationsHelper
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapOperationsHelper.class );

    public static void addConfiguredUserObjectClass(
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final PwmApplication pwmApplication
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );
        final Set<String> newObjClasses = new HashSet<>( ldapProfile.readSettingAsStringArray( PwmSetting.AUTO_ADD_OBJECT_CLASSES ) );
        if ( newObjClasses.isEmpty() )
        {
            return;
        }
        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider( userIdentity.getLdapProfileID() );
        final ChaiUser theUser = chaiProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );
        addUserObjectClass( sessionLabel, theUser, newObjClasses );
    }

    private static void addUserObjectClass(
            final SessionLabel sessionLabel,
            final ChaiUser theUser,
            final Set<String> newObjClasses
    )
            throws ChaiUnavailableException
    {
        String auxClass = null;
        try
        {
            final Set<String> existingObjClasses = theUser.readMultiStringAttribute( ChaiConstant.ATTR_LDAP_OBJECTCLASS );
            newObjClasses.removeAll( existingObjClasses );

            for ( final String newObjClass : newObjClasses )
            {
                auxClass = newObjClass;
                theUser.addAttribute( ChaiConstant.ATTR_LDAP_OBJECTCLASS, auxClass );
                LOGGER.info( sessionLabel, "added objectclass '" + auxClass + "' to user " + theUser.getEntryDN() );
            }
        }
        catch ( ChaiOperationException e )
        {
            final StringBuilder errorMsg = new StringBuilder();

            errorMsg.append( "error adding objectclass '" ).append( auxClass ).append( "' to user " );
            errorMsg.append( theUser.getEntryDN() );
            errorMsg.append( ": " );
            errorMsg.append( e.toString() );

            LOGGER.error( sessionLabel, errorMsg.toString() );
        }
    }

    public static ChaiProvider openProxyChaiProvider(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final LdapProfile ldapProfile,
            final Configuration config,
            final StatisticsManager statisticsManager
    )
            throws PwmUnrecoverableException
    {
        return openProxyChaiProvider(
                pwmApplication.getLdapConnectionService().getChaiProviderFactory(),
                sessionLabel,
                ldapProfile,
                config,
                statisticsManager
        );
    }

    static ChaiProvider openProxyChaiProvider(
            final ChaiProviderFactory chaiProviderFactory,
            final SessionLabel sessionLabel,
            final LdapProfile ldapProfile,
            final Configuration config,
            final StatisticsManager statisticsManager
    )
            throws PwmUnrecoverableException
    {
        LOGGER.trace( sessionLabel, "opening new ldap proxy connection" );

        final String proxyDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN );
        final PasswordData proxyPW = ldapProfile.readSettingAsPassword( PwmSetting.LDAP_PROXY_USER_PASSWORD );

        try
        {
            return createChaiProvider( chaiProviderFactory, sessionLabel, ldapProfile, config, proxyDN, proxyPW );
        }
        catch ( ChaiUnavailableException e )
        {
            if ( statisticsManager != null )
            {
                statisticsManager.incrementValue( Statistic.LDAP_UNAVAILABLE_COUNT );
            }
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append( "error connecting as proxy user: " );
            final PwmError pwmError = PwmError.forChaiError( e.getErrorCode() );
            if ( pwmError != null && pwmError != PwmError.ERROR_UNKNOWN )
            {
                errorMsg.append( new ErrorInformation( pwmError, e.getMessage() ).toDebugStr() );
            }
            else
            {
                errorMsg.append( e.getMessage() );
            }
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_DIRECTORY_UNAVAILABLE, errorMsg.toString() );
            LOGGER.fatal( sessionLabel, "check ldap proxy settings: " + errorInformation.toDebugStr() );
            throw new PwmUnrecoverableException( errorInformation );
        }
    }


    private static final String NULL_CACHE_GUID = "NULL_CACHE_GUID";

    public static String readLdapGuidValue(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final boolean throwExceptionOnError
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final boolean enableCache = Boolean.parseBoolean( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_CACHE_USER_GUID_ENABLE ) );
        final CacheKey cacheKey = CacheKey.makeCacheKey( LdapOperationsHelper.class, null, "guidValue-" + userIdentity.toDelimitedKey() );

        if ( enableCache )
        {
            final String cachedValue = pwmApplication.getCacheService().get( cacheKey );
            if ( cachedValue != null )
            {
                return NULL_CACHE_GUID.equals( cachedValue )
                        ? null
                        : cachedValue;
            }
        }

        final String existingValue = GUIDHelper.readExistingGuidValue(
                pwmApplication,
                sessionLabel,
                userIdentity,
                throwExceptionOnError
        );

        final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );
        final String guidAttributeName = ldapProfile.readSettingAsString( PwmSetting.LDAP_GUID_ATTRIBUTE );
        if ( StringUtil.isEmpty( existingValue ) )
        {
            if ( !"DN".equalsIgnoreCase( guidAttributeName ) && !"VENDORGUID".equalsIgnoreCase( guidAttributeName ) )
            {
                if ( ldapProfile.readSettingAsBoolean( PwmSetting.LDAP_GUID_AUTO_ADD ) )
                {
                    LOGGER.trace( "assigning new GUID to user " + userIdentity );
                    return GUIDHelper.assignGuidToUser( pwmApplication, sessionLabel, userIdentity, guidAttributeName );
                }
            }
            final String errorMsg = "unable to resolve GUID value for user " + userIdentity.toString();
            GUIDHelper.processError( errorMsg, throwExceptionOnError );
        }

        if ( enableCache )
        {
            final long cacheSeconds = Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_CACHE_USER_GUID_SECONDS ) );
            final CachePolicy cachePolicy = CachePolicy.makePolicyWithExpiration( new TimeDuration( cacheSeconds, TimeUnit.SECONDS ) );
            final String cacheValue = existingValue == null
                    ? NULL_CACHE_GUID
                    : existingValue;
            pwmApplication.getCacheService().put( cacheKey, cachePolicy, cacheValue );
        }

        return existingValue;
    }

    /**
     * Writes a Map of form values to ldap onto the supplied user object.
     * The map key must be a string of attribute names.
     * <p/>
     * Any ldap operation exceptions are not reported (but logged).
     *
     * @param theUser    User to write to
     * @param formValues A map with {@link FormConfiguration} keys and String values.
     * @throws ChaiUnavailableException if the directory is unavailable
     * @throws PwmOperationalException  if their is an unexpected ldap problem
     */
    public static void writeFormValuesToLdap(
            final PwmApplication pwmApplication,
            final MacroMachine macroMachine,
            final ChaiUser theUser,
            final Map<FormConfiguration, String> formValues,
            final boolean expandMacros
    )
            throws ChaiUnavailableException, PwmOperationalException, PwmUnrecoverableException
    {
        final Map<String, String> tempMap = new HashMap<>();

        for ( final Map.Entry<FormConfiguration, String> entry : formValues.entrySet() )
        {
            final FormConfiguration formItem = entry.getKey();
            if ( !formItem.isReadonly() )
            {
                tempMap.put( formItem.getName(), entry.getValue() );
            }
        }

        writeMapToLdap( theUser, tempMap, macroMachine, expandMacros );
    }

    /**
     * Writes a Map of values to ldap onto the supplied user object.
     * The map key must be a string of attribute names.
     * <p/>
     * Any ldap operation exceptions are not reported (but logged).
     *
     * @param theUser  User to write to
     * @param valueMap A map with String keys and String values.
     * @throws ChaiUnavailableException if the directory is unavailable
     * @throws PwmOperationalException  if their is an unexpected ldap problem
     */
    public static void writeMapToLdap(
            final ChaiUser theUser,
            final Map<String, String> valueMap,
            final MacroMachine macroMachine,
            final boolean expandMacros
    )
            throws PwmOperationalException, ChaiUnavailableException
    {
        final Map<String, String> currentValues;
        try
        {
            currentValues = theUser.readStringAttributes( valueMap.keySet() );
        }
        catch ( ChaiOperationException e )
        {
            final String errorMsg = "error reading existing values on user " + theUser.getEntryDN() + " prior to replacing values, error: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
            final PwmOperationalException newException = new PwmOperationalException( errorInformation );
            newException.initCause( e );
            throw newException;
        }

        for ( final Map.Entry<String, String> entry : valueMap.entrySet() )
        {
            final String attrName = entry.getKey();
            final String value = entry.getValue();
            String attrValue = value != null
                    ? value
                    : "";

            if ( expandMacros )
            {
                attrValue = macroMachine.expandMacros( attrValue );
            }

            if ( !attrValue.equals( currentValues.get( attrName ) ) )
            {
                if ( attrValue.length() > 0 )
                {
                    try
                    {
                        theUser.writeStringAttribute( attrName, attrValue );
                        LOGGER.info( "set attribute on user " + theUser.getEntryDN() + " (" + attrName + "=" + attrValue + ")" );
                    }
                    catch ( ChaiOperationException e )
                    {
                        final String errorMsg = "error setting '" + attrName + "' attribute on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                        final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
                        final PwmOperationalException newException = new PwmOperationalException( errorInformation );
                        newException.initCause( e );
                        throw newException;
                    }
                }
                else
                {
                    if ( currentValues.get( attrName ) != null && currentValues.get( attrName ).length() > 0 )
                    {
                        try
                        {
                            theUser.deleteAttribute( attrName, null );
                            LOGGER.info( "deleted attribute value on user " + theUser.getEntryDN() + " (" + attrName + ")" );
                        }
                        catch ( ChaiOperationException e )
                        {
                            final String errorMsg = "error removing '" + attrName + "' attribute value on user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
                            final PwmOperationalException newException = new PwmOperationalException( errorInformation );
                            newException.initCause( e );
                            throw newException;
                        }
                    }
                }
            }
            else
            {
                LOGGER.debug( "skipping attribute modify for attribute '" + attrName + "', no change in value" );
            }
        }
    }

    private static class GUIDHelper
    {
        private static String readExistingGuidValue(
                final PwmApplication pwmApplication,
                final SessionLabel sessionLabel,
                final UserIdentity userIdentity,
                final boolean throwExceptionOnError
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
            final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );
            final String guidAttributeName = ldapProfile.readSettingAsString( PwmSetting.LDAP_GUID_ATTRIBUTE );

            if ( "DN".equalsIgnoreCase( guidAttributeName ) )
            {
                return userIdentity.toDelimitedKey();
            }

            if ( "VENDORGUID".equalsIgnoreCase( guidAttributeName ) )
            {
                try
                {
                    final String guidValue = theUser.readGUID();
                    if ( guidValue != null && guidValue.length() > 1 )
                    {
                        LOGGER.trace( sessionLabel, "read VENDORGUID value for user " + theUser + ": " + guidValue );
                    }
                    else
                    {
                        LOGGER.trace( sessionLabel, "unable to find a VENDORGUID value for user " + theUser.getEntryDN() );
                    }
                    return guidValue;
                }
                catch ( Exception e )
                {
                    final String errorMsg = "error while reading vendor GUID value for user " + theUser.getEntryDN() + ", error: " + e.getMessage();
                    return processError( errorMsg, throwExceptionOnError );
                }
            }

            try
            {
                return theUser.readStringAttribute( guidAttributeName );
            }
            catch ( ChaiOperationException e )
            {
                final String errorMsg = "unexpected error while reading attribute GUID value for user "
                        + userIdentity + " from '" + guidAttributeName + "', error: " + e.getMessage();
                return processError( errorMsg, throwExceptionOnError );
            }
        }

        private static String processError( final String errorMsg, final boolean throwExceptionOnError )
                throws PwmUnrecoverableException
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_MISSING_GUID, errorMsg );
            if ( throwExceptionOnError )
            {
                throw new PwmUnrecoverableException( errorInformation );
            }
            LOGGER.warn( errorMsg );
            return null;
        }

        private static boolean searchForExistingGuidValue(
                final PwmApplication pwmApplication,
                final SessionLabel sessionLabel,
                final String guidValue
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            boolean exists = false;
            for ( final LdapProfile ldapProfile : pwmApplication.getConfig().getLdapProfiles().values() )
            {
                final String guidAttributeName = ldapProfile.readSettingAsString( PwmSetting.LDAP_GUID_ATTRIBUTE );
                if ( !"DN".equalsIgnoreCase( guidAttributeName ) && !"VENDORGUID".equalsIgnoreCase( guidAttributeName ) )
                {
                    try
                    {
                        // check if it is unique
                        final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                                .filter( "(" + guidAttributeName + "=" + guidValue + ")" )
                                .build();

                        final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();
                        final UserIdentity result = userSearchEngine.performSingleUserSearch( searchConfiguration, sessionLabel );
                        exists = result != null;
                    }
                    catch ( PwmOperationalException e )
                    {
                        if ( e.getError() != PwmError.ERROR_CANT_MATCH_USER )
                        {
                            LOGGER.warn( sessionLabel, "error while searching to verify new unique GUID value: " + e.getError() );
                        }
                    }
                }
            }
            return exists;
        }

        private static String assignGuidToUser(
                final PwmApplication pwmApplication,
                final SessionLabel sessionLabel,
                final UserIdentity userIdentity,
                final String guidAttributeName
        )
                throws ChaiUnavailableException, PwmUnrecoverableException
        {
            int attempts = 0;
            String newGuid = null;

            while ( attempts < 10 && newGuid == null )
            {
                attempts++;
                newGuid = generateGuidValue( pwmApplication, sessionLabel );
                if ( searchForExistingGuidValue( pwmApplication, sessionLabel, newGuid ) )
                {
                    newGuid = null;
                }
            }

            if ( newGuid == null )
            {
                throw new PwmUnrecoverableException( new ErrorInformation(
                        PwmError.ERROR_UNKNOWN,
                        "unable to generate unique GUID value for user " + userIdentity )
                );
            }

            addConfiguredUserObjectClass( sessionLabel, userIdentity, pwmApplication );
            try
            {
                // write it to the directory
                final ChaiUser chaiUser = pwmApplication.getProxiedChaiUser( userIdentity );
                chaiUser.writeStringAttribute( guidAttributeName, newGuid );
                LOGGER.info( sessionLabel, "added GUID value '" + newGuid + "' to user " + userIdentity );
                return newGuid;
            }
            catch ( ChaiOperationException e )
            {
                final String errorMsg = "unable to write GUID value to user attribute " + guidAttributeName + " : " + e.getMessage()
                        + ", cannot write GUID value to user " + userIdentity;
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_UNKNOWN, errorMsg );
                LOGGER.error( errorInformation.toDebugStr() );
                throw new PwmUnrecoverableException( errorInformation );
            }
        }

        private static String generateGuidValue(
                final PwmApplication pwmApplication,
                final SessionLabel sessionLabel
        )
                throws PwmUnrecoverableException
        {
            final MacroMachine macroMachine = MacroMachine.forNonUserSpecific( pwmApplication, sessionLabel );
            final String guidPattern = pwmApplication.getConfig().readAppProperty( AppProperty.LDAP_GUID_PATTERN );
            return macroMachine.expandMacros( guidPattern );
        }
    }

    public static ChaiProvider createChaiProvider(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final LdapProfile ldapProfile,
            final Configuration config,
            final String userDN,
            final PasswordData userPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        return createChaiProvider(
                pwmApplication.getLdapConnectionService().getChaiProviderFactory(),
                sessionLabel,
                ldapProfile,
                config,
                userDN,
                userPassword
        );
    }

    public static ChaiProvider createChaiProvider(
            final ChaiProviderFactory chaiProviderFactory,
            final SessionLabel sessionLabel,
            final LdapProfile ldapProfile,
            final Configuration config,
            final String userDN,
            final PasswordData userPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final List<String> ldapURLs = ldapProfile.readSettingAsStringArray( PwmSetting.LDAP_SERVER_URLS );
        final ChaiConfiguration chaiConfig = createChaiConfiguration( config, ldapProfile, ldapURLs, userDN, userPassword );
        LOGGER.trace( sessionLabel, "creating new ldap connection using config: " + chaiConfig.toString() );
        return chaiProviderFactory.newProvider( chaiConfig );
    }

    public static ChaiProvider createChaiProvider(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final Configuration config,
            final LdapProfile ldapProfile,
            final List<String> ldapURLs,
            final String userDN,
            final PasswordData userPassword
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final ChaiConfiguration chaiConfig = createChaiConfiguration( config, ldapProfile, ldapURLs, userDN, userPassword );
        LOGGER.trace( sessionLabel, "creating new ldap connection using config: " + chaiConfig.toString() );
        return pwmApplication.getLdapConnectionService().getChaiProviderFactory().newProvider( chaiConfig );
    }

    public static ChaiConfiguration createChaiConfiguration(
            final Configuration config,
            final LdapProfile ldapProfile
    )
            throws PwmUnrecoverableException
    {
        final List<String> ldapURLs = ldapProfile.readSettingAsStringArray( PwmSetting.LDAP_SERVER_URLS );
        final String userDN = ldapProfile.readSettingAsString( PwmSetting.LDAP_PROXY_USER_DN );
        final PasswordData userPassword = ldapProfile.readSettingAsPassword( PwmSetting.LDAP_PROXY_USER_PASSWORD );
        return createChaiConfiguration( config, ldapProfile, ldapURLs, userDN, userPassword );
    }

    public static ChaiConfiguration createChaiConfiguration(
            final Configuration config,
            final LdapProfile ldapProfile,
            final List<String> ldapURLs,
            final String userDN,
            final PasswordData userPassword
    )
            throws PwmUnrecoverableException
    {
        final ChaiConfiguration.ChaiConfigurationBuilder configBuilder = ChaiConfiguration.builder(
                ldapURLs,
                userDN,
                userPassword == null
                        ? null
                        : userPassword.getStringValue()
        );

        configBuilder.setSetting( ChaiSetting.PROMISCUOUS_SSL, config.readAppProperty( AppProperty.LDAP_PROMISCUOUS_ENABLE ) );
        {
            final boolean enableNmasExtensions = Boolean.parseBoolean( config.readAppProperty( AppProperty.LDAP_EXTENSIONS_NMAS_ENABLE ) );
            configBuilder.setSetting( ChaiSetting.EDIRECTORY_ENABLE_NMAS, Boolean.toString( enableNmasExtensions ) );
        }

        configBuilder.setSetting(
                ChaiSetting.CR_CHAI_STORAGE_ATTRIBUTE,
                ldapProfile.readSettingAsString( PwmSetting.CHALLENGE_USER_ATTRIBUTE )
        );
        configBuilder.setSetting(
                ChaiSetting.CR_ALLOW_DUPLICATE_RESPONSES,
                Boolean.toString( config.readSettingAsBoolean( PwmSetting.CHALLENGE_ALLOW_DUPLICATE_RESPONSES ) )
        );
        configBuilder.setSetting(
                ChaiSetting.CR_CASE_INSENSITIVE,
                Boolean.toString( config.readSettingAsBoolean( PwmSetting.CHALLENGE_CASE_INSENSITIVE ) )
        );

        {
            final String setting = config.readAppProperty( AppProperty.SECURITY_RESPONSES_HASH_ITERATIONS );
            if ( setting != null && setting.length() > 0 )
            {
                final int intValue = Integer.parseInt( setting );
                configBuilder.setSetting( ChaiSetting.CR_CHAI_SALT_COUNT, Integer.toString( intValue ) );
            }
        }

        // can cause issues with previous password authentication
        configBuilder.setSetting( ChaiSetting.JNDI_ENABLE_POOL, "false" );

        configBuilder.setSetting( ChaiSetting.CR_DEFAULT_FORMAT_TYPE, Answer.FormatType.SHA1_SALT.toString() );
        final String storageMethodString = config.readSettingAsString( PwmSetting.CHALLENGE_STORAGE_HASHED );
        try
        {
            final Answer.FormatType formatType = Answer.FormatType.valueOf( storageMethodString );
            configBuilder.setSetting( ChaiSetting.CR_DEFAULT_FORMAT_TYPE, formatType.toString() );
        }
        catch ( Exception e )
        {
            LOGGER.warn( "unknown CR storage format type '" + storageMethodString + "' " );
        }

        final List<X509Certificate> ldapServerCerts = ldapProfile.readSettingAsCertificate( PwmSetting.LDAP_SERVER_CERTS );
        if ( ldapServerCerts != null && ldapServerCerts.size() > 0 )
        {
            final X509TrustManager tm = new X509Utils.CertMatchingTrustManager( config, ldapServerCerts );
            configBuilder.setTrustManager( new X509TrustManager[]
                    {
                            tm,
                    }
            );
        }

        final String idleTimeoutMsString = config.readAppProperty( AppProperty.LDAP_CONNECTION_TIMEOUT );
        configBuilder.setSetting( ChaiSetting.LDAP_CONNECT_TIMEOUT, idleTimeoutMsString );

        // set the watchdog idle timeout.
        final int idleTimeoutMs = ( int ) config.readSettingAsLong( PwmSetting.LDAP_IDLE_TIMEOUT ) * 1000;
        if ( idleTimeoutMs > 0 )
        {
            configBuilder.setSetting( ChaiSetting.WATCHDOG_ENABLE, "true" );
            configBuilder.setSetting( ChaiSetting.WATCHDOG_IDLE_TIMEOUT, idleTimeoutMsString );
        }
        else
        {
            configBuilder.setSetting( ChaiSetting.WATCHDOG_ENABLE, "false" );
        }

        configBuilder.setSetting( ChaiSetting.LDAP_SEARCH_PAGING_ENABLE, config.readAppProperty( AppProperty.LDAP_SEARCH_PAGING_ENABLE ) );
        configBuilder.setSetting( ChaiSetting.LDAP_SEARCH_PAGING_SIZE, config.readAppProperty( AppProperty.LDAP_SEARCH_PAGING_SIZE ) );

        if ( config.readSettingAsBoolean( PwmSetting.AD_ENFORCE_PW_HISTORY_ON_SET ) )
        {
            configBuilder.setSetting( ChaiSetting.AD_SET_POLICY_HINTS_ON_PW_SET, "true" );
        }

        // write out any configured values;
        final String rawValue = config.readAppProperty( AppProperty.LDAP_CHAI_SETTINGS );
        final String[] rawValues = rawValue != null ? rawValue.split( AppProperty.VALUE_SEPARATOR ) : new String[ 0 ];
        final Map<String, String> configuredSettings = StringUtil.convertStringListToNameValuePair( Arrays.asList( rawValues ), "=" );
        for ( final Map.Entry<String, String> entry : configuredSettings.entrySet() )
        {
            final String key = entry.getKey();
            if ( key != null && !key.isEmpty() )
            {
                final ChaiSetting theSetting = ChaiSetting.forKey( key );
                if ( theSetting == null )
                {
                    LOGGER.warn( "ignoring unknown chai setting '" + key + "'" );
                }
                else
                {
                    configBuilder.setSetting( theSetting, entry.getValue() );
                }
            }
        }

        // set ldap referrals
        configBuilder.setSetting( ChaiSetting.LDAP_FOLLOW_REFERRALS, String.valueOf( config.readSettingAsBoolean( PwmSetting.LDAP_FOLLOW_REFERRALS ) ) );

        // enable wire trace;
        if ( config.readSettingAsBoolean( PwmSetting.LDAP_ENABLE_WIRE_TRACE ) )
        {
            configBuilder.setSetting( ChaiSetting.WIRETRACE_ENABLE, "true" );
        }

        return configBuilder.build();
    }

    /**
     * Update the user's "lastUpdated" attribute. By default this is
     * "pwmLastUpdate" attribute
     *
     * @param userIdentity ldap user to operate on
     * @return true if successful;
     * @throws com.novell.ldapchai.exception.ChaiUnavailableException if the
     *                                                                directory is unavailable
     */
    public static boolean updateLastPasswordUpdateAttribute(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        final ChaiUser theUser = pwmApplication.getProxiedChaiUser( userIdentity );
        boolean success = false;

        final LdapProfile ldapProfile = pwmApplication.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );
        final String updateAttribute = ldapProfile.readSettingAsString( PwmSetting.PASSWORD_LAST_UPDATE_ATTRIBUTE );

        if ( updateAttribute != null && updateAttribute.length() > 0 )
        {
            try
            {
                theUser.writeDateAttribute( updateAttribute, Instant.now() );
                LOGGER.debug( sessionLabel, "wrote pwdLastModified update attribute for " + theUser.getEntryDN() );
                success = true;
            }
            catch ( ChaiOperationException e )
            {
                LOGGER.debug( sessionLabel, "error writing update attribute for user '" + theUser.getEntryDN() + "' " + e.getMessage() );
            }
        }

        return success;
    }

    public static Map<String, List<String>> readAllEntryAttributeValues( final ChaiEntry chaiEntry )
            throws ChaiUnavailableException, ChaiOperationException
    {
        final SearchHelper searchHelper = new SearchHelper();
        searchHelper.setSearchScope( SearchScope.BASE );
        searchHelper.setFilter( "(objectClass=*)" );
        final Map<String, Map<String, List<String>>> results = chaiEntry.getChaiProvider().searchMultiValues( chaiEntry.getEntryDN(), searchHelper );
        if ( !results.isEmpty() )
        {
            return results.values().iterator().next();
        }
        return Collections.emptyMap();
    }

    public static Iterator<UserIdentity> readAllUsersFromLdap(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final String searchFilter,
            final int maxResults
    )
            throws ChaiUnavailableException, ChaiOperationException, PwmUnrecoverableException, PwmOperationalException
    {
        final UserSearchEngine userSearchEngine = pwmApplication.getUserSearchEngine();

        final SearchConfiguration searchConfiguration;
        {
            final SearchConfiguration.SearchConfigurationBuilder builder = SearchConfiguration.builder();

            builder.enableValueEscaping( false );
            builder.searchTimeout( Long.parseLong( pwmApplication.getConfig().readAppProperty( AppProperty.REPORTING_LDAP_SEARCH_TIMEOUT ) ) );

            if ( searchFilter == null )
            {
                builder.username( "*" );
            }
            else
            {
                builder.filter( searchFilter );
            }

            searchConfiguration = builder.build();
        }

        LOGGER.debug( sessionLabel, "beginning user search using parameters: " + ( JsonUtil.serialize( searchConfiguration ) ) );

        final Map<UserIdentity, Map<String, String>> searchResults = userSearchEngine.performMultiUserSearch(
                searchConfiguration,
                maxResults,
                Collections.emptyList(),
                sessionLabel

        );
        LOGGER.debug( sessionLabel, "user search found " + searchResults.size() + " users" );

        final Queue<UserIdentity> tempQueue = new LinkedList<>( searchResults.keySet() );

        return new Iterator<UserIdentity>()
        {
            @Override
            public boolean hasNext( )
            {
                return tempQueue.peek() != null;
            }

            @Override
            public UserIdentity next( )
            {
                return tempQueue.poll();
            }
        };
    }

    public static Instant readPasswordExpirationTime( final ChaiUser theUser )
    {
        try
        {
            Instant ldapPasswordExpirationTime = theUser.readPasswordExpirationDate();
            if ( ldapPasswordExpirationTime != null && ldapPasswordExpirationTime.toEpochMilli() < 0 )
            {
                // If ldapPasswordExpirationTime is less than 0, this may indicate an extremely late date, past the epoch.
                ldapPasswordExpirationTime = null;
            }
            return ldapPasswordExpirationTime;
        }
        catch ( Exception e )
        {
            LOGGER.warn( "error reading password expiration time: " + e.getMessage() );
        }

        return null;
    }

    public static PasswordData readLdapPassword(
            final PwmApplication pwmApplication,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException
    {
        if ( userIdentity == null || userIdentity.getUserDN() == null || userIdentity.getUserDN().length() < 1 )
        {
            throw new NullPointerException( "invalid user (null)" );
        }

        final ChaiProvider chaiProvider = pwmApplication.getProxyChaiProvider( userIdentity.getLdapProfileID() );
        final ChaiUser chaiUser = chaiProvider.getEntryFactory().newChaiUser( userIdentity.getUserDN() );

        // use chai (nmas) to retrieve user password
        if ( pwmApplication.getConfig().readSettingAsBoolean( PwmSetting.EDIRECTORY_READ_USER_PWD ) )
        {
            String currentPass = null;
            try
            {
                final String readPassword = chaiUser.readPassword();
                if ( readPassword != null && readPassword.length() > 0 )
                {
                    currentPass = readPassword;
                    LOGGER.debug( sessionLabel, "successfully retrieved user's current password from ldap, now conducting standard authentication" );
                }
            }
            catch ( Exception e )
            {
                LOGGER.debug( sessionLabel, "unable to retrieve user password from ldap: " + e.getMessage() );
            }

            // actually do the authentication since we have user pw.
            if ( currentPass != null && currentPass.length() > 0 )
            {
                return new PasswordData( currentPass );
            }
        }
        else
        {
            LOGGER.trace( sessionLabel, "skipping attempt to read user password, option disabled" );
        }
        return null;
    }

    public static PhotoDataBean readPhotoDataFromLdap(
            final Configuration configuration,
            final ChaiUser chaiUser,
            final UserIdentity userIdentity
    )
            throws ChaiUnavailableException, PwmUnrecoverableException, PwmOperationalException
    {
        final LdapProfile ldapProfile = userIdentity.getLdapProfile( configuration );
        final String attribute = ldapProfile.readSettingAsString( PwmSetting.PEOPLE_SEARCH_PHOTO_ATTRIBUTE );
        if ( attribute == null || attribute.isEmpty() )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "ldap photo attribute is not configured" ) );
        }

        final byte[] photoData;
        final String mimeType;
        try
        {
            final byte[][] photoAttributeData = chaiUser.readMultiByteAttribute( attribute );
            if ( photoAttributeData == null || photoAttributeData.length == 0 || photoAttributeData[ 0 ].length == 0 )
            {
                throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_SERVICE_NOT_AVAILABLE, "user has no photo data stored in LDAP attribute" ) );
            }
            photoData = photoAttributeData[ 0 ];
            mimeType = URLConnection.guessContentTypeFromStream( new ByteArrayInputStream( photoData ) );
        }
        catch ( IOException | ChaiOperationException e )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.ERROR_UNKNOWN, "error reading user photo ldap attribute: " + e.getMessage() ) );
        }
        return new PhotoDataBean( mimeType, photoData );
    }

}
