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

package password.pwm.cr.storage;

public enum StoredResponseFormatType {
    TEXT(new StoredCrTextResponse.TextAnswerFactory()),
    MD5(new StoredHashSaltResponse.HashSaltAnswerFactory()),
    SHA1(new StoredHashSaltResponse.HashSaltAnswerFactory()),
    SHA1_SALT(new StoredHashSaltResponse.HashSaltAnswerFactory()),
    SHA256_SALT(new StoredHashSaltResponse.HashSaltAnswerFactory()),
    SHA512_SALT(new StoredHashSaltResponse.HashSaltAnswerFactory()),
    BCRYPT(new StoredCrCryptResponse.PasswordCryptAnswerFactory()),
    SCRYPT(new StoredCrCryptResponse.PasswordCryptAnswerFactory()),
    PBKDF2(new StoredCrPKDBF2Response.PKDBF2AnswerFactory()),
    HELPDESK(new StoredHelpdeskResponse.StoredHelpdeskResponseFactory()),
    NMAS(null),
    ;

    private StoredResponse.ImplementationFactory factory;


    StoredResponseFormatType(final StoredResponse.ImplementationFactory implementationClass) {
        this.factory = implementationClass;
    }

    public StoredResponse.ImplementationFactory getFactory() {
        return factory;
    }
}
