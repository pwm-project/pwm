/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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
import password.pwm.PasswordUtility;
import password.pwm.PwmSession;
import password.pwm.Validator;
import password.pwm.config.Message;
import password.pwm.error.ErrorInformation;
import password.pwm.wordlist.SeedlistManager;
import password.pwm.wordlist.WordlistManager;
import password.pwm.wordlist.WordlistStatus;

import java.security.SecureRandom;
import java.util.*;

/**
 * Random password generator
 * @author Jason D. Rivard
 */
public class RandomPasswordGenerator {
// ------------------------------ FIELDS ------------------------------

    /**
     * Default seed phrases.  Most basic ASCII chars, except those that are visually ambiguous are
     * respresented here.  No multi-character phrases are included.
     */
    public static final List<String> DEFAULT_SEED_PHRASES = Collections.unmodifiableList(new ArrayList<String>(Arrays.asList(
            "a", "b", "c", "d", "e", "f", "g", "h", "j", "k", "m", "n", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "a", "b", "c", "d", "e", "f", "g", "h", "j", "k", "m", "n", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "a", "b", "c", "d", "e", "f", "g", "h", "j", "k", "m", "n", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",
            "A", "B", "C", "D", "E", "F", "G", "H", "J", "K", "L", "M", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
            "2", "3", "4", "5", "6", "7", "8", "9",
            "@", "&", "!", "?", "%", "$", "#", "^", ")", "(", "+", "-", "=", ".", ",", "/", "\\"
    )));

    public static final int DEFAULT_LENGTH = 8;
    public static final int DEFAULT_DESIRED_STRENGTH = 4;

    public static final int MINIMUM_STRENGTH = 0;
    public static final int MAXIMUM_STRENGTH = 9;

    private static final int MAXIMUM_TRY_COUNT = 1000;

    private static final Random random = new SecureRandom();

    private static final PwmLogger LOGGER = PwmLogger.getLogger(RandomPasswordGenerator.class);

// -------------------------- STATIC METHODS --------------------------

    public static String createRandomPassword(final PwmSession pwmSession)
    {
        List<String> seeds = DEFAULT_SEED_PHRASES;

        final SeedlistManager slMgr = pwmSession.getContextManager().getSeedlistManager();
        if (slMgr != null && slMgr.getStatus() != WordlistStatus.CLOSED && slMgr.size() > 0) {
            seeds = new LinkedList<String>();
            while (seeds.size() < 10) {
                final String randomWord = slMgr.randomSeed();
                if (randomWord != null) {
                    seeds.add(slMgr.randomSeed());
                } else {
                    break;
                }
            }
        }

        return createRandomPassword(seeds, DEFAULT_LENGTH, DEFAULT_DESIRED_STRENGTH, pwmSession);
    }


