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
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ChecksumOutputStream extends OutputStream {
    private final MessageDigest messageDigest;
    private final OutputStream wrappedStream;

    public ChecksumOutputStream(final PwmHashAlgorithm hash, final OutputStream wrappedStream) throws PwmUnrecoverableException {
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
    public void close() throws IOException {
        wrappedStream.close();
    }

    @Override
    public void write(final byte[] b) throws IOException {
        messageDigest.update(b);
        wrappedStream.write(b);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (len > 0) {
            messageDigest.update(b,off,len);
        }

        wrappedStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        wrappedStream.flush();
    }

    @Override
    public void write(final int b) throws IOException {
        messageDigest.update((byte)b);
        wrappedStream.write(b);
    }

    public byte[] getInProgressChecksum() {
        return messageDigest.digest();
    }
}
