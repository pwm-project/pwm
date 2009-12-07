/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

package password.pwm.util;

public abstract class ByteConverter {
// -------------------------- STATIC METHODS --------------------------

    /**
     * ByteConverter a char to two bytes, putting the result at the given position in
     * the given array.
     */
    public static byte[] charToTwoBytes(final char i, final byte[] b, final int index) {
        b[index] = (byte) (i >> 8);
        b[index + 1] = (byte) (i);
        return b;
    }

    /**
     * ByteConverter eight bytes at the given position in an array to a long.
     */
    public static long eightBytesToLong(final byte[] b, final int i) {
        return eightBytesToLong(b[i], b[i + 1], b[i + 2], b[i + 3], b[i + 4], b[i + 5], b[i + 6], b[i + 7]);
    }

    /**
     * ByteConverter eight bytes to a long.
     */
    public static long eightBytesToLong(final byte b1, final byte b2, final byte b3, final byte b4, final byte b5, final byte b6, final byte b7, final byte b8) {
        final int hi = fourBytesToInt(b1, b2, b3, b4);
        final int lo = fourBytesToInt(b5, b6, b7, b8);
        return twoIntsToLong(lo, hi);
    }

    /**
     * ByteConverter four bytes to an int.
     */
    public static int fourBytesToInt(final byte b1, final byte b2, final byte b3, final byte b4) {
        return (b1 << 24) | ((b2 & 0xFF) << 16) | ((b3 & 0xFF) << 8) | (b4 & 0xFF);
    }

    /**
     * ByteConverter two ints to a long.
     */
    public static long twoIntsToLong(final int lo, final int hi) {
        return (((long) lo) & 0xFFFFFFFFL) | ((long) hi << 32);
    }

    /**
     * ByteConverter four bytes at the given position in an array to an int.
     */
    public static int fourBytesToInt(final byte[] b, final int i) {
        return fourBytesToInt(b[i], b[i + 1], b[i + 2], b[i + 3]);
    }

    /**
     * ByteConverter an int to four bytes, putting the result at the given position in
     * the given array.
     */
    public static byte[] intToFourBytes(final int i, final byte[] b, final int index) {
        b[index] = (byte) (i >> 24);
        b[index + 1] = (byte) (i >> 16);
        b[index + 2] = (byte) (i >> 8);
        b[index + 3] = (byte) (i);
        return b;
    }

    /**
     * ByteConverter a long to eight bytes, putting the result at the given position
     * in the given array.
     */
    public static void longToEightBytes(final long i, final byte[] b, final int index) {
        b[index] = (byte) (i >> 56);
        b[index + 1] = (byte) (i >> 48);
        b[index + 2] = (byte) (i >> 40);
        b[index + 3] = (byte) (i >> 32);
        b[index + 4] = (byte) (i >> 24);
        b[index + 5] = (byte) (i >> 16);
        b[index + 6] = (byte) (i >> 8);
        b[index + 7] = (byte) (i);
    }

    /**
     * ByteConverter an int to four bytes, putting the result at the given position in
     * the given array.
     */
    public static byte[] shortToTwoBytes(final short s, final byte[] b, final int index) {
        b[index] = (byte) (s >> 8);
        b[index + 1] = (byte) (s);
        return b;
    }

    /**
     * ByteConverter two bytes at the given position in an array to a char.
     */
    public static char twoBytesToChar(final byte[] b, final int i) {
        return ByteConverter.twoBytesToChar(b[i], b[i + 1]);
    }

    /**
     * ByteConverter two bytes to a char.
     *
     * @param b1 first byte
     * @param b2 second byte
     * @return char result
     */
    public static char twoBytesToChar(final byte b1, final byte b2) {
        return (char) ((b1 << 8) | (b2 & 0xFF));
    }

    /**
     * ByteConverter two bytes at the given position in an array to a short.
     */
    public static short twoBytesToShort(final byte[] b, final int i) {
        return twoBytesToShort(b[i], b[i + 1]);
    }

    /**
     * ByteConverter two bytes to a short.
     *
     * @param b1 first byte
     * @param b2 second byte
     * @return short result
     */
    public static short twoBytesToShort(final byte b1, final byte b2) {
        return (short) ((b1 << 8) | (b2 & 0xFF));
    }

    /**
     * ByteConverter two chars to an int.
     *
     * @param c1 first char
     * @param c2 second char
     * @return int result
     */
    public static int twoCharsToInt(final char c1, final char c2) {
        return (c1 << 16) | c2;
    }
}