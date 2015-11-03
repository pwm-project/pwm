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

import java.io.Serializable;

public class StoredResponseBean implements Serializable {
    public StoredResponseFormatType type;
    public String answerText;
    public String answerHash;
    public String salt;
    public int hashCount;
    public boolean caseInsensitive;

    public StoredResponseFormatType getType() {
        return type;
    }

    public String getAnswerText() {
        return answerText;
    }

    public String getAnswerHash() {
        return answerHash;
    }
    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public int getHashCount() {
        return hashCount;
    }

    public boolean isCaseInsensitive() {
        return caseInsensitive;
    }
}
