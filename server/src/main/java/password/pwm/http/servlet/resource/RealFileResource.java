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

package password.pwm.http.servlet.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

class RealFileResource implements FileResource
{
    private final Path realFile;

    RealFileResource( final Path realFile )
    {
        this.realFile = realFile;
    }

    @Override
    public InputStream getInputStream( ) throws IOException
    {
        return Files.newInputStream( realFile );
    }

    @Override
    public long length( )
            throws IOException
    {
        return Files.size( realFile );
    }

    @Override
    public Instant lastModified( )
            throws IOException
    {
        return Files.getLastModifiedTime( realFile ).toInstant();
    }

    @Override
    public String getName( )
    {
        final Path fileName = realFile.getFileName();
        return fileName == null ? "" : fileName.toString();
    }
}
