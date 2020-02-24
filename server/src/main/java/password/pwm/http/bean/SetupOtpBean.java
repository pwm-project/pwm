/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.http.bean;

import password.pwm.config.option.SessionBeanMode;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.operations.otp.OTPUserRecord;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SetupOtpBean extends PwmSessionBean
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( SetupOtpBean.class );

    private OTPUserRecord otpUserRecord;
    private boolean confirmed;
    private boolean codeSeen;
    private boolean written;
    private List<String> recoveryCodes;

    // for HOTP only
    private Long challenge;
    private boolean hasPreExistingOtp;

    public SetupOtpBean( )
    {
    }

    public OTPUserRecord getOtpUserRecord( )
    {
        return otpUserRecord;
    }

    public boolean isHasPreExistingOtp( )
    {
        return hasPreExistingOtp;
    }

    public void setHasPreExistingOtp( final boolean hasPreExistingOtp )
    {
        this.hasPreExistingOtp = hasPreExistingOtp;
    }

    public void setOtpUserRecord( final OTPUserRecord otp )
    {
        this.otpUserRecord = otp;
    }

    public boolean isConfirmed( )
    {
        return confirmed;
    }

    public void setConfirmed( final boolean confirmed )
    {
        this.confirmed = confirmed;
    }

    public Long getChallenge( )
    {
        if ( challenge == null )
        {
            SecureRandom random;
            try
            {
                random = SecureRandom.getInstance( "SHA1PRNG", "SUN" );
            }
            catch ( final NoSuchAlgorithmException | NoSuchProviderException ex )
            {
                random = new SecureRandom();
                LOGGER.error( () -> ex.getMessage(), ex );
            }
            random.setSeed( ( new Date() ).getTime() );
            challenge = random.nextLong() % ( 10 ^ 6 );
        }
        return challenge;
    }

    public void setChallenge( final Long challenge )
    {
        this.challenge = challenge;
    }

    public List<String> getRecoveryCodes( )
    {
        return recoveryCodes;
    }

    public void setRecoveryCodes( final List<String> recoveryCodes )
    {
        this.recoveryCodes = recoveryCodes;
    }

    public boolean isCodeSeen( )
    {
        return codeSeen;
    }

    public void setCodeSeen( final boolean codeSeen )
    {
        this.codeSeen = codeSeen;
    }

    public boolean isWritten( )
    {
        return written;
    }

    public void setWritten( final boolean written )
    {
        this.written = written;
    }

    public Type getType( )
    {
        return Type.AUTHENTICATED;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.unmodifiableSet( new HashSet<>( Arrays.asList( SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE ) ) );
    }

}
