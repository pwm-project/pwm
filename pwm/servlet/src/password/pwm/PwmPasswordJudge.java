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

package password.pwm;

import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordCharCounter;

public class PwmPasswordJudge implements ExternalJudgeMethod {
    /**
     * Judge a password's strength
     *
     * @param password password to check
     * @return 0-100, 0 being a very week password, 100 being a strong password.
     */
    public int judgePassword(final Configuration config, final String password) {
        if (password == null || password.length() < 1) {
            return 0;
        }

        int score = 0;
        final PasswordCharCounter charCounter = new PasswordCharCounter(password);

        // -- Additions --
        // amount of unique chars
        if (charCounter.getUniqueChars() > 7) {
            score = score + 10;
        }
        score = score + ((charCounter.getUniqueChars()) * 3);

        // Numbers
        if (charCounter.getNumericChars() > 0) {
            score = score + 8;
            score = score + (charCounter.getNumericChars()) * 4;
        }

        // specials
        if (charCounter.getSpecialChars() > 0) {
            score = score + 14;
            score = score + (charCounter.getSpecialChars()) * 5;
        }

        // mixed case
        if ((charCounter.getAlphaChars() != charCounter.getUpperChars()) && (charCounter.getAlphaChars() != charCounter.getLowerChars())) {
            score = score + 10;
        }

        // -- Deductions --

        // sequential numbers
        if (charCounter.getSequentialNumericChars() > 2) {
            score = score - (charCounter.getSequentialNumericChars() - 1) * 4;
        }

        // sequential chars
        if (charCounter.getSequentialRepeatedChars() > 1) {
            score = score - (charCounter.getSequentialRepeatedChars()) * 5;
        }

        return score > 100 ? 100 : score < 0 ? 0 : score;
    }
}
