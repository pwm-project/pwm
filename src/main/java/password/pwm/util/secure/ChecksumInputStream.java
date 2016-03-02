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

import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ChecksumInputStream extends InputStream {
    private final MessageDigest messageDigest;
    private final InputStream wrappedStream;

    public ChecksumInputStream(PwmHashAlgorithm hash, InputStream wrappedStream) throws PwmUnrecoverableException {
        this.wrappedStream = wrappedStream;

        try {
            messageDigest = MessageDigest.getInstance(hash.getAlgName());
        } catch (NoSuchAlgorithmException e) {
            final String errorMsg = "missing hash algorithm: " + e.getMessage();
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CRYPT_ERROR, errorMsg);
            throw new PwmUnrecoverableException(errorInformation);
        }
    }

    @Override
    public int read() throws IOException {
        final int value = wrappedStream.read();
        if (value >= 0) {
            messageDigest.update((byte)value);
        }
        return value;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        final int length = wrappedStream.read(b);
        if (length > 0) {
            messageDigest.update(b,0,length);
        }
        return length;
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        int length = wrappedStream.read(b, off, len);
        if (length > 0) {
            messageDigest.update(b,off,length);
        }
        return length;
    }

    @Override
    public long skip(long n) throws IOException {
        throw new IOException("operation not supported");
    }

    @Override
    public int available() throws IOException {
        return wrappedStream.available();
    }

    @Override
    public void close() throws IOException {
        wrappedStream.close();
    }

    @Override
    public synchronized void mark(int readlimit) {
        wrappedStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        throw new IOException("operation not supported");
    }

    @Override
    public boolean markSupported() {
        return false;
    }

    public byte[] getInProgressChecksum() {
        return messageDigest.digest();
    }

    public byte[] closeAndFinalChecksum() throws IOException {
        final byte[] buffer = new byte[1024];

        while (read(buffer) > 0); // read out the remainder of the stream contents

        return getInProgressChecksum();
    }
}
