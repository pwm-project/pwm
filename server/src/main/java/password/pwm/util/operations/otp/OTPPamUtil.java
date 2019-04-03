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

import password.pwm.util.logging.PwmLogger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


/**
 * @author Menno Pieters
 */
public class OTPPamUtil
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( OTPPamUtil.class );

    /**
     * Split the string in lines; separate by CR, LF or CRLF.
     *
     * @param text string containing line separators.
     *
     * @return a list of split lines
     */
    public static List<String> splitLines( final String text )
    {
        final List<String> list = new ArrayList<>();
        if ( text != null )
        {
            final String[] lines = text.split( "\r?\n|\r" );
            list.addAll( Arrays.asList( lines ) );
        }
        return list;
    }

    public static OTPUserRecord decomposePamData( final String otpInfo )
    {
        final List<String> lines = splitLines( otpInfo );
        if ( lines.size() >= 2 )
        {
            final Iterator<String> iterator = lines.iterator();
            String line = iterator.next();
            if ( line.matches( "^[A-Z2-7\\=]{16}$" ) )
            {
                // default identifier
                final OTPUserRecord otp = new OTPUserRecord();
                otp.setSecret( line );
                final List<OTPUserRecord.RecoveryCode> recoveryCodes = new ArrayList<>();
                while ( iterator.hasNext() )
                {
                    line = iterator.next();
                    if ( line.startsWith( "\" " ) )
                    {
                        final String option = line.substring( 2 ).trim();
                        if ( "TOTP_AUTH".equals( option ) )
                        {
                            otp.setType( OTPUserRecord.Type.TOTP );
                        }
                        else if ( option.matches( "^HOTP_COUNTER\\s+\\d+$" ) )
                        {
                            final String countStr = option.substring( option.indexOf( " " ) + 1 );
                            otp.setType( OTPUserRecord.Type.HOTP );
                            otp.setAttemptCount( Long.parseLong( countStr ) );
                        }
                    }
                    else if ( line.matches( "^\\d{8}$" ) )
                    {
                        final OTPUserRecord.RecoveryCode code = new OTPUserRecord.RecoveryCode();
                        code.setUsed( false );
                        code.setHashCode( line );
                        recoveryCodes.add( code );
                    }
                    else
                    {
                        final String finalLine = line;
                        LOGGER.trace( () -> String.format( "Unrecognized line: \"%s\"", finalLine ) );
                    }
                }
                if ( recoveryCodes.isEmpty() )
                {
                    LOGGER.debug( () -> "No recovery codes read." );
                    otp.setRecoveryCodes( null );
                    otp.setRecoveryInfo( null );
                }
                else
                {
                    LOGGER.debug( () -> String.format( "%d recovery codes read.", recoveryCodes.size() ) );
                    final OTPUserRecord.RecoveryInfo recoveryInfo = new OTPUserRecord.RecoveryInfo();
                    recoveryInfo.setHashCount( 0 );
                    recoveryInfo.setSalt( null );
                    recoveryInfo.setHashMethod( null );
                    otp.setRecoveryInfo( recoveryInfo );
                    otp.setRecoveryCodes( recoveryCodes );
                }
                return otp;
            }
        }
        return null;
    }

    /**
     * Create a string representation to be stored by the operator.
     *
     * @param otp the record with OTP configuration
     * @return the string representation of the OTP record
     */
    public static String composePamData( final OTPUserRecord otp )
    {
        if ( otp == null )
        {
            return "";
        }
        final String secret = otp.getSecret();
        final OTPUserRecord.Type type = otp.getType();
        final List<OTPUserRecord.RecoveryCode> recoveryCodes = otp.getRecoveryCodes();
        final StringBuilder pamData = new StringBuilder();
        pamData.append( secret ).append( "\n" );
        if ( OTPUserRecord.Type.HOTP.equals( type ) )
        {
            pamData.append( String.format( "\" HOTP_COUNTER %d%n", otp.getAttemptCount() ) );
        }
        else
        {
            pamData.append( "\" TOTP_AUTH\n" );
        }
        if ( recoveryCodes != null && recoveryCodes.size() > 0 )
        {
            // The codes are assumed to be non-hashed
            for ( final OTPUserRecord.RecoveryCode code : recoveryCodes )
            {
                if ( !code.isUsed() )
                {
                    pamData.append( code.getHashCode() + "\n" );
                }
            }
        }
        return pamData.toString();
    }
}
