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

package password.pwm.config;

import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.ChaiChallengeSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmConstants;
import password.pwm.util.PwmLogger;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChallengeProfile implements Serializable {
    private static final PwmLogger LOGGER = PwmLogger.getLogger(ChallengeProfile.class);

    private final String profileID;
    private final Locale locale;
    private final StoredConfiguration storedConfiguration;
    private final ChallengeSet challengeSet;
    private final ChallengeSet helpdeskChallengeSet;

    private ChallengeProfile(
            String profileID,
            Locale locale,
            StoredConfiguration storedConfiguration,
            ChallengeSet challengeSet,
            ChallengeSet helpdeskChallengeSet
    )
    {
        this.profileID = profileID;
        this.locale = locale;
        this.storedConfiguration = storedConfiguration;
        this.challengeSet = challengeSet;
        this.helpdeskChallengeSet = helpdeskChallengeSet;
    }

    ChallengeProfile(
            String profileID,
            Locale locale,
            StoredConfiguration storedConfiguration
    )
    {
        this.profileID = profileID;
        this.locale = locale;
        this.storedConfiguration = storedConfiguration;

        final Long minRandomRequired = (Long)storedConfiguration.readSetting(PwmSetting.CHALLENGE_MIN_RANDOM_REQUIRED,profileID).toNativeObject();
        this.challengeSet = readChallengeSet(
                profileID,
                locale,
                storedConfiguration,
                PwmSetting.CHALLENGE_REQUIRED_CHALLENGES,
                PwmSetting.CHALLENGE_RANDOM_CHALLENGES,
                minRandomRequired.intValue()
        );

        this.helpdeskChallengeSet = readChallengeSet(
                profileID,
                locale,
                storedConfiguration,
                PwmSetting.CHALLENGE_HELPDESK_REQUIRED_CHALLENGES,
                PwmSetting.CHALLENGE_HELPDESK_RANDOM_CHALLENGES,
                1
        );
    }

    public ChallengeProfile overrideChallengeSet(final ChallengeSet challengeSet) {
        return new ChallengeProfile(
                this.profileID,
                this.locale,
                this.storedConfiguration,
                challengeSet,
                this.helpdeskChallengeSet
        );
    }

    public String getProfileID()
    {
        return profileID;
    }

    public String getQueryString()
    {
        return Configuration.JavaTypeConverter.valueToString(storedConfiguration.readSetting(PwmSetting.CHALLENGE_POLICY_QUERY_MATCH,profileID));
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

    public long readSettingAsLong(final PwmSetting setting) {
        if (PwmSettingSyntax.NUMERIC != setting.getSyntax()) {
            throw new IllegalArgumentException("may not read NUMERIC value for setting: " + setting.toString());
        }

        return (Long)storedConfiguration.readSetting(setting,profileID).toNativeObject();
    }

    private static ChallengeSet readChallengeSet(
            final String profileID,
            final Locale locale,
            final StoredConfiguration storedConfiguration,
            final PwmSetting requiredChallenges,
            final PwmSetting randomChallenges,
            int minimumRands
    )
    {
        final List<String> requiredQuestions = Configuration.JavaTypeConverter.valueToLocalizedStringArray(
                storedConfiguration.readSetting(requiredChallenges, profileID), locale);
        final List<String> randomQuestions = Configuration.JavaTypeConverter.valueToLocalizedStringArray(
                storedConfiguration.readSetting(randomChallenges, profileID), locale);

        final List<Challenge> challenges = new ArrayList<Challenge>();

        if (requiredQuestions != null) {
            for (final String question : requiredQuestions) {
                final Challenge challenge = parseConfigStringToChallenge(question, true);
                if (challenge != null) {
                    challenges.add(challenge);
                }
            }
        }

        if (randomQuestions != null) {
            for (final String question : randomQuestions) {
                final Challenge challenge = parseConfigStringToChallenge(question, false);
                if (challenge != null) {
                    challenges.add(challenge);
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
            LOGGER.warn("invalid challenge set configuration: " + e.getMessage());
        }
        return null;
    }

    private static Challenge parseConfigStringToChallenge(String inputString, final boolean required) {
        if (inputString == null || inputString.length() < 1) {
            return null;
        }

        int minLength = 2;
        int maxLength = 255;

        final String[] s1 = inputString.split("::");
        if (s1.length > 0) {
            inputString = s1[0].trim();
        }
        if (s1.length > 1) {
            try {
                minLength = Integer.parseInt(s1[1]);
            } catch (Exception e) {
                LOGGER.debug("unexpected error parsing config input '" + inputString + "' " + e.getMessage());
            }
        }
        if (s1.length > 2) {
            try {
                maxLength = Integer.parseInt(s1[2]);
            } catch (Exception e) {
                LOGGER.debug("unexpected error parsing config input '" + inputString + "' " + e.getMessage());
            }
        }

        boolean adminDefined = true;
        if (inputString != null && inputString.equalsIgnoreCase("%user%")) {
            inputString = null;
            adminDefined = false;
        }

        return new ChaiChallenge(required, inputString, minLength, maxLength, adminDefined);
    }


}
