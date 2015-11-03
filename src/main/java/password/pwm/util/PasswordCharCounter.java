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

package password.pwm.util;

public class PasswordCharCounter {
    private final String password;
    private final int passwordLength;

    public PasswordCharCounter(final String password) {
        this.password = password;
        this.passwordLength = password.length();
    }

    public int getNumericCharCount() {
        return getNumericChars().length();
    }

    public String getNumericChars() {
        return returnCharsOfType(password, CharType.NUMBER);
    }

    public int getUpperCharCount() {
        return getUpperChars().length();
    }

    public String getUpperChars() {
        return returnCharsOfType(password, CharType.UPPERCASE);
    }

    public int getAlphaCharCount() {
        return getAlphaChars().length();
    }

    public String getAlphaChars() {
        return returnCharsOfType(password, CharType.LETTER);
    }

    public int getNonAlphaCharCount() {
        return getNonAlphaChars().length();
    }

    public String getNonAlphaChars() {
        return returnCharsOfType(password, CharType.NON_LETTER);
    }

    public int getLowerCharCount() {
        return getLowerChars().length();
    }

    public String getLowerChars() {
        return returnCharsOfType(password, CharType.LOWERCASE);
    }

    public int getSpecialCharsCount() {
        return getSpecialChars().length();
    }

    public String getSpecialChars() {
        return returnCharsOfType(password, CharType.SPECIAL);
    }

    public int getRepeatedChars() {
        int numberOfRepeats = 0;
        final CharSequence passwordL = password.toString().toLowerCase();

        for (int i = 0; i < passwordLength - 1; i++) {
            int loopRepeats = 0;
            final char loopChar = passwordL.charAt(i);
            for (int j = i; j < passwordLength; j++)
                if (loopChar == passwordL.charAt(j))
                    loopRepeats++;

            if (loopRepeats > numberOfRepeats)
                numberOfRepeats = loopRepeats;
        }
        return numberOfRepeats;
    }

    public int getSequentialRepeatedChars() {
        int numberOfRepeats = 0;
        final CharSequence passwordL = password.toString().toLowerCase();

        for (int i = 0; i < passwordLength - 1; i++) {
            int loopRepeats = 0;
            final char loopChar = passwordL.charAt(i);
            for (int j = i; j < passwordLength; j++)
                if (loopChar == passwordL.charAt(j))
                    loopRepeats++;
                else
                    break;

            if (loopRepeats > numberOfRepeats)
                numberOfRepeats = loopRepeats;
        }
        return numberOfRepeats;
    }

    public int getSequentialNumericChars() {
        int numberOfRepeats = 0;

        for (int i = 0; i < passwordLength - 1; i++) {
            int loopRepeats = 0;
            for (int j = i; j < passwordLength; j++)
                if (Character.isDigit(password.charAt(j)))
                    loopRepeats++;
                else
                    break;
            if (loopRepeats > numberOfRepeats)
                numberOfRepeats = loopRepeats;
        }
        return numberOfRepeats;
    }

    public int getSequentialAlphaChars() {
        int numberOfRepeats = 0;

        for (int i = 0; i < passwordLength - 1; i++) {
            int loopRepeats = 0;
            for (int j = i; j < passwordLength; j++)
                if (Character.isLetter(password.charAt(j)))
                    loopRepeats++;
                else
                    break;
            if (loopRepeats > numberOfRepeats)
                numberOfRepeats = loopRepeats;
        }
        return numberOfRepeats;
    }

    public int getUniqueChars() {
        final StringBuilder sb = new StringBuilder();
        final String passwordL = password.toString().toLowerCase();
        for (int i = 0; i < passwordLength; i++) {
            final char loopChar = passwordL.charAt(i);
            if (sb.indexOf(String.valueOf(loopChar)) == -1)
                sb.append(loopChar);
        }
        return sb.length();
    }

    public int getOtherLetterCharCount() {
        return getOtherLetterChars().length();
    }

    public String getOtherLetterChars() {
        return returnCharsOfType(password, CharType.OTHER_LETTER);
    }

    public boolean isFirstNumeric() {
        return password.length() > 0 && Character.isDigit(password.charAt(0));
    }

    public boolean isLastNumeric() {
        return password.length() > 0 && Character.isDigit(password.charAt(password.length() - 1));
    }

    public boolean isFirstSpecial() {
        return password.length() > 0 && !Character.isLetterOrDigit(password.charAt(0));
    }

    public boolean isLastSpecial() {
        return password.length() > 0 && !Character.isLetterOrDigit(password.charAt(password.length() - 1));
    }

    private static String returnCharsOfType(final String input, final CharType charType) {
        final int passwordLength = input.length();
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passwordLength; i++) {
            final char nextChar = input.charAt(i);
            if (charType.getCharTester().isType(nextChar)) {
                sb.append(nextChar);
            }
        }
        return sb.toString();
    }

    private enum CharType {
        UPPERCASE(new CharTester() {
            public boolean isType(final char character)
            {
                return Character.isUpperCase(character);
            }
        }),
        LOWERCASE(new CharTester() {
            public boolean isType(final char character)
            {
                return Character.isLowerCase(character);
            }
        }),
        SPECIAL(new CharTester() {
            public boolean isType(final char character)
            {
                return !Character.isLetterOrDigit(character);
            }
        }),
        NUMBER(new CharTester() {
            public boolean isType(final char character)
            {
                return Character.isDigit(character);
            }
        }),
        LETTER(new CharTester() {
            public boolean isType(final char character)
            {
                return Character.isLetter(character);
            }
        }),
        NON_LETTER(new CharTester() {
            public boolean isType(final char character)
            {
                return !Character.isLetter(character);
            }
        }),
        OTHER_LETTER(new CharTester() {
            public boolean isType(final char character)
            {
                return Character.getType(character) == Character.OTHER_LETTER;
            }
        }),


        ;

        private final CharTester charTester;

        CharType(CharTester charClassType)
        {
            this.charTester = charClassType;
        }

        public CharTester getCharTester()
        {
            return charTester;
        }
    }

    private interface CharTester {
        boolean isType(final char character);
    }
}
