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

package password.pwm.config.profile;

import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingCategory;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequestContext;
import password.pwm.ldap.permission.UserPermissionUtility;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ProfileUtility
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ProfileUtility.class );

    public static Optional<String> discoverProfileIDForUser(
            final PwmRequestContext pwmRequestContext,
            final UserIdentity userIdentity,
            final ProfileDefinition profileDefinition
    )
            throws PwmUnrecoverableException
    {
        return discoverProfileIDForUser( pwmRequestContext.getPwmDomain(), pwmRequestContext.getSessionLabel(), userIdentity, profileDefinition );
    }

    public static <T extends Profile> T profileForUser(
            final PwmRequestContext pwmRequestContext,
            final UserIdentity userIdentity,
            final ProfileDefinition profileDefinition,
            final Class<T> classOfT
    )
            throws PwmUnrecoverableException
    {
        final Optional<String> profileID = discoverProfileIDForUser( pwmRequestContext, userIdentity, profileDefinition );
        if ( profileID.isEmpty() )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_NO_PROFILE_ASSIGNED, "profile of type " + profileDefinition + " is required but not assigned" );
        }
        return ( T ) pwmRequestContext.getDomainConfig().getProfileMap( profileDefinition ).get( profileID.get() );
    }


    public static Optional<String> discoverProfileIDForUser(
            final PwmDomain pwmDomain,
            final SessionLabel sessionLabel,
            final UserIdentity userIdentity,
            final ProfileDefinition profileDefinition
    )
            throws PwmUnrecoverableException
    {
        final Map<String, Profile> profileMap = pwmDomain.getConfig().getProfileMap( profileDefinition );
        for ( final Profile profile : profileMap.values() )
        {
            final List<UserPermission> queryMatches = profile.profilePermissions();
            final boolean match = UserPermissionUtility.testUserPermission( pwmDomain, sessionLabel, userIdentity, queryMatches );
            if ( match )
            {
                return Optional.of( profile.getIdentifier() );
            }
        }
        return Optional.empty();
    }

    public static List<String> profileIDsForCategory( final StoredConfiguration storedConfiguration, final DomainID domainID, final PwmSettingCategory pwmSettingCategory )
    {
        final PwmSetting profileSetting = pwmSettingCategory.getProfileSetting().orElseThrow( IllegalStateException::new );
        final StoredConfigKey key = StoredConfigKey.forSetting( profileSetting, null, domainID );
        final StoredValue storedValue = StoredConfigurationUtil.getValueOrDefault( storedConfiguration, key );
        final Predicate<String> regexPredicate = syntaxFilterPredicateForProfileID( pwmSettingCategory );

        final List<String> returnData = ValueTypeConverter.valueToStringArray( storedValue )
                .stream()
                .distinct()
                .filter( StringUtil::notEmpty )
                .filter( regexPredicate )
                .collect( Collectors.toUnmodifiableList() );

        if ( returnData.isEmpty() )
        {
            return Collections.singletonList( PwmConstants.PROFILE_ID_DEFAULT );
        }

        return returnData;
    }

    private static Predicate<String> syntaxFilterPredicateForProfileID( final PwmSettingCategory pwmSettingCategory )
    {
        final PwmSetting pwmSetting = pwmSettingCategory.getProfileSetting().orElseThrow();
        final Pattern pattern = pwmSetting.getRegExPattern();
        return pattern.asMatchPredicate();
    }
}
