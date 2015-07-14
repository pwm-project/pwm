/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.http.servlet.resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by amb on 7/13/15.
 */
class ZipFileResource implements FileResource {
    private final ZipFile zipFile;
    private final ZipEntry zipEntry;

    ZipFileResource(ZipFile zipFile, ZipEntry zipEntry) {
        this.zipFile = zipFile;
        this.zipEntry = zipEntry;
    }

    public InputStream getInputStream()
            throws IOException {
        return zipFile.getInputStream(zipEntry);
    }

    public long length() {
        return zipEntry.getSize();
    }

    public long lastModified() {
        return zipEntry.getTime();
    }

    public boolean exists() {
        return zipEntry != null && zipFile != null;
    }

    public String getName() {
        return zipFile.getName() + ":" + zipEntry.getName();
    }
}
