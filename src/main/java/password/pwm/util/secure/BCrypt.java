/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2016 The PWM Project
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

package password.pwm.util.secure;

import org.bouncycastle.crypto.generators.OpenBSDBCrypt;

import java.security.SecureRandom;

public class BCrypt {
    public static String hashPassword(final String password) {
        final int bcryptRounds = 10;
        final byte[] salt = new byte[16];
        (new SecureRandom()).nextBytes(salt);
        return OpenBSDBCrypt.generate(password.toLowerCase().toCharArray(), salt, bcryptRounds);
    }

    public static boolean testAnswer(final String password, final String hashedPassword) {
        return OpenBSDBCrypt.checkPassword(hashedPassword, password.toLowerCase().toCharArray());
    }
}
