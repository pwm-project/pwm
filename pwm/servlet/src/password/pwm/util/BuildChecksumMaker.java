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
import password.pwm.util.secure.PwmHashAlgorithm;
import password.pwm.util.secure.SecureEngine;

import java.io.*;
import java.util.*;

public class BuildChecksumMaker {
// ------------------------------ FIELDS ------------------------------


    public static void main(final String[] args)
            throws IOException, PwmUnrecoverableException
    {
        makeBuildChecksumFile(args);
    }

    public static void makeBuildChecksumFile(final String[] args)
            throws IOException, PwmUnrecoverableException
    {
        if (args == null || args.length < 2) {
            output("usage: BuildManifestMaker <directory source> <output file>");
            System.exit(-1);
        }

        makeBuildChecksumFile(new File(args[0]),new File(args[1]));
    }

    public static void makeBuildChecksumFile(final File rootDirectory, final File outputFile)
            throws IOException, PwmUnrecoverableException
    {
        if (!rootDirectory.exists()) {
            output("error: can't find input directory");
            System.exit(-1);
        }

        if (!rootDirectory.isDirectory()) {
            output("error: input file must be a directory");
            System.exit(-1);
        }

        if (outputFile.exists()) {
            output("error: output file cant already exist");
            System.exit(-1);
        }

        output("executing Build Checksum Maker rootDirectory=" + rootDirectory + " outputFile=" + outputFile);
        final LinkedHashMap<String,String> results = new LinkedHashMap<>();
        results.putAll(readDirectorySums(rootDirectory));

        final Writer outputWriter = new BufferedWriter(new FileWriter(outputFile));
        outputWriter.write(JsonUtil.serialize(results, JsonUtil.Flag.PrettyPrint));
        outputWriter.close();
        output("build Build Checksum Maker completed successfully");
    }

    public static Map<String,String> readDirectorySums(final File rootFile)
            throws PwmUnrecoverableException, FileNotFoundException
    {
        return readDirectorySums(rootFile,"");
    }

    protected static Map<String,String> readDirectorySums(
            final File rootFile,
            final String relativePath
    )
            throws PwmUnrecoverableException, FileNotFoundException
    {
        final LinkedHashMap<String,String> results = new LinkedHashMap<>();
        for (final File loopFile : rootFile.listFiles()) {
            final String path = relativePath + loopFile.getName();
            if (loopFile.isDirectory()) {
                results.putAll(readDirectorySums(loopFile,path + "/"));
            } else {
                results.put(path,md5sumFile(loopFile));
            }
        }
        return results;
    }

    public static List<FileInformation> readFileInformation(final File rootFile)
            throws PwmUnrecoverableException, IOException
    {
        return readFileInformation(rootFile, "");
    }

    protected static List<FileInformation>  readFileInformation(
            final File rootFile,
            final String relativePath
    )
            throws PwmUnrecoverableException, IOException
    {
        final ArrayList<FileInformation> results = new ArrayList<>();
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

    private static String md5sumFile(final File file)
            throws PwmUnrecoverableException, FileNotFoundException
    {
        final InputStream is = new FileInputStream(file);
        return SecureEngine.md5sum(is);
    }

    private static void output(final String output) {
        System.out.println(output);
    }

    public static FileInformation fileInformationForFile(final File file)
            throws IOException, PwmUnrecoverableException
    {
        if (file == null || !file.exists()) {
            return null;
        }
        return new FileInformation(
                file.getName(),
                file.getAbsolutePath(),
                new Date(file.lastModified()),
                file.length(),
                SecureEngine.hash(file, PwmHashAlgorithm.SHA1)
        );
    }

    public static class FileInformation implements Serializable {
        private final String filename;
        private final String filepath;
        private final Date modified;
        private final long size;
        private final String sha1sum;

        public FileInformation(String filename, String filepath, Date modified, long size, String sha1sum) {
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
