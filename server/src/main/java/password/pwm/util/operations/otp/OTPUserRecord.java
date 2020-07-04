/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
