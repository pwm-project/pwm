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

package password.pwm.util.localdb;

import password.pwm.PwmConstants;
import password.pwm.util.StringUtil;
import password.pwm.util.logging.PwmLogger;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class LocalDBCompressor implements LocalDB {
    private static final PwmLogger LOGGER = PwmLogger.forClass(LocalDBCompressor.class);

    static final String COMPRESS_PREFIX = "c!";

    final LocalDB innerLocalDB;
    final int minCompressLength;
    final boolean enableCompression;

    public LocalDBCompressor(
            final LocalDB innerLocalDB,
            final int minCompressLength,
            final boolean enableCompression
    ) {
        this.innerLocalDB = innerLocalDB;
        this.minCompressLength = minCompressLength;
        this.enableCompression = enableCompression;
    }

    public static LocalDBCompressor createLocalDBCompressor(
            final LocalDB innerLocalDB,
            final int minCompressLength,
            final boolean enableCompression
    ) {
        if (innerLocalDB == null) {
            return null;
        }
        return new LocalDBCompressor(innerLocalDB, minCompressLength, enableCompression);
    }

    @Override
    public void close() throws LocalDBException {
        innerLocalDB.close();
    }

    @Override
    public boolean contains(DB db, String key) throws LocalDBException {
        return innerLocalDB.contains(db, key);
    }

    @Override
    public String get(DB db, String key) throws LocalDBException {
        return decompressData(innerLocalDB.get(db, key));
    }

    @Override
    public LocalDBIterator<String> iterator(DB db) throws LocalDBException {
        return innerLocalDB.iterator(db);
    }

    @Override
    public void putAll(DB db, Map<String, String> keyValueMap) throws LocalDBException {
        final Map<String,String> tempMap = new LinkedHashMap<>();
        for (final String key : keyValueMap.keySet()) {
            tempMap.put(key,compressData(keyValueMap.get(key)));
        }

        innerLocalDB.putAll(db,tempMap);
    }

    @Override
    public Status status() {
        return innerLocalDB.status();
    }

    @Override
    public boolean put(DB db, String key, String value) throws LocalDBException {
        return innerLocalDB.put(db, key, compressData(value));
    }

    @Override
    public boolean remove(DB db, String key) throws LocalDBException {
        return innerLocalDB.remove(db, key);
    }

    @Override
    public void removeAll(DB db, Collection<String> key) throws LocalDBException {
        innerLocalDB.removeAll(db, key);
    }

    @Override
    public int size(DB db) throws LocalDBException {
        return innerLocalDB.size(db);
    }

    @Override
    public void truncate(DB db) throws LocalDBException {
        innerLocalDB.truncate(db);
    }

    @Override
    public File getFileLocation() {
        return innerLocalDB.getFileLocation();
    }

    private String compressData(final String input) {
        if (input == null) {
            return null;
        }
        if (!enableCompression) {
            return input;
        }
        if (input.length() < minCompressLength) {
            return input;
        }
        final String compressedValue;
        try {
            compressedValue = StringUtil.base64Encode(input.getBytes(PwmConstants.DEFAULT_CHARSET),StringUtil.Base64Options.GZIP);
        } catch (IOException e) {
            return input;
        }
        if (compressedValue.length() < input.length()) {
            return COMPRESS_PREFIX + compressedValue;
        }
        return input;
    }

    private String decompressData(final String input) {
        if (input == null) {
            return null;
        }

        if (input.startsWith(COMPRESS_PREFIX)) {
            final String compressedValue = input.substring(COMPRESS_PREFIX.length(),input.length());
            try {
                return new String(StringUtil.base64Decode(compressedValue, StringUtil.Base64Options.GZIP),PwmConstants.DEFAULT_CHARSET);
            } catch (IOException e) {
                LOGGER.warn("error decompressing data string: " + input + "\n error: " + e.getMessage());
                return compressedValue;
            }
        }

        return input;
    }
}
