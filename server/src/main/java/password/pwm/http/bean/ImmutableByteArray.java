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

package password.pwm.http.bean;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Arrays;

public class ImmutableByteArray implements Serializable
{
    private final byte[] bytes;

    private static final ImmutableByteArray EMPTY = ImmutableByteArray.of( new byte[0] );

    private ImmutableByteArray( final byte[] bytes )
    {
        this.bytes = bytes == null ? null : Arrays.copyOf( bytes, bytes.length );
    }

    public static ImmutableByteArray of( final byte[] bytes )
    {
        return bytes == null || bytes.length == 0
                ? EMPTY
                : new ImmutableByteArray( bytes );
    }

    public byte[] copyOf( )
    {
        return bytes == null ? null : Arrays.copyOf( bytes, bytes.length );
    }

    public InputStream newByteArrayInputStream( )
    {
        return new ByteArrayInputStream( bytes == null ? EMPTY.bytes : bytes );
    }

    public int size()
    {
        return bytes == null ? 0 : bytes.length;
    }

    public boolean isEmpty()
    {
        return bytes == null || bytes.length == 0;
    }

    public static ImmutableByteArray empty()
    {
        return EMPTY;
    }
}
