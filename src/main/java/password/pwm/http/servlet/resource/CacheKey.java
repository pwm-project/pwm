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

import java.io.Serializable;

final class CacheKey implements Serializable {
    final private String fileName;
    final private boolean acceptsGzip;
    final private long fileModificationTimestamp;

    CacheKey(final FileResource file, final boolean acceptsGzip) {
        this.fileName = file.getName();
        this.acceptsGzip = acceptsGzip;
        this.fileModificationTimestamp = file.lastModified();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CacheKey cacheKey = (CacheKey) o;

        return acceptsGzip == cacheKey.acceptsGzip &&
                fileModificationTimestamp == cacheKey.fileModificationTimestamp &&
                !(fileName != null ? !fileName.equals(cacheKey.fileName) : cacheKey.fileName != null);

    }

    @Override
    public int hashCode() {
        int result = fileName != null ? fileName.hashCode() : 0;
        result = 31 * result + (acceptsGzip ? 1 : 0);
        result = 31 * result + (int) (fileModificationTimestamp ^ (fileModificationTimestamp >>> 32));
        return result;
    }
}
