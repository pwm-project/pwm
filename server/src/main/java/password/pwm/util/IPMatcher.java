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

package password.pwm.util;

import java.net.Inet6Address;
import java.net.UnknownHostException;

public class IPMatcher
{
    /**
     * Network to match.
     */
    private byte[] network;

    /**
     * Network mask.
     */
    private byte[] netmask;

    /**
     * Construct an IPMatcher that will test for the given IP specification.
     *
     * @param ipSpec IP specification (full or partial address, network/netmask,
     *               network/cidr)
     * @throws IPMatcherException if there is an error parsing the specification (i.e. it is
     *                            somehow malformed)
     */
    public IPMatcher( final String ipSpec ) throws IPMatcherException
    {
        // Boil all specs down to network + mask

        String ipPart = ipSpec;
        final String[] parts = ipSpec.split( "/" );

        if ( parts[ 0 ].indexOf( ':' ) >= 0 )
        {
            // looks like IPv6
            try
            {
                network = Inet6Address.getByName( parts[ 0 ] ).getAddress();
            }
            catch ( final UnknownHostException e )
            {
                throw new IPMatcherException(
                        "Malformed IP range specification " + ipSpec, e );
            }

            netmask = new byte[ 16 ];
            switch ( parts.length )
            {
                case 2:
                    // CIDR notation:  calculate the mask
                    final int maskBits;
                    try
                    {
                        maskBits = Integer.parseInt( parts[ 1 ] );
                    }
                    catch ( final NumberFormatException nfe )
                    {
                        throw new IPMatcherException(
                                "Malformed IP range specification " + ipSpec, nfe );
                    }
                    if ( maskBits < 0 || maskBits > 128 )
                    {
                        throw new IPMatcherException( "Mask bits out of range 0-128 "
                                + ipSpec );
                    }

                    final int maskBytes = maskBits / 8;
                    for ( int i = 0; i < maskBytes; i++ )
                    {
                        netmask[ i ] = ( byte ) 0Xff;
                    }
                    netmask[ maskBytes ] = ( byte ) ( ( byte ) 0Xff << 8 - ( maskBits % 8 ) );
                    for ( int i = maskBytes + 1; i < ( 128 / 8 ); i++ )
                    {
                        netmask[ i ] = 0;
                    }
                    break;
                case 1:
                    // No explicit mask:  fill the mask with 1s
                    for ( int i = 0; i < netmask.length; i++ )
                    {
                        netmask[ i ] = ( byte ) 0Xff;
                    }
                    break;
                default:
                    throw new IPMatcherException( "Malformed IP range specification "
                            + ipSpec );
            }
        }
        else
        {
            // assume IPv4
            // Allow partial IP
            boolean mustHave4 = false;

            network = new byte[ 4 ];
            netmask = new byte[ 4 ];
            switch ( parts.length )
            {
                case 2:
                    // Some kind of slash notation -- we'll need a full network IP
                    ipPart = parts[ 0 ];
                    mustHave4 = true;

                    final String[] maskParts = parts[ 1 ].split( "\\." );
                    if ( maskParts.length == 1 )
                    {
                        // CIDR slash notation
                        final int x;

                        try
                        {
                            x = Integer.parseInt( maskParts[ 0 ] );
                        }
                        catch ( final NumberFormatException nfe )
                        {
                            throw new IPMatcherException(
                                    "Malformed IP range specification " + ipSpec, nfe );
                        }

                        if ( x < 0 || x > 32 )
                        {
                            throw new IPMatcherException();
                        }

                        final int fullMask = -1 << ( 32 - x );
                        netmask[ 0 ] = ( byte ) ( ( fullMask & 0xFF000000 ) >>> 24 );
                        netmask[ 1 ] = ( byte ) ( ( fullMask & 0x00FF0000 ) >>> 16 );
                        netmask[ 2 ] = ( byte ) ( ( fullMask & 0x0000FF00 ) >>> 8 );
                        netmask[ 3 ] = ( byte ) ( fullMask & 0x000000FF );
                        ipToBytes( ipPart, network, mustHave4 );
                    }
                    else
                    {
                        // full subnet specified
                        ipToBytes( parts[ 0 ], network, true );
                        ipToBytes( parts[ 1 ], netmask, true );
                    }
                    break;

                case 1:
                    // Get IP
                    for ( int i = 0; i < netmask.length; i++ )
                    {
                        netmask[ i ] = -1;
                    }
                    final int partCount = ipToBytes( ipPart, network, mustHave4 );

                    // If partial IP, set mask for remaining bytes
                    for ( int i = 3; i >= partCount; i-- )
                    {
                        netmask[ i ] = 0;
                    }

                    break;

                default:
                    throw new IPMatcherException( "Malformed IP range specification "
                            + ipSpec );
            }
            network = ip4ToIp6( network );
            netmask = ip4MaskToIp6( netmask );
        }
    }

