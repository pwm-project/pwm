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

import com.novell.ldapchai.exception.ImpossiblePasswordPolicyException;
import password.pwm.PwmApplication;
import password.pwm.PwmPasswordPolicy;
import password.pwm.PwmService;
import password.pwm.PwmSession;
import password.pwm.config.Configuration;
import password.pwm.config.PwmPasswordRule;
import password.pwm.config.PwmSetting;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.operations.PasswordUtility;
import password.pwm.util.stats.Statistic;
import password.pwm.wordlist.SeedlistManager;

import java.util.*;

/**
 * Random password generator
 *
 * @author Jason D. Rivard
 */
public class RandomPasswordGenerator {
// ------------------------------ FIELDS ------------------------------

    /**
     * Default seed phrases.  Most basic ASCII chars, except those that are visually ambiguous are
     * represented here.  No multi-character phrases are included.
     */
    public static final Set<String> DEFAULT_SEED_PHRASES = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "a", "b", "c", "d", "e", "f", "g", "h", "j", "k", "m", "n", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "a", "b", "c", "d", "e", "f", "g", "h", "j", "k", "m", "n", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "a", "b", "c", "d", "e", "f", "g", "h", "j", "k", "m", "n", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
            "2", "3", "4", "5", "6", "7", "8", "9",
            "@", "&", "!", "?", "%", "$", "#", "^", ")", "(", "+", "-", "=", ".", ",", "/", "\\"
    )));


    private static final SeedMachine DEFAULT_SEED_MACHINE = new SeedMachine(DEFAULT_SEED_PHRASES);

    private static final PwmRandom RANDOM = PwmRandom.getInstance();

    private static final PwmLogger LOGGER = PwmLogger.getLogger(RandomPasswordGenerator.class);

// -------------------------- STATIC METHODS --------------------------

    public static String createRandomPassword(
            final PwmSession pwmSession,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException
    {
        final PwmPasswordPolicy userPasswordPolicy = pwmSession.getUserInfoBean().getPasswordPolicy();
        return createRandomPassword(pwmSession, userPasswordPolicy, pwmApplication);
    }

    public static String createRandomPassword(
            final PwmSession pwmSession,
            final PwmPasswordPolicy passwordPolicy,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException
    {
        final RandomGeneratorConfig randomGeneratorConfig = new RandomGeneratorConfig();
        randomGeneratorConfig.setPasswordPolicy(passwordPolicy);

        return createRandomPassword(
                pwmSession,
                randomGeneratorConfig,
                pwmApplication
        );
    }


        /**
        * Creates a new password that satisfies the password rules.  All rules are checked for.  If for some
        * reason the RANDOM algorithm can not generate a valid password, null will be returned.
        * <p/>
        * If there is an identifiable reason the password can not be created (such as mis-configured rules) then
        * an {@link com.novell.ldapchai.exception.ImpossiblePasswordPolicyException} will be thrown.
        *
        * @param pwmSession A valid pwmSession
        * @param randomGeneratorConfig Policy to be used during generation
        * @param pwmApplication Used to get configuration, seedmanager and other services.
        * @return A randomly generated password value that meets the requirements of this {@code PasswordPolicy}
        * @throws com.novell.ldapchai.exception.ImpossiblePasswordPolicyException
        *          If there is no way to create a password using the configured rules and
        *          default seed phrase
        */
    public static String createRandomPassword(
            final PwmSession pwmSession,
            final RandomGeneratorConfig randomGeneratorConfig,
            final PwmApplication pwmApplication
    )
            throws PwmUnrecoverableException
    {
        final long startTimeMS = System.currentTimeMillis();

        if (randomGeneratorConfig.getSeedlistPhrases() == null || randomGeneratorConfig.getSeedlistPhrases().isEmpty()) {
            Set<String> seeds = DEFAULT_SEED_PHRASES;

            final SeedlistManager seedlistManager = pwmApplication.getSeedlistManager();
            if (seedlistManager != null && seedlistManager.status() == PwmService.STATUS.OPEN && seedlistManager.size() > 0) {
                seeds = new HashSet<String>();
                int safetyCounter = 0;
                while (seeds.size() < 10 && safetyCounter < 100) {
                    safetyCounter++;
                    final String randomWord = seedlistManager.randomSeed();
                    if (randomWord != null) {
                        seeds.add(randomWord);
                    }
                }
            }
            randomGeneratorConfig.setSeedlistPhrases(seeds);
        }


        final SeedMachine seedMachine = new SeedMachine(normalizeSeeds(randomGeneratorConfig.getSeedlistPhrases()));

        int tryCount = 0;
        final StringBuilder password = new StringBuilder();

        // determine the password policy to use for random generation
        final PwmPasswordPolicy randomGenPolicy;
        {
            final Map<String, String> newPolicyMap = new HashMap<String, String>();
            newPolicyMap.putAll(randomGeneratorConfig.getPasswordPolicy().getPolicyMap());
            if (randomGeneratorConfig.getMinimumLength() > randomGeneratorConfig.getPasswordPolicy().getRuleHelper().readIntValue(PwmPasswordRule.MinimumLength)) {
                newPolicyMap.put(PwmPasswordRule.MinimumLength.getKey(), String.valueOf(randomGeneratorConfig.getMinimumLength()));
            }
            if (randomGeneratorConfig.getMaximumLength() < randomGeneratorConfig.getPasswordPolicy().getRuleHelper().readIntValue(PwmPasswordRule.MaximumLength)) {
                newPolicyMap.put(PwmPasswordRule.MaximumLength.getKey(), String.valueOf(randomGeneratorConfig.getMaximumLength()));
            }
            if (randomGeneratorConfig.getMinimumStrength() > randomGeneratorConfig.getPasswordPolicy().getRuleHelper().readIntValue(PwmPasswordRule.MinimumStrength)) {
                newPolicyMap.put(PwmPasswordRule.MinimumStrength.getKey(), String.valueOf(randomGeneratorConfig.getMinimumStrength()));
            }
            randomGenPolicy = PwmPasswordPolicy.createPwmPasswordPolicy(newPolicyMap);
        }

        // initial creation
        password.append(generateNewPassword(seedMachine, randomGeneratorConfig.getMinimumLength()));

        // get a rule validator
        final PwmPasswordRuleValidator pwmPasswordRuleValidator = new PwmPasswordRuleValidator(pwmApplication, randomGenPolicy);

        // modify until it passes all the rules
        boolean validPassword = false;
        while (!validPassword && tryCount < randomGeneratorConfig.getMaximumTryCount()) {
            tryCount++;
            validPassword = true;

            final List<ErrorInformation> errors = pwmPasswordRuleValidator.internalPwmPolicyValidator(password.toString(), null, null);
            if (errors != null && !errors.isEmpty()) {
                validPassword = false;
                modifyPasswordBasedOnErrors(password, errors, seedMachine);
            } else if (pwmApplication != null && checkPasswordAgainstDisallowedHttpValues(pwmApplication.getConfig(),password.toString())) {
                validPassword = false;
                password.delete(0,password.length());
                password.append(generateNewPassword(seedMachine, randomGeneratorConfig.getMinimumLength()));
            }

        }

        // report outcome
        {
            final TimeDuration td = TimeDuration.fromCurrent(startTimeMS);
            if (validPassword) {
                LOGGER.trace(pwmSession, "finished random password generation in " + td.asCompactString() + " after " + tryCount + " tries.");
            } else {
                final List<ErrorInformation> errors = pwmPasswordRuleValidator.internalPwmPolicyValidator(password.toString(), null, null);
                final int judgeLevel = PasswordUtility.checkPasswordStrength(pwmApplication.getConfig(), password.toString());
                final StringBuilder sb = new StringBuilder();
                sb.append("failed random password generation after ").append(td.asCompactString()).append(" after ").append(tryCount).append(" tries. ");
                sb.append("(errors=").append(errors.size()).append(", judgeLevel=").append(judgeLevel);
                LOGGER.error(pwmSession, sb.toString());
            }
        }

        if (pwmApplication != null && pwmApplication.getStatisticsManager() != null) {
            pwmApplication.getStatisticsManager().incrementValue(Statistic.GENERATED_PASSWORDS);
        }

        final StringBuilder sb = new StringBuilder();
        sb.append("real-time random password generator called");
        sb.append(" (").append(TimeDuration.fromCurrent(startTimeMS).asCompactString());
        sb.append(")");
        LOGGER.trace(pwmSession, sb.toString());

        return password.toString();
    }

    private static void modifyPasswordBasedOnErrors(final StringBuilder password, final List<ErrorInformation> errors, final SeedMachine seedMachine) {
        if (password == null || errors == null || errors.isEmpty()) {
            return;
        }

        final Set<PwmError> errorMessages = new HashSet<PwmError>();
        for (final ErrorInformation errorInfo : errors) {
            errorMessages.add(errorInfo.getError());
        }

        boolean touched = false;

        if (errorMessages.contains(PwmError.PASSWORD_TOO_SHORT)) {
            addRandChar(password, seedMachine.getAllChars());
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_TOO_LONG)) {
            password.deleteCharAt(RANDOM.nextInt(password.length()));
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_FIRST_IS_NUMERIC) || errorMessages.contains(PwmError.PASSWORD_FIRST_IS_SPECIAL)) {
            password.deleteCharAt(0);
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_LAST_IS_NUMERIC) || errorMessages.contains(PwmError.PASSWORD_LAST_IS_SPECIAL)) {
            password.deleteCharAt(password.length() - 1);
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_NOT_ENOUGH_NUM)) {
            addRandChar(password, seedMachine.getNumChars());
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_NOT_ENOUGH_SPECIAL)) {
            addRandChar(password, seedMachine.getSpecialChars());
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_NOT_ENOUGH_UPPER)) {
            addRandChar(password, seedMachine.getUpperChars());
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_NOT_ENOUGH_LOWER)) {
            addRandChar(password, seedMachine.getLowerChars());
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_TOO_MANY_NUMERIC)) {
            deleteRandChar(password, seedMachine.getNumChars());
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_TOO_MANY_SPECIAL)) {
            deleteRandChar(password, seedMachine.getSpecialChars());
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_TOO_MANY_UPPER)) {
            deleteRandChar(password, seedMachine.getUpperChars());
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_TOO_MANY_LOWER)) {
            deleteRandChar(password, seedMachine.getLowerChars());
            touched = true;
        }

        if (errorMessages.contains(PwmError.PASSWORD_TOO_WEAK)) {
            randomPasswordModifier(password, seedMachine);
            touched = true;
        }

        if (!touched) { // dunno whats wrong, try just deleting a RANDOM char, and hope a re-insert will add another.
            randomPasswordModifier(password, seedMachine);
        }
    }

    private static void deleteRandChar(final StringBuilder password, final String charsToRemove)
            throws ImpossiblePasswordPolicyException {
        final List<Integer> removePossibilities = new ArrayList<Integer>();
        for (int i = 0; i < password.length(); i++) {
            final char loopChar = password.charAt(i);
            final int index = charsToRemove.indexOf(loopChar);
            if (index != -1) {
                removePossibilities.add(i);
            }
        }
        if (removePossibilities.isEmpty()) {
            throw new ImpossiblePasswordPolicyException(ImpossiblePasswordPolicyException.ErrorEnum.UNEXPECTED_ERROR);
        }
        final Integer charToDelete = removePossibilities.get(RANDOM.nextInt(removePossibilities.size()));
        password.deleteCharAt(charToDelete);
    }

    private static void randomPasswordModifier(final StringBuilder password, final SeedMachine seedMachine) {
        switch (RANDOM.nextInt(6)) {
            case 0:
            case 1:
                addRandChar(password, seedMachine.getSpecialChars());
                break;
            case 2:
            case 3:
                addRandChar(password, seedMachine.getNumChars());
                break;
            case 4:
                addRandChar(password, seedMachine.getUpperChars());
                break;
            case 5:
                addRandChar(password, seedMachine.getLowerChars());
                break;
            default:
                switchRandomCase(password);
                break;
        }
    }

    private static void switchRandomCase(final StringBuilder password) {
        for (int i = 0; i < password.length(); i++) {
            final int randspot = RANDOM.nextInt(password.length());
            final char oldChar = password.charAt(randspot);
            if (Character.isLetter(oldChar)) {
                final char newChar = Character.isUpperCase(oldChar) ? Character.toLowerCase(oldChar) : Character.toUpperCase(oldChar);
                password.deleteCharAt(randspot);
                password.insert(randspot, newChar);
                return;
            }
        }
    }

    private static void addRandChar(final StringBuilder password, final String allowedChars)
            throws ImpossiblePasswordPolicyException {
        final int insertPosition = RANDOM.nextInt(password.length());
        addRandChar(password, allowedChars, insertPosition);
    }

    private static void addRandChar(final StringBuilder password, final String allowedChars, final int insertPosition)
            throws ImpossiblePasswordPolicyException {
        if (allowedChars.length() < 1) {
            throw new ImpossiblePasswordPolicyException(ImpossiblePasswordPolicyException.ErrorEnum.REQUIRED_CHAR_NOT_ALLOWED);
        } else {
            final int newCharPosition = RANDOM.nextInt(allowedChars.length());
            final char charToAdd = allowedChars.charAt(newCharPosition);
            password.insert(insertPosition, charToAdd);
        }
    }

    private static boolean checkPasswordAgainstDisallowedHttpValues(final Configuration config, final String password) {
        if (config != null && password != null) {
            final List<String> disallowedInputs = config.readSettingAsStringArray(PwmSetting.DISALLOWED_HTTP_INPUTS);
            for (final String loopRegex : disallowedInputs) {
                if (password.matches(loopRegex)) {
                    return true;
                }
            }
        }
        return false;
    }

