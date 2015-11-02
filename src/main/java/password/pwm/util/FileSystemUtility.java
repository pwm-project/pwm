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

package password.pwm.util;

import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class FileSystemUtility {

    private static final PwmLogger LOGGER = PwmLogger.forClass(FileSystemUtility.class);

    public static List<FileSummaryInformation> readFileInformation(final File rootFile)
            throws PwmUnrecoverableException, IOException
    {
        return readFileInformation(rootFile, "");
    }

    protected static List<FileSummaryInformation> readFileInformation(
            final File rootFile,
            final String relativePath
    )
            throws PwmUnrecoverableException, IOException
    {
        final ArrayList<FileSummaryInformation> results = new ArrayList<>();
        for (final File loopFile : rootFile.listFiles()) {
            final String path = relativePath + loopFile.getName();
            if (loopFile.isDirectory()) {
                results.addAll(readFileInformation(loopFile, path + "/"));
            } else {
                results.add(fileInformationForFile(loopFile));
            }
        }
        return results;
    }

    public static FileSummaryInformation fileInformationForFile(final File file)
            throws IOException, PwmUnrecoverableException
    {
        if (file == null || !file.exists()) {
            return null;
        }
        return new FileSummaryInformation(
                file.getName(),
                file.getAbsolutePath(),
                new Date(file.lastModified()),
                file.length(),
                SecureEngine.hash(file, PwmHashAlgorithm.SHA1)
        );
    }

    public static long getFileDirectorySize(final File dir) {
        long size = 0;
        try {
            if (dir.isFile()) {
                size = dir.length();
            } else {
                final File[] subFiles = dir.listFiles();
                if (subFiles != null) {
                    for (final File file : subFiles) {
                        if (file.isFile()) {
                            size += file.length();
                        } else {
                            size += getFileDirectorySize(file);
                        }

                    }
                }
            }
        } catch (NullPointerException e) {
            // file was deleted before file size could be read
        }

        return size;
    }

    public static File figureFilepath(final String filename, final File suggestedPath)
    {
        if (filename == null || filename.length() < 1) {
            return null;
        }

        if ((new File(filename)).isAbsolute()) {
            return new File(filename);
        }

        return new File(suggestedPath + File.separator + filename);
    }

    public static long diskSpaceRemaining(final File file) {
        try {
            final Method getFreeSpaceMethod = File.class.getMethod("getFreeSpace");
            final Object rawResult = getFreeSpaceMethod.invoke(file);
            return (Long) rawResult;
        } catch (NoSuchMethodException e) {
            /* no error, pre java 1.6 doesn't have this method */
        } catch (Exception e) {
            LOGGER.debug("error reading file space remaining for " + file.toString() + ",: " + e.getMessage());
        }
        return -1;
    }

    public static class FileSummaryInformation implements Serializable {
        private final String filename;
        private final String filepath;
        private final Date modified;
        private final long size;
        private final String sha1sum;

        public FileSummaryInformation(String filename, String filepath, Date modified, long size, String sha1sum) {
            this.filename = filename;
            this.filepath = filepath;
            this.modified = modified;
            this.size = size;
            this.sha1sum = sha1sum;
        }

        public String getFilename() {
            return filename;
        }

        public String getFilepath() {
            return filepath;
        }

        public Date getModified() {
            return modified;
        }

        public long getSize() {
            return size;
        }

        public String getSha1sum() {
            return sha1sum;
        }
    }
}
