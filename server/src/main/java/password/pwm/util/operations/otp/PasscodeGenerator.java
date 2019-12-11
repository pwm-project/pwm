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

package password.pwm.util.operations.otp;

import javax.crypto.Mac;
import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

/**
 * <p>An implementation of the HOTP generator specified by RFC 4226. Generates
 * short passcodes that may be used in challenge-response protocols or as
 * timeout passcodes that are only valid for a short period.</p>
 *
 * <p>The default passcode is a 6-digit decimal code and the default timeout
 * period is 5 minutes.</p>
 *
 * @author sweis@google.com (Steve Weis)
 *         http://code.google.com/p/google-authenticator/
 */
public class PasscodeGenerator
{
    /**
     * Default decimal passcode length.
     */
    private static final int PASS_CODE_LENGTH = 6;

    /**
     * Default passcode timeout period (in seconds).
     */
    private static final int INTERVAL = 30;

    /**
     * The number of previous and future intervals to check.
     */
    private static final int ADJACENT_INTERVALS = 1;

    private static final int PIN_MODULO =
            ( int ) Math.pow( 10, PASS_CODE_LENGTH );

    private final Signer signer;
    private final int codeLength;
    private final int intervalPeriod;

    /*
     * Using an interface to allow us to inject different signature
     * implementations.
     */
    interface Signer
    {
        byte[] sign( byte[] data ) throws GeneralSecurityException;
    }

    /**
     * @param mac A {@link Mac} used to generate passcodes
     */
    public PasscodeGenerator( final Mac mac )
    {
        this( mac, PASS_CODE_LENGTH, INTERVAL );
    }

    /**
     * @param mac            A {@link Mac} used to generate passcodes
     * @param passCodeLength The length of the decimal passcode
     * @param interval       The interval that a passcode is valid for
     */
    public PasscodeGenerator( final Mac mac, final int passCodeLength, final int interval )
    {
        this( new Signer()
        {
            public byte[] sign( final byte[] data )
            {
                return mac.doFinal( data );
            }
        }, passCodeLength, interval );
    }

    public PasscodeGenerator( final Signer signer, final int passCodeLength, final int interval )
    {
        this.signer = signer;
        this.codeLength = passCodeLength;
        this.intervalPeriod = interval;
    }

    private String padOutput( final int value )
    {
        String result = Integer.toString( value );
        for ( int i = result.length(); i < codeLength; i++ )
        {
            result = "0" + result;
        }
        return result;
    }

    /**
     * @return A decimal timeout code
     *
     * @throws GeneralSecurityException if a security exception is generated
     */
    public String generateTimeoutCode( ) throws GeneralSecurityException
    {
        return generateResponseCode( clock.getCurrentInterval() );
    }

    /**
     * @param challenge A long-valued challenge
     * @return A decimal response code
     * @throws GeneralSecurityException If a JCE exception occur
     */
    public String generateResponseCode( final long challenge )
            throws GeneralSecurityException
    {
        final byte[] value = ByteBuffer.allocate( 8 ).putLong( challenge ).array();
        return generateResponseCode( value );
    }

    /**
     * @param challenge An arbitrary byte array used as a challenge
     * @return A decimal response code
     * @throws GeneralSecurityException If a JCE exception occur
     */
    public String generateResponseCode( final byte[] challenge )
            throws GeneralSecurityException
    {
        final byte[] hash = signer.sign( challenge );

        // Dynamically truncate the hash
        // OffsetBits are the low order bits of the last byte of the hash
        final int offset = hash[ hash.length - 1 ] & 0xF;
        // Grab a positive integer value starting at the given offset.
        final int truncatedHash = hashToInt( hash, offset ) & 0x7FFFFFFF;
        final int pinValue = truncatedHash % PIN_MODULO;
        return padOutput( pinValue );
    }

    /**
     * Grabs a positive integer value from the input array starting at
     * the given offset.
     *
     * @param bytes the array of bytes
     * @param start the index into the array to start grabbing bytes
     * @return the integer constructed from the four bytes in the array
     */
    private int hashToInt( final byte[] bytes, final int start )
    {
        final DataInput input = new DataInputStream(
                new ByteArrayInputStream( bytes, start, bytes.length - start ) );
        final int val;
        try
        {
            val = input.readInt();
        }
        catch ( final IOException e )
        {
            throw new IllegalStateException( e );
        }
        return val;
    }

    /**
     * @param challenge A challenge to check a response against
     * @param response  A response to verify
     * @return True if the response is valid
     * @throws GeneralSecurityException if a security exception is generated
     */
    public boolean verifyResponseCode( final long challenge, final String response )
            throws GeneralSecurityException
    {
        final String expectedResponse = generateResponseCode( challenge );
        return expectedResponse.equals( response );
    }

    /**
     * Verify a timeout code. The timeout code will be valid for a time
     * determined by the interval period and the number of adjacent intervals
     * checked.
     *
     * @param timeoutCode The timeout code
     * @return True if the timeout code is valid
     * @throws GeneralSecurityException if a security exception is generated
     */
    public boolean verifyTimeoutCode( final String timeoutCode )
            throws GeneralSecurityException
    {
        return verifyTimeoutCode( timeoutCode, ADJACENT_INTERVALS,
                ADJACENT_INTERVALS );
    }

    /**
     * Verify a timeout code. The timeout code will be valid for a time
     * determined by the interval period and the number of adjacent intervals
     * checked.
     *
     * @param timeoutCode     The timeout code
     * @param pastIntervals   The number of past intervals to check
     * @param futureIntervals The number of future intervals to check
     * @return True if the timeout code is valid
     * @throws GeneralSecurityException if a security exception is generated
     */
    public boolean verifyTimeoutCode(
            final String timeoutCode,
            final int pastIntervals,
            final int futureIntervals
    )
            throws GeneralSecurityException
    {
        final long currentInterval = clock.getCurrentInterval();
        final String expectedResponse = generateResponseCode( currentInterval );
        if ( expectedResponse.equals( timeoutCode ) )
        {
            return true;
        }
        for ( int i = 1; i <= pastIntervals; i++ )
        {
            final String pastResponse = generateResponseCode( currentInterval - i );
            if ( pastResponse.equals( timeoutCode ) )
            {
                return true;
            }
        }
        for ( int i = 1; i <= futureIntervals; i++ )
        {
            final String futureResponse = generateResponseCode( currentInterval + i );
            if ( futureResponse.equals( timeoutCode ) )
            {
                return true;
            }
        }
        return false;
    }

    private IntervalClock clock = new IntervalClock()
    {
        /*
         * @return The current interval
         */
        public long getCurrentInterval( )
        {
            final long currentTimeSeconds = System.currentTimeMillis() / 1000;
            return currentTimeSeconds / getIntervalPeriod();
        }

        public int getIntervalPeriod( )
        {
            return intervalPeriod;
        }
    };

    // To facilitate injecting a mock clock
    interface IntervalClock
    {
        int getIntervalPeriod( );

        long getCurrentInterval( );
    }
}
