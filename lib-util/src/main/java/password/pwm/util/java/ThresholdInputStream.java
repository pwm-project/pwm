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

import org.apache.commons.io.input.ObservableInputStream;

import java.io.IOException;
import java.io.InputStream;

public class ThresholdInputStream extends ObservableInputStream
{
    private ThresholdInputStream( final InputStream inputStream, final Observer observer )
    {
        super( inputStream, observer );
    }

    public static ThresholdInputStream newThresholdInputStream( final InputStream inputStream, final long maxBytes )
    {
        return new ThresholdInputStream( inputStream, new ThresholdObserver( maxBytes ) );
    }

    private static class ThresholdObserver extends Observer
    {
        private final long maxBytes;

        private long lengthCount = 0;

        ThresholdObserver( final long maxBytes )
        {
            this.maxBytes = maxBytes;
        }

        private void checkLength( final long addLength )
                throws IOException
        {
            if ( addLength > 0 )
            {
                this.lengthCount += addLength;
                if ( lengthCount > maxBytes )
                {
                    throw new IOException( "maximum input length exceeded" );
                }
            }
        }

        @Override
        public void data( final int input )
                throws IOException
        {
            checkLength( 1 );
        }

        @Override
        public void data( final byte[] input, final int offset, final int length )
                throws IOException
        {
            checkLength( length - offset );
        }
    }
}
