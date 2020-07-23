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

package password.pwm.config.profile;

import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.ChaiChallengeSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.ChallengeValue;
import password.pwm.config.value.data.ChallengeItemConfiguration;
import password.pwm.config.value.data.UserPermission;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChallengeProfile implements Profile, Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ChallengeProfile.class );

    private final String profileID;
    private final Locale locale;
    private final ChallengeSet challengeSet;
    private final ChallengeSet helpdeskChallengeSet;
    private final int minRandomSetup;
    private final int minHelpdeskRandomsSetup;
    private final List<UserPermission> userPermissions;

    private ChallengeProfile(
            final String profileID,
            final Locale locale,
            final ChallengeSet challengeSet,
            final ChallengeSet helpdeskChallengeSet,
            final int minRandomSetup,
            final int minHelpdeskRandomSetup,
            final List<UserPermission> userPermissions
    )
    {
        this.profileID = profileID;
        this.locale = locale;
        this.challengeSet = challengeSet;
        this.helpdeskChallengeSet = helpdeskChallengeSet;
        this.minRandomSetup = minRandomSetup;
        this.minHelpdeskRandomsSetup = minHelpdeskRandomSetup;
        this.userPermissions = userPermissions != null ? Collections.unmodifiableList( userPermissions ) : Collections.<UserPermission>emptyList();
    }

    public static ChallengeProfile readChallengeProfileFromConfig(
            final String profileID,
            final Locale locale,
            final StoredConfiguration storedConfiguration
    )
    {
        final int minRandomRequired = ( int ) Configuration.JavaTypeConverter.valueToLong( storedConfiguration.readSetting( PwmSetting.CHALLENGE_MIN_RANDOM_REQUIRED, profileID ) );

        ChallengeSet readChallengeSet = null;
        try
        {
            readChallengeSet = readChallengeSet(
                    profileID,
                    locale,
                    storedConfiguration,
                    PwmSetting.CHALLENGE_REQUIRED_CHALLENGES,
                    PwmSetting.CHALLENGE_RANDOM_CHALLENGES,
                    minRandomRequired
            );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.trace( () -> "configured challengeSet for profile '" + profileID + "' is not valid: " + e.getMessage() );
        }

        ChallengeSet readHelpdeskChallengeSet = null;
        try
        {
            readHelpdeskChallengeSet = readChallengeSet(
                    profileID,
                    locale,
                    storedConfiguration,
                    PwmSetting.CHALLENGE_HELPDESK_REQUIRED_CHALLENGES,
                    PwmSetting.CHALLENGE_HELPDESK_RANDOM_CHALLENGES,
                    1
            );
        }
        catch ( final PwmOperationalException e )
        {
            LOGGER.trace( () -> "discarding configured helpdesk challengeSet for profile '" + profileID + "' issue: " + e.getMessage() );
        }

        final int minRandomSetup = ( int ) Configuration.JavaTypeConverter.valueToLong( storedConfiguration.readSetting( PwmSetting.CHALLENGE_MIN_RANDOM_SETUP, profileID ) );
        final int minHelpdeskRandomSetup = ( int ) Configuration.JavaTypeConverter.valueToLong( storedConfiguration.readSetting(
                PwmSetting.CHALLENGE_HELPDESK_MIN_RANDOM_SETUP,
                profileID
        ) );
        final List<UserPermission> userPermissions = ( List<UserPermission> ) storedConfiguration.readSetting(
                PwmSetting.CHALLENGE_POLICY_QUERY_MATCH,
                profileID
        ).toNativeObject();

        return new ChallengeProfile( profileID, locale, readChallengeSet, readHelpdeskChallengeSet, minRandomSetup, minHelpdeskRandomSetup, userPermissions );
    }

    public static ChallengeProfile createChallengeProfile(
            final String profileID,
            final Locale locale,
            final ChallengeSet challengeSet,
            final ChallengeSet helpdeskChallengeSet,
            final int minRandomSetup,
            final int minHelpdeskRandomSetup
    )
    {
        return new ChallengeProfile( profileID, locale, challengeSet, helpdeskChallengeSet, minRandomSetup, minHelpdeskRandomSetup, null );
    }

    public String getIdentifier( )
    {
        return profileID;
    }

    public String getDisplayName( final Locale locale )
    {
        return getIdentifier();
    }

    public Locale getLocale( )
    {
        return locale;
    }

    public ChallengeSet getChallengeSet( )
    {
        return challengeSet;
    }

    public ChallengeSet getHelpdeskChallengeSet( )
    {
        return helpdeskChallengeSet;
    }

    public int getMinRandomSetup( )
    {
        return minRandomSetup;
    }

    public int getMinHelpdeskRandomsSetup( )
    {
        return minHelpdeskRandomsSetup;
    }

    public List<UserPermission> getUserPermissions( )
    {
        return userPermissions;
    }

    private static ChallengeSet readChallengeSet(
            final String profileID,
            final Locale locale,
            final StoredConfiguration storedConfiguration,
            final PwmSetting requiredChallenges,
            final PwmSetting randomChallenges,
            final int minimumRands
    )
            throws PwmOperationalException
    {
        final List<ChallengeItemConfiguration> requiredQuestions = valueToChallengeItemArray(
                storedConfiguration.readSetting( requiredChallenges, profileID ), locale );
        final List<ChallengeItemConfiguration> randomQuestions = valueToChallengeItemArray(
                storedConfiguration.readSetting( randomChallenges, profileID ), locale );

        final List<Challenge> challenges = new ArrayList<>();
        int randoms = minimumRands;

        if ( requiredQuestions != null )
        {
            for ( final ChallengeItemConfiguration item : requiredQuestions )
            {
                if ( item != null )
                {
                    final Challenge chaiChallenge = new ChaiChallenge(
                            true,
                            item.getText(),
                            item.getMinLength(),
                            item.getMaxLength(),
                            item.isAdminDefined(),
                            item.getMaxQuestionCharsInAnswer(),
                            item.isEnforceWordlist()
                    );
                    challenges.add( chaiChallenge );
                }
            }
        }

        if ( randomQuestions != null )
        {
            for ( final ChallengeItemConfiguration item : randomQuestions )
            {
                if ( item != null )
                {
                    final Challenge chaiChallenge = new ChaiChallenge(
                            false,
                            item.getText(),
                            item.getMinLength(),
                            item.getMaxLength(),
                            item.isAdminDefined(),
                            item.getMaxQuestionCharsInAnswer(),
                            item.isEnforceWordlist()
                    );
                    challenges.add( chaiChallenge );
                }
            }

            if ( randoms > randomQuestions.size() )
            {
                randoms = randomQuestions.size();
            }
        }
        else
        {
            randoms = 0;
        }

        try
        {
            return new ChaiChallengeSet( challenges, randoms, locale, PwmConstants.PWM_APP_NAME + "-defined " + PwmConstants.SERVLET_VERSION );
        }
        catch ( final ChaiValidationException e )
        {
            throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, "invalid challenge set configuration: " + e.getMessage() ) );
        }
    }

    static List<ChallengeItemConfiguration> valueToChallengeItemArray(
            final StoredValue value,
            final Locale locale
    )
    {
        if ( !( value instanceof ChallengeValue ) )
        {
            throw new IllegalArgumentException( "may not read ChallengeValue value" );
        }
        final Map<String, List<ChallengeItemConfiguration>> storedValues = ( Map<String, List<ChallengeItemConfiguration>> ) value.toNativeObject();
        final Map<Locale, List<ChallengeItemConfiguration>> availableLocaleMap = new LinkedHashMap<>();
        for ( final Map.Entry<String, List<ChallengeItemConfiguration>> entry : storedValues.entrySet() )
        {
            final String localeStr = entry.getKey();
            availableLocaleMap.put( LocaleHelper.parseLocaleString( localeStr ), entry.getValue() );
        }
        final Locale matchedLocale = LocaleHelper.localeResolver( locale, availableLocaleMap.keySet() );

        return availableLocaleMap.get( matchedLocale );
    }

    @Override
    public ProfileDefinition profileType( )
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<UserPermission> getPermissionMatches( )
    {
        throw new UnsupportedOperationException();
    }
}
