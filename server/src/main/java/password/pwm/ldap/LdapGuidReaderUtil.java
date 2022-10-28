/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.ldap;

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.ChaiOperationException;
import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.AppProperty;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.search.SearchConfiguration;
import password.pwm.ldap.search.UserSearchService;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.util.Optional;

class LdapGuidReaderUtil
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( LdapGuidReaderUtil.class );

    static Optional<String> readExistingGuidValue(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final ChaiUser theUser = pwmDomain.getProxiedChaiUser( sessionLabel, userIdentity );
        final LdapProfile ldapProfile = pwmDomain.getConfig().getLdapProfiles().get( userIdentity.getLdapProfileID() );
        final LdapProfile.GuidMode guidMode = ldapProfile.getGuidMode();

        if ( guidMode == LdapProfile.GuidMode.DN )
        {
            return Optional.of( userIdentity.getUserDN() );
        }

        if ( guidMode == LdapProfile.GuidMode.VENDORGUID )
        {
            return readVendorGuid( theUser, sessionLabel );
        }

        return readAttributeGuid( sessionLabel, ldapProfile, userIdentity, theUser );
    }

    private static Optional<String> readAttributeGuid(
            final SessionLabel sessionLabel,
            final LdapProfile ldapProfile,
            final UserIdentity userIdentity,
            final ChaiUser theUser
    )
            throws PwmUnrecoverableException
    {
        final String guidAttributeName = ldapProfile.readSettingAsString( PwmSetting.LDAP_GUID_ATTRIBUTE );
        try
        {
            final String value = theUser.readStringAttribute( guidAttributeName );
            if ( StringUtil.isEmpty( value ) )
            {
                LOGGER.warn( sessionLabel, () -> "missing GUID value for user " + userIdentity
                        + " from '" + guidAttributeName + "' attribute" );
            }
            else
            {
                LOGGER.trace( sessionLabel, () -> "read GUID value for user " + userIdentity
                        + " using attribute '"
                        + guidAttributeName + "': " + value );
                return Optional.of( value );
            }
        }
        catch ( final ChaiOperationException e )
        {
            LOGGER.warn( sessionLabel, () -> "unexpected error while reading attribute GUID "
                    + "value for user "
                    + userIdentity + " from '" + guidAttributeName + "' attribute, error: " + e.getMessage() );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }

        return Optional.empty();
    }

    private static Optional<String> readVendorGuid(
            final ChaiUser theUser,
            final SessionLabel sessionLabel
    )
    {
        try
        {
            final String guidValue = theUser.readGUID();
            if ( StringUtil.isEmpty( guidValue ) )
            {
                LOGGER.warn( sessionLabel,
                        () -> "unable to find a VENDORGUID value for user " + theUser.getEntryDN() );
            }
            else
            {
                LOGGER.trace( sessionLabel, () -> "read VENDORGUID value for user " + theUser
                        + ": " + guidValue );
                return Optional.of( guidValue );
            }
        }
        catch ( final Exception e )
        {
            LOGGER.warn( sessionLabel, () -> "error while reading vendor GUID value for user "
                    + theUser.getEntryDN() + ", error: " + e.getMessage() );
        }

        return Optional.empty();
    }


    private static boolean searchForExistingGuidValue(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final String guidValue
    )
            throws PwmUnrecoverableException
    {
        boolean exists = false;
        for ( final LdapProfile ldapProfile : pwmDomain.getConfig().getLdapProfiles().values() )
        {
            final LdapProfile.GuidMode guidMode = ldapProfile.getGuidMode();
            if ( guidMode == LdapProfile.GuidMode.ATTRIBUTE )
            {
                try
                {
                    final String guidAttributeName = ldapProfile.readSettingAsString( PwmSetting.LDAP_GUID_ATTRIBUTE );

                    // check if it is unique
                    final SearchConfiguration searchConfiguration = SearchConfiguration.builder()
                            .filter( "(" + guidAttributeName + "=" + guidValue + ")" )
                            .build();

                    final UserSearchService userSearchService = pwmDomain.getUserSearchEngine();
                    final UserIdentity result = userSearchService.performSingleUserSearch( searchConfiguration,
                            sessionLabel );
                    exists = result != null;
                }
                catch ( final PwmOperationalException e )
                {
                    if ( e.getError() != PwmError.ERROR_CANT_MATCH_USER )
                    {
                        LOGGER.warn( sessionLabel, () -> "error while searching to verify new "
                                + "unique GUID value: " + e.getError() );
                    }
                }
            }
        }
        return exists;
    }

    static String assignGuidToUser(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final String guidAttributeName
    )
            throws PwmUnrecoverableException
    {
        int attempts = 0;
        String newGuid = null;

        while ( attempts < 10 && newGuid == null )
        {
            attempts++;
            newGuid = generateGuidValue( pwmDomain, sessionLabel );
            if ( searchForExistingGuidValue( pwmDomain, sessionLabel, newGuid ) )
            {
                newGuid = null;
            }
        }

        if ( newGuid == null )
        {
            throw new PwmUnrecoverableException( new ErrorInformation(
                    PwmError.ERROR_INTERNAL,
                    "unable to generate unique GUID value for user " + userIdentity )
            );
        }

        LdapOperationsHelper.addConfiguredUserObjectClass( sessionLabel, userIdentity, pwmDomain );
        try
        {
            // write it to the directory
            final ChaiUser chaiUser = pwmDomain.getProxiedChaiUser( sessionLabel, userIdentity );
            chaiUser.writeStringAttribute( guidAttributeName, newGuid );
            final String finalNewGuid = newGuid;
            LOGGER.debug( sessionLabel,
                    () -> "added GUID value '" + finalNewGuid + "' to user " + userIdentity );
            return newGuid;
        }
        catch ( final ChaiOperationException e )
        {
            final String errorMsg =
                    "unable to write GUID value to user attribute " + guidAttributeName + " : " + e.getMessage()
                    + ", cannot write GUID value to user " + userIdentity;
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INTERNAL, errorMsg );
            LOGGER.warn( errorInformation::toDebugStr );
            throw new PwmUnrecoverableException( errorInformation );
        }
        catch ( final ChaiUnavailableException e )
        {
            throw PwmUnrecoverableException.fromChaiException( e );
        }
    }

    private static String generateGuidValue(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel
    )
    {
        final MacroRequest macroRequest = MacroRequest.forNonUserSpecific( pwmDomain.getPwmApplication(),
                sessionLabel );
        final String guidPattern = pwmDomain.getConfig().readAppProperty( AppProperty.LDAP_GUID_PATTERN );
        return macroRequest.expandMacros( guidPattern );
    }
}