// --------------------------- CONSTRUCTORS ---------------------------

    private RandomPasswordGenerator() {
    }

// -------------------------- INNER CLASSES --------------------------

    protected static class SeedMachine {
        private final Collection<String> seeds;
        private final String allChars;
        private final String numChars;
        private final String specialChars;
        private final String upperChars;
        private final String lowerChars;

        public SeedMachine(final Collection<String> seeds) {
            this.seeds = seeds;

            {
                final StringBuilder sb = new StringBuilder();
                for (final String s : seeds) {
                    for (final Character c : s.toCharArray()) {
                        if (sb.indexOf(c.toString()) == -1) {
                            sb.append(c);
                        }
                    }
                }
                allChars = sb.length() > 2 ? sb.toString() : (new SeedMachine(DEFAULT_SEED_PHRASES)).getAllChars();
            }

            {
                final StringBuilder sb = new StringBuilder();
                for (final Character c : allChars.toCharArray()) {
                    if (Character.isDigit(c)) {
                        sb.append(c);
                    }
                }
                numChars = sb.length() > 2 ? sb.toString() : (new SeedMachine(DEFAULT_SEED_PHRASES)).getNumChars();
            }

            {
                final StringBuilder sb = new StringBuilder();
                for (final Character c : allChars.toCharArray()) {
                    if (!Character.isLetterOrDigit(c)) {
                        sb.append(c);
                    }
                }
                specialChars = sb.length() > 2 ? sb.toString() : (new SeedMachine(DEFAULT_SEED_PHRASES)).getSpecialChars();
            }

            {
                final StringBuilder sb = new StringBuilder();
                for (final Character c : allChars.toCharArray()) {
                    if (Character.isLowerCase(c)) {
                        sb.append(c);
                    }
                }
                lowerChars = sb.length() > 0 ? sb.toString() : (new SeedMachine(DEFAULT_SEED_PHRASES)).getLowerChars();
            }

            {
                final StringBuilder sb = new StringBuilder();
                for (final Character c : allChars.toCharArray()) {
                    if (Character.isUpperCase(c)) {
                        sb.append(c);
                    }
                }
                upperChars = sb.length() > 0 ? sb.toString() : (new SeedMachine(DEFAULT_SEED_PHRASES)).getUpperChars();
            }
        }

        public String getRandomSeed() {
            return new ArrayList<String>(seeds).get(RANDOM.nextInt(seeds.size()));
        }

        public String getAllChars() {
            return allChars;
        }

        public String getNumChars() {
            return numChars;
        }

        public String getSpecialChars() {
            return specialChars;
        }

        public String getUpperChars() {
            return upperChars;
        }

        public String getLowerChars() {
            return lowerChars;
        }
    }

    private static String generateNewPassword(final SeedMachine seedMachine, final int desiredLength) {
        final StringBuilder password = new StringBuilder();

        while (password.length() < (desiredLength - 1)) {//loop around until we're long enough
            password.append(seedMachine.getRandomSeed());
        }

        if (RANDOM.nextInt(3) == 0) {
            addRandChar(password, DEFAULT_SEED_MACHINE.getNumChars(), RANDOM.nextInt(password.length()));
        }

        if (RANDOM.nextBoolean()) {
            switchRandomCase(password);
        }

        return password.toString();
    }

    private static Collection<String> normalizeSeeds(final Collection<String> inputSeeds) {

        if (inputSeeds == null) {
            return DEFAULT_SEED_PHRASES;
        }

        final Collection<String> newSeeds = new HashSet<String>();
        newSeeds.addAll(inputSeeds);

        for (Iterator<String> iter = newSeeds.iterator(); iter.hasNext();) {
            final String s = iter.next();
            if (s == null || s.length() < 1) {
                iter.remove();
            }
        }

        return newSeeds.isEmpty() ? DEFAULT_SEED_PHRASES : newSeeds;
    }

    public static class RandomGeneratorConfig {
        public static final int DEFAULT_MINIMUM_LENGTH = 6;
        public static final int DEFAULT_MAXIMUM_LENGTH = 16;
        public static final int DEFAULT_DESIRED_STRENGTH = 45;
        public static final int DEFAULT_MAXIMUM_TRY_COUNT = 1000;

        public static final int MINIMUM_STRENGTH = 0;
        public static final int MAXIMUM_STRENGTH = 100;

        private Collection<String> seedlistPhrases = Collections.emptySet();
        private int minimumLength = DEFAULT_MINIMUM_LENGTH;
        private int maximumLength = DEFAULT_MAXIMUM_LENGTH;
        private int minimumStrength = DEFAULT_DESIRED_STRENGTH;
        private int maximumTryCount = DEFAULT_MAXIMUM_TRY_COUNT;
        private PwmPasswordPolicy passwordPolicy = PwmPasswordPolicy.defaultPolicy();

        public Collection<String> getSeedlistPhrases() {
            return seedlistPhrases;
        }

        /**
         * @param seedlistPhrases A set of phrases (Strings) used to generate the RANDOM passwords.  There must be enough
         *                        values in the phrases to build a resonably RANDOM password that meets rule requirements
         */
        public void setSeedlistPhrases(final Collection<String> seedlistPhrases) {
            this.seedlistPhrases = seedlistPhrases;
        }

        public int getMinimumLength() {
            return minimumLength;
        }

        /**
         * @param minimumLength The minimum length desired for the password.  The algorith will attempt to make
         *                      the returned value at least this long, but it is not guarenteed.
         */
        public void setMinimumLength(final int minimumLength) {
            this.minimumLength = minimumLength;
        }

        public int getMaximumLength() {
            return maximumLength;
        }

        public void setMaximumLength(final int maximumLength) {
            this.maximumLength = maximumLength;
        }

        public int getMinimumStrength() {
            return minimumStrength;
        }

        /**
         * @param minimumStrength The minimum length desired strength.  The algorith will attempt to make
         *                        the returned value at least this strong, but it is not guarenteed.
         */
        public void setMinimumStrength(final int minimumStrength) {
            int desiredStrength = minimumStrength > MAXIMUM_STRENGTH ? MAXIMUM_STRENGTH : minimumStrength;
            desiredStrength = desiredStrength < MINIMUM_STRENGTH ? MINIMUM_STRENGTH : desiredStrength;
            this.minimumStrength = desiredStrength;
        }

        public int getMaximumTryCount() {
            return maximumTryCount;
        }

        public void setMaximumTryCount(final int maximumTryCount) {
            this.maximumTryCount = maximumTryCount;
        }

        public PwmPasswordPolicy getPasswordPolicy() {
            return passwordPolicy;
        }

        public void setPasswordPolicy(final PwmPasswordPolicy passwordPolicy) {
            this.passwordPolicy = passwordPolicy;
        }
    }
}
