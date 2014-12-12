/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2014 The PWM Project
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

package password.pwm.config.policy;

import com.novell.ldapchai.cr.ChaiChallenge;
import com.novell.ldapchai.cr.ChaiChallengeSet;
import com.novell.ldapchai.cr.Challenge;
import com.novell.ldapchai.cr.ChallengeSet;
import com.novell.ldapchai.exception.ChaiValidationException;
import password.pwm.PwmConstants;
import password.pwm.config.*;
import password.pwm.config.value.ChallengeValue;
import password.pwm.cr.ChallengeItemBean;
import password.pwm.i18n.LocaleHelper;
import password.pwm.util.logging.PwmLogger;

import java.io.Serializable;
import java.util.*;

public class ChallengeProfile extends AbstractProfile implements Profile, Serializable {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ChallengeProfile.class);

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
        super(profileID, null);
        this.profileID = profileID;
        this.locale = locale;
        this.storedConfiguration = storedConfiguration;
        this.challengeSet = challengeSet;
        this.helpdeskChallengeSet = helpdeskChallengeSet;
    }

    private ChallengeProfile(
            String profileID,
            Locale locale,
            StoredConfiguration storedConfiguration
    )
    {
        super(profileID, null);
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

    public static ChallengeProfile createChallengeProfile(
            String profileID,
            Locale locale,
            StoredConfiguration storedConfiguration
    ) {
        return new ChallengeProfile(profileID, locale, storedConfiguration);
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

    public String getIdentifier()
    {
        return profileID;
    }

    public String getDisplayName(final Locale locale) {
        return getIdentifier();
    }

    public List<UserPermission> getUserPermissions()
    {
        final StoredValue readValue = storedConfiguration.readSetting(PwmSetting.CHALLENGE_POLICY_QUERY_MATCH,profileID);
        return (List<UserPermission>)readValue.toNativeObject();
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
        final List<ChallengeItemBean> requiredQuestions = valueToChallengeItemArray(
                storedConfiguration.readSetting(requiredChallenges, profileID), locale);
        final List<ChallengeItemBean> randomQuestions = valueToChallengeItemArray(
                storedConfiguration.readSetting(randomChallenges, profileID), locale);

        final List<Challenge> challenges = new ArrayList<>();

        if (requiredQuestions != null) {
            for (final ChallengeItemBean item : requiredQuestions) {
                if (item != null) {
                    final Challenge chaiChallenge = new ChaiChallenge(true, item.getText(), item.getMinLength(), item.getMaxLength(), item.isAdminDefined());
                    challenges.add(chaiChallenge);
                }
            }
        }

        if (randomQuestions != null) {
            for (final ChallengeItemBean item : randomQuestions) {
                if (item != null) {
                    final Challenge chaiChallenge = new ChaiChallenge(false, item.getText(), item.getMinLength(), item.getMaxLength(), item.isAdminDefined());
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
            LOGGER.warn("invalid challenge set configuration: " + e.getMessage());
        }
        return null;
    }

    static List<ChallengeItemBean> valueToChallengeItemArray(
            final StoredValue value,
            final Locale locale
    ) {
        if (!(value instanceof ChallengeValue)) {
            throw new IllegalArgumentException("may not read ChallengeValue value");
        }
        final Map<String, List<ChallengeItemBean>> storedValues = (Map<String, List<ChallengeItemBean>>)value.toNativeObject();
        final Map<Locale, List<ChallengeItemBean>> availableLocaleMap = new LinkedHashMap<>();
        for (final String localeStr : storedValues.keySet()) {
            availableLocaleMap.put(LocaleHelper.parseLocaleString(localeStr), storedValues.get(localeStr));
        }
        final Locale matchedLocale = LocaleHelper.localeResolver(locale, availableLocaleMap.keySet());

        return availableLocaleMap.get(matchedLocale);
    }

}