    /**
     * Fill out a given four-byte array with the IPv4 address specified in the
     * given String.
     *
     * @param ip        IPv4 address as a dot-delimited String
     * @param bytes     4-byte array to fill out
     * @param mustHave4 if true, will require that the given IP string specify all
     *                  four bytes
     * @return the number of actual IP bytes found in the given IP address
     *         String
     * @throws IPMatcherException if there is a problem parsing the IP string -- e.g. number
     *                            outside of range 0-255, too many numbers, less than 4 numbers
     *                            if {@code mustHave4} is true
     */
    private static int ipToBytes( final String ip, final byte[] bytes, final boolean mustHave4 )
            throws IPMatcherException
    {
        final String[] parts = ip.split( "\\." );

        if ( parts.length > 4 || mustHave4 && parts.length != 4 )
        {
            throw new IPMatcherException( "Malformed IP specification " + ip );
        }

        try
        {

            for ( int i = 0; i < parts.length; i++ )
            {
                final int p = Integer.parseInt( parts[ i ] );
                if ( p < 0 || p > 255 )
                {
                    throw new IPMatcherException( "Malformed IP specification "
                            + ip );

                }

                bytes[ i ] = ( byte ) ( p < 128 ? p : p - 256 );
            }
        }
        catch ( final NumberFormatException nfe )
        {
            throw new IPMatcherException( "Malformed IP specification " + ip,
                    nfe );
        }

        return parts.length;
    }

    /**
     * Determine whether the given full IP falls within the range this
     * {@code IPMatcher} was initialized with.
     *
     * @param ipIn IP address as dot-delimited String
     * @return {@code true} if the IP matches the range of this
     *         {@code IPMatcher}; {@code false} otherwise
     * @throws IPMatcherException if the IP passed in cannot be parsed correctly (i.e. is
     *                            malformed)
     */
    public boolean match( final String ipIn ) throws IPMatcherException
    {
        byte[] candidate;

        if ( ipIn.indexOf( ':' ) < 0 )
        {
            candidate = new byte[ 4 ];
            ipToBytes( ipIn, candidate, true );
            candidate = ip4ToIp6( candidate );
        }
        else
        {
            try
            {
                candidate = Inet6Address.getByName( ipIn ).getAddress();
            }
            catch ( final UnknownHostException e )
            {
                throw new IPMatcherException( "Malformed IPv6 address ", e );
            }
        }

        for ( int i = 0; i < netmask.length; i++ )
        {
            if ( ( candidate[ i ] & netmask[ i ] ) != ( network[ i ] & netmask[ i ] ) )
            {
                return false;
            }
        }

        return true;
    }

    /**
     * Convert an IPv4 address to an IPv6 IPv4-compatible address.
     *
     * @param ip4 an IPv4 address
     * @return the corresponding IPv6 address
     * @throws IllegalArgumentException if ip4 is not exactly four octets long.
     */
    private static byte[] ip4ToIp6( final byte[] ip4 )
    {
        if ( ip4.length != 4 )
        {
            throw new IllegalArgumentException( "IPv4 address must be four octets" );
        }

        final byte[] ip6 = new byte[ 16 ];
        for ( int i = 0; i < 16 - 4; i++ )
        {
            ip6[ i ] = 0;
        }
        for ( int i = 0; i < 4; i++ )
        {
            ip6[ 12 + i ] = ip4[ i ];
        }
        return ip6;
    }

    /**
     * Convert an IPv4 mask to the equivalent IPv6 mask.
     *
     * @param ip4 an IPv4 mask
     * @return the corresponding IPv6 mask
     * @throws IllegalArgumentException if ip4 is not exactly four octets long.
     */
    private static byte[] ip4MaskToIp6( final byte[] ip4 )
    {
        if ( ip4.length != 4 )
        {
            throw new IllegalArgumentException( "IPv4 mask must be four octets" );
        }

        final byte[] ip6 = new byte[ 16 ];
        for ( int i = 0; i < 16 - 4; i++ )
        {
            ip6[ i ] = ( byte ) 0Xff;
        }
        for ( int i = 0; i < 4; i++ )
        {
            ip6[ 12 + i ] = ip4[ i ];
        }
        return ip6;
    }

    public static class IPMatcherException extends Exception
    {
        public IPMatcherException( )
        {
            super();
        }

        public IPMatcherException( final String message )
        {
            super( message );
        }

        public IPMatcherException( final Throwable cause )
        {
            super( cause );
        }

        public IPMatcherException( final String message, final Throwable cause )
        {
            super( message, cause );
        }
    }
}
