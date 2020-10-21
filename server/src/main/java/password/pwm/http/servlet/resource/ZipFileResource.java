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

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by amb on 7/13/15.
 */
class ZipFileResource implements FileResource
{
    private final ZipFile zipFile;
    private final ZipEntry zipEntry;

    ZipFileResource( final ZipFile zipFile, final ZipEntry zipEntry )
    {
        this.zipFile = zipFile;
        this.zipEntry = zipEntry;
    }

    @Override
    public InputStream getInputStream( )
            throws IOException
    {
        return zipFile.getInputStream( zipEntry );
    }

    @Override
    public long length( )
    {
        return zipEntry.getSize();
    }

    @Override
    public long lastModified( )
    {
        return zipEntry.getTime();
    }

    @Override
    public boolean exists( )
    {
        return zipEntry != null && zipFile != null;
    }

    @Override
    public String getName( )
    {
        return zipFile.getName() + ":" + zipEntry.getName();
    }
}