    /**
     * Creates a new password that satisfies the password rules.  All rules are checked for.  If for some
     * reason the random algortithm can not generate a valid password, null will be returned.
     * <p/>
     * If there is an identifiable reason the password can not be created (such as mis-configured rules) then
     * an {@link com.novell.ldapchai.exception.ImpossiblePasswordPolicyException} will be thrown.
     *
     * @param seedPhrases   A set of phrases (Strings) used to generate the random passwords.  There must be enough
     *                      values in the phrases to build a resonably random password that meets rule requirements
     * @param desiredLength The minimum length desired for the password.  The algorith will attempt to make
     *                      the returned value at least this long, but it is not guarenteed.
     * @param desiredStrength The minimum length desired strength.  The algorith will attempt to make
     *                      the returned value at least this strong, but it is not guarenteed.
     * @param pwmSession    A valid pwmSession
     * @return A randomly generated password value that meets the requirements of this {@code PasswordPolicy}
     * @throws com.novell.ldapchai.exception.ImpossiblePasswordPolicyException
     *          If there is no way to create a password using the configured rules and
     *          default seed phrase
     */
    private static String createRandomPassword(
            final Collection<String> seedPhrases,
            final int desiredLength,
            int desiredStrength,
            final PwmSession pwmSession
    )
    {
        final long startTimeMS = System.currentTimeMillis();

        final SeedMachine seedMachine = new SeedMachine(normalizeSeeds(seedPhrases));
        final WordlistManager wordlistManager = pwmSession.getContextManager().getWordlistManager();

        desiredStrength = desiredStrength > MAXIMUM_STRENGTH ? MAXIMUM_STRENGTH : desiredStrength;
        desiredStrength = desiredStrength < MINIMUM_STRENGTH ? MINIMUM_STRENGTH : desiredStrength;

        int tryCount = 0;
        boolean validPassword = false;
        final StringBuilder password = new StringBuilder();

        //initial creation
        password.append(generateNewPassword(seedMachine,  desiredLength));

        while (!validPassword && tryCount < MAXIMUM_TRY_COUNT) {
            tryCount++;
            validPassword = true;
            final List<ErrorInformation> errors = Validator.pwmPasswordPolicyValidator(password.toString(),pwmSession,false,pwmSession.getUserInfoBean().getPasswordPolicy());
            if (errors != null && !errors.isEmpty()) {
                validPassword = false;
                modifyPasswordBasedOnErrors(password, errors, seedMachine);
            }

            final int judgeLevel = PasswordUtility.judgePassword(password.toString());
            if (judgeLevel < desiredStrength) {
                validPassword = false;
                randomPasswordModifier(password, seedMachine);
            }

            if (validPassword && wordlistManager != null) {
                if (wordlistManager.containsWord(pwmSession,password.toString())) {
                    validPassword = false;
                    LOGGER.trace(pwmSession, "rejected random password due to wordlist check");
                }
            }
        }

        {
            final TimeDuration td = TimeDuration.fromCurrent(startTimeMS);
            if (validPassword) {
                LOGGER.trace(pwmSession, "finished random password generation in " + td.asCompactString() + " after " + tryCount + " tries.");
            } else {
                final List<ErrorInformation> errors = Validator.pwmPasswordPolicyValidator(password.toString(),pwmSession,false,pwmSession.getUserInfoBean().getPasswordPolicy());
                final int judgeLevel = PasswordUtility.judgePassword(password.toString());
                final StringBuilder sb = new StringBuilder();
                sb.append("failed random password generation after ").append(td.asCompactString()).append(" after ").append(tryCount).append(" tries. ");
                sb.append("(errors=").append(errors.size()).append(", judgeLevel=").append(judgeLevel);
                LOGGER.trace(pwmSession, sb.toString());
            }
        }

        return password.toString();
    }

