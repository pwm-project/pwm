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

public class PasswordCharCounter {
    private final CharSequence password;
    private final int passwordLength;

    public PasswordCharCounter(final CharSequence password) {
        this.password = password;
        this.passwordLength = password.length();
    }

    public int getNumericChars() {
        int numberOfNumericChars = 0;
        for (int i = 0; i < passwordLength; i++)
            if (Character.isDigit(password.charAt(i)))
                numberOfNumericChars++;

        return numberOfNumericChars;
    }

    public int getUpperChars() {
        int numberOfUpperChars = 0;
        for (int i = 0; i < passwordLength; i++)
            if (Character.isUpperCase(password.charAt(i)))
                numberOfUpperChars++;

        return numberOfUpperChars;
    }

    public int getAlphaChars() {
        int numberOfAlphaChars = 0;
        for (int i = 0; i < passwordLength; i++)
            if (Character.isLetter(password.charAt(i)))
                numberOfAlphaChars++;

        return numberOfAlphaChars;
    }

    public int getNonAlphaChars() {
        int numberOfNonAlphaChars = 0;
        for (int i = 0; i < passwordLength; i++)
            if (!Character.isLetter(password.charAt(i)))
                numberOfNonAlphaChars++;

        return numberOfNonAlphaChars;
    }

    public int getLowerChars() {
        int numberOfLowerChars = 0;
        for (int i = 0; i < passwordLength; i++)
            if (Character.isLowerCase(password.charAt(i)))
                numberOfLowerChars++;

        return numberOfLowerChars;
    }

    public int getSpecialChars() {
        int numberOfSpecial = 0;
        for (int i = 0; i < passwordLength; i++)
            if (!Character.isLetterOrDigit(password.charAt(i)))
                numberOfSpecial++;

        return numberOfSpecial;
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

    public int getOtherLetter() {
        int numberOfOtherLetter = 0;
        for (int i = 0, n = passwordLength; i < n; i++) {
            if (Character.getType(password.charAt(i)) == Character.OTHER_LETTER)
                numberOfOtherLetter++;
        }
        return numberOfOtherLetter;
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
}
