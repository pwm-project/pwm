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

package password.pwm.http.servlet.resource;

import java.io.Serializable;

final class CacheKey implements Serializable
{
    private final String fileName;
    private final boolean acceptsGzip;
    private final long fileModificationTimestamp;

    CacheKey( final FileResource file, final boolean acceptsGzip )
    {
        this.fileName = file.getName();
        this.acceptsGzip = acceptsGzip;
        this.fileModificationTimestamp = file.lastModified();
    }

    @Override
    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }

        final CacheKey cacheKey = ( CacheKey ) o;

        return acceptsGzip == cacheKey.acceptsGzip
                && fileModificationTimestamp == cacheKey.fileModificationTimestamp
                && !( fileName != null ? !fileName.equals( cacheKey.fileName ) : cacheKey.fileName != null );

    }

    @Override
    public int hashCode( )
    {
        int result = fileName != null ? fileName.hashCode() : 0;
        result = 31 * result + ( acceptsGzip ? 1 : 0 );
        result = 31 * result + ( int ) ( fileModificationTimestamp ^ ( fileModificationTimestamp >>> 32 ) );
        return result;
    }
}
