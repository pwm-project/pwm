/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.util.java;

import java.io.OutputStream;

public class CrcChecksumOutputStream extends CopyingOutputStream
{
    private final CrcChecksumConsumer crcChecksumConsumer;

    private CrcChecksumOutputStream( final OutputStream realStream, final CrcChecksumConsumer crcChecksumConsumer )
    {
        super( realStream, crcChecksumConsumer );
        this.crcChecksumConsumer = crcChecksumConsumer;
    }

    public static CrcChecksumOutputStream newChecksumOutputStream( final OutputStream wrappedStream )
    {
        final CrcChecksumConsumer crcChecksumConsumer = new CrcChecksumConsumer();
        return new CrcChecksumOutputStream( wrappedStream, crcChecksumConsumer );
    }

    public long checksum()
    {
        return crcChecksumConsumer.checksum();
    }
}
