/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.util.operations.otp;

import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OTPUserRecord implements Serializable
{

    private static final String CURRENT_VERSION = "1";

    private Instant timestamp = Instant.now();
    private String identifier;
    private String secret;
    private List<RecoveryCode> recoveryCodes = new ArrayList<>();
    private RecoveryInfo recoveryInfo;
    private long attemptCount = 0;
    private Type type = Type.TOTP;
    private String version = CURRENT_VERSION;

    public static class RecoveryInfo implements Serializable
    {
        private String salt;
        private String hashMethod;
        private int hashCount;

        public String getSalt( )
        {
            return salt;
        }

        public void setSalt( final String salt )
        {
            this.salt = salt;
        }

        public String getHashMethod( )
        {
            return hashMethod;
        }

        public void setHashMethod( final String hashMethod )
        {
            this.hashMethod = hashMethod;
        }

        public int getHashCount( )
        {
            return hashCount;
        }

        public void setHashCount( final int hashCount )
        {
            this.hashCount = hashCount;
        }
    }

    public enum Type
    {
        // NOT currently used!
        HOTP,

        TOTP,
    }

    public static class RecoveryCode implements Serializable
    {
        private String hashCode;
        private boolean used;

        public String getHashCode( )
        {
            return hashCode;
        }

        public void setHashCode( final String hashCode )
        {
            this.hashCode = hashCode;
        }

        public boolean isUsed( )
        {
            return used;
        }

        public void setUsed( final boolean used )
        {
            this.used = used;
        }
    }

    public String getIdentifier( )
    {
        return identifier;
    }

    public void setIdentifier( final String identifier )
    {
        this.identifier = identifier;
    }

    public String getSecret( )
    {
        return secret;
    }

    public void setSecret( final String secret )
    {
        this.secret = secret;
    }

    public List<RecoveryCode> getRecoveryCodes( )
    {
        return recoveryCodes;
    }

    public void setRecoveryCodes( final List<RecoveryCode> recoveryCodes )
    {
        this.recoveryCodes = recoveryCodes;
    }

    public Type getType( )
    {
        return type;
    }

    public void setType( final Type type )
    {
        this.type = type;
    }

    public Instant getTimestamp( )
    {
        return timestamp;
    }

    public void setTimestamp( final Instant timestamp )
    {
        this.timestamp = timestamp;
    }

    public String getVersion( )
    {
        return version;
    }

    public void setVersion( final String version )
    {
        this.version = version;
    }

    public long getAttemptCount( )
    {
        return attemptCount;
    }

    public void setAttemptCount( final long attemptCount )
    {
        this.attemptCount = attemptCount;
    }

    public RecoveryInfo getRecoveryInfo( )
    {
        return recoveryInfo;
    }

    public void setRecoveryInfo( final RecoveryInfo recoveryInfo )
    {
        this.recoveryInfo = recoveryInfo;
    }
}
