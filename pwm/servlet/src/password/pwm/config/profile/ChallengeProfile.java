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

package password.pwm.config.profile;

import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.ChaiChallengeSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmConstants;
import password.pwm.config.*;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.ChallengeValue;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.*;

public class ChallengeProfile implements Profile, Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ChallengeProfile.class);

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
        this.userPermissions = userPermissions != null ? Collections.unmodifiableList(userPermissions) : Collections.<UserPermission>emptyList();
    }

    public static ChallengeProfile readChallengeProfileFromConfig(
            final String profileID,
            final Locale locale,
            final StoredConfiguration storedConfiguration
    ) {
        final int minRandomRequired = (int)Configuration.JavaTypeConverter.valueToLong(storedConfiguration.readSetting(PwmSetting.CHALLENGE_MIN_RANDOM_REQUIRED,profileID));

        ChallengeSet readChallengeSet = null;
        try {
            readChallengeSet = readChallengeSet(
                    profileID,
                    locale,
                    storedConfiguration,
                    PwmSetting.CHALLENGE_REQUIRED_CHALLENGES,
                    PwmSetting.CHALLENGE_RANDOM_CHALLENGES,
                    minRandomRequired
            );
        } catch (PwmOperationalException e) {
            LOGGER.trace("configured challengeSet for profile '" + profileID + "' is not valid: " + e.getMessage());
        }

        ChallengeSet readHelpdeskChallengeSet = null;
        try {
            readHelpdeskChallengeSet = readChallengeSet(
                    profileID,
                    locale,
                    storedConfiguration,
                    PwmSetting.CHALLENGE_HELPDESK_REQUIRED_CHALLENGES,
                    PwmSetting.CHALLENGE_HELPDESK_RANDOM_CHALLENGES,
                    1
            );
        } catch (PwmOperationalException e) {
            LOGGER.trace("discarding configured helpdesk challengeSet for profile '" + profileID + "' issue: " + e.getMessage());
        }
        
        final int minRandomSetup = (int)Configuration.JavaTypeConverter.valueToLong(storedConfiguration.readSetting(PwmSetting.CHALLENGE_MIN_RANDOM_SETUP, profileID));
        final int minHelpdeskRandomSetup = (int)Configuration.JavaTypeConverter.valueToLong(storedConfiguration.readSetting(PwmSetting.CHALLENGE_HELPDESK_MIN_RANDOM_SETUP, profileID));
        final List<UserPermission> userPermissions = (List<UserPermission>)storedConfiguration.readSetting(PwmSetting.CHALLENGE_POLICY_QUERY_MATCH, profileID).toNativeObject();

        return new ChallengeProfile(profileID, locale, readChallengeSet, readHelpdeskChallengeSet, minRandomSetup, minHelpdeskRandomSetup, userPermissions);
    }

    public static ChallengeProfile createChallengeProfile(
            String profileID,
            Locale locale,
            ChallengeSet challengeSet,
            ChallengeSet helpdeskChallengeSet,
            int minRandomSetup,
            int minHelpdeskRandomSetup
    ) {
        return new ChallengeProfile(profileID, locale, challengeSet, helpdeskChallengeSet, minRandomSetup, minHelpdeskRandomSetup, null);
    }

    public String getIdentifier()
    {
        return profileID;
    }

    public String getDisplayName(final Locale locale) {
        return getIdentifier();
    }

    public Locale getLocale()
    {
        return locale;
    }

    public ChallengeSet getChallengeSet()
    {
        return challengeSet;
    }

    public ChallengeSet getHelpdeskChallengeSet()
    {
        return helpdeskChallengeSet;
    }

    public int getMinRandomSetup() {
        return minRandomSetup;
    }

    public int getMinHelpdeskRandomsSetup() {
        return minHelpdeskRandomsSetup;
    }

    public List<UserPermission> getUserPermissions() {
        return userPermissions;
    }

    private static ChallengeSet readChallengeSet(
            final String profileID,
            final Locale locale,
            final StoredConfiguration storedConfiguration,
            final PwmSetting requiredChallenges,
            final PwmSetting randomChallenges,
            int minimumRands
    ) throws PwmOperationalException {
        final List<ChallengeItemConfiguration> requiredQuestions = valueToChallengeItemArray(
                storedConfiguration.readSetting(requiredChallenges, profileID), locale);
        final List<ChallengeItemConfiguration> randomQuestions = valueToChallengeItemArray(
                storedConfiguration.readSetting(randomChallenges, profileID), locale);

        final List<Challenge> challenges = new ArrayList<>();

        if (requiredQuestions != null) {
            for (final ChallengeItemConfiguration item : requiredQuestions) {
                if (item != null) {
                    final Challenge chaiChallenge = new ChaiChallenge(
                            true, 
                            item.getText(), 
                            item.getMinLength(), 
                            item.getMaxLength(), 
                            item.isAdminDefined(),
                            item.getMaxQuestionCharsInAnswer(),
                            item.isEnforceWordlist()
                    );
                    challenges.add(chaiChallenge);
                }
            }
        }

        if (randomQuestions != null) {
            for (final ChallengeItemConfiguration item : randomQuestions) {
                if (item != null) {
                    final Challenge chaiChallenge = new ChaiChallenge(
                            false, 
                            item.getText(), 
                            item.getMinLength(), 
                            item.getMaxLength(), 
                            item.isAdminDefined(),
                            item.getMaxQuestionCharsInAnswer(),
                            item.isEnforceWordlist()
                    );
                    challenges.add(chaiChallenge);
                }
            }

            if (minimumRands > randomQuestions.size()) {
                minimumRands = randomQuestions.size();
            }
        } else {
            minimumRands = 0;
        }

        try {
            return new ChaiChallengeSet(challenges, minimumRands, locale, PwmConstants.PWM_APP_NAME + "-defined " + PwmConstants.SERVLET_VERSION);
        } catch (ChaiValidationException e) {
            throw new PwmOperationalException(new ErrorInformation(PwmError.CONFIG_FORMAT_ERROR,"invalid challenge set configuration: " + e.getMessage()));
        }
    }

    static List<ChallengeItemConfiguration> valueToChallengeItemArray(
            final StoredValue value,
            final Locale locale
    ) {
        if (!(value instanceof ChallengeValue)) {
            throw new IllegalArgumentException("may not read ChallengeValue value");
        }
        final Map<String, List<ChallengeItemConfiguration>> storedValues = (Map<String, List<ChallengeItemConfiguration>>)value.toNativeObject();
        final Map<Locale, List<ChallengeItemConfiguration>> availableLocaleMap = new LinkedHashMap<>();
        for (final String localeStr : storedValues.keySet()) {
            availableLocaleMap.put(LocaleHelper.parseLocaleString(localeStr), storedValues.get(localeStr));
        }
        final Locale matchedLocale = LocaleHelper.localeResolver(locale, availableLocaleMap.keySet());

        return availableLocaleMap.get(matchedLocale);
    }

    @Override
    public ProfileType profileType() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<UserPermission> getPermissionMatches() {
        throw new UnsupportedOperationException();
    }
}