    private static void modifyPasswordBasedOnErrors(final StringBuilder password, final List<ErrorInformation> errors, final SeedMachine seedMachine) {
        if (password == null || errors == null || errors.isEmpty()) {
            return;
        }

        final Set<Message> errorMessages = new HashSet<Message>();
        for (final ErrorInformation errorInfo : errors) {
            errorMessages.add(errorInfo.getError());
        }

        boolean touched = false;

        if (errorMessages.contains(Message.PASSWORD_TOO_SHORT)) {
            addRandChar(password, seedMachine.getAllChars());
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_TOO_LONG)) {
            password.deleteCharAt(random.nextInt(password.length()));
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_FIRST_IS_NUMERIC) || errorMessages.contains(Message.PASSWORD_FIRST_IS_SPECIAL)) {
            password.deleteCharAt(0);
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_LAST_IS_NUMERIC) || errorMessages.contains(Message.PASSWORD_LAST_IS_SPECIAL)) {
            password.deleteCharAt(password.length() - 1);
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_NOT_ENOUGH_NUM)) {
            addRandChar(password, seedMachine.getNumChars());
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_NOT_ENOUGH_SPECIAL)) {
            addRandChar(password, seedMachine.getSpecialChars());
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_NOT_ENOUGH_UPPER)) {
            addRandChar(password, seedMachine.getUpperChars());
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_NOT_ENOUGH_LOWER)) {
            addRandChar(password, seedMachine.getLowerChars());
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_TOO_MANY_NUMERIC)) {
            deleteRandChar(password, seedMachine.getNumChars());
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_TOO_MANY_SPECIAL)) {
            deleteRandChar(password, seedMachine.getSpecialChars());
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_TOO_MANY_UPPER)) {
            deleteRandChar(password, seedMachine.getUpperChars());
            touched = true;
        }

        if (errorMessages.contains(Message.PASSWORD_TOO_MANY_LOWER)) {
            deleteRandChar(password, seedMachine.getLowerChars());
            touched = true;
        }

        if (!touched) { // dunno whats wrong, try just deleting a random char, and hope a re-insert will add another.
            randomPasswordModifier(password, seedMachine);
        }
    }

    protected static void deleteRandChar(final StringBuilder password, final String charsToRemove)
            throws ImpossiblePasswordPolicyException
    {
        final List<Integer> removePossibilies = new ArrayList<Integer>();
        for (int i = 0; i < password.length(); i++) {
            final char loopChar = password.charAt(i);
            final int index = charsToRemove.indexOf(loopChar);
            if (index != -1) {
                removePossibilies.add(i);
            }
        }
        if (removePossibilies.isEmpty()) {
            throw new ImpossiblePasswordPolicyException(ImpossiblePasswordPolicyException.ErrorEnum.UNEXPECTED_ERROR);
        }
        final Integer charToDelete = removePossibilies.get(random.nextInt(removePossibilies.size()));
        password.deleteCharAt(charToDelete);
    }

    private static void randomPasswordModifier(final StringBuilder password, final SeedMachine seedMachine) {
        switch (random.nextInt(3)) {
            case 0:
                addRandChar(password, seedMachine.getSpecialChars());
                break;
            case 1:
                addRandChar(password, seedMachine.getNumChars());
                break;
            case 2:  // switch case
                switchRandomCase(password);
        }
    }

    private static void switchRandomCase(final StringBuilder password) {
        for (int i = 0; i < password.length(); i++) {
            final int randspot = random.nextInt(password.length());
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
            throws ImpossiblePasswordPolicyException
    {
        final int insertPosition = random.nextInt(password.length());
        addRandChar(password, allowedChars, insertPosition);
    }

    private static void addRandChar(final StringBuilder password, final String allowedChars, final int insertPosition)
            throws ImpossiblePasswordPolicyException
    {
        if (allowedChars.length() < 1) {
            throw new ImpossiblePasswordPolicyException(ImpossiblePasswordPolicyException.ErrorEnum.REQUIRED_CHAR_NOT_ALLOWED);
        } else {
            final int newCharPosition = random.nextInt(allowedChars.length());
            final char charToAdd = allowedChars.charAt(newCharPosition);
            password.insert(insertPosition, charToAdd);
        }
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

        public SeedMachine(final Collection<String> seeds)
        {
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

        public String getRandomSeed()
        {
            return new ArrayList<String>(seeds).get(random.nextInt(seeds.size()));
        }

        public String getAllChars()
        {
            return allChars;
        }

        public String getNumChars()
        {
            return numChars;
        }

        public String getSpecialChars()
        {
            return specialChars;
        }

        public String getUpperChars()
        {
            return upperChars;
        }

        public String getLowerChars()
        {
            return lowerChars;
        }
    }

    private static String generateNewPassword(final SeedMachine seedMachine, final int desiredLength) {
        final StringBuilder password = new StringBuilder();

        password.append(seedMachine.getRandomSeed());

        while (password.length() < desiredLength) {//loop around until we're long enugh.
            if (random.nextBoolean()) {
                addRandChar(password, seedMachine.getNumChars(), password.length());
            } else {
                addRandChar(password, seedMachine.getSpecialChars(), password.length());
            }
            password.append(seedMachine.getRandomSeed());
        }

        switchRandomCase(password);

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
}
