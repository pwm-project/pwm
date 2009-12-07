/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
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

import password.pwm.Constants;
import password.pwm.Helper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class BuildChecksumMaker {
// ------------------------------ FIELDS ------------------------------

    private static final String[] IGNORE_LIST = new String[] {
            "MANIFEST.MF",
            Constants.DEFAULT_BUILD_CHECKSUM_FILENAME
    };

// --------------------------- main() method ---------------------------

    public static void main(final String[] args)
            throws IOException
    {
        if (args == null || args.length < 2) {
            output("usage: BuildManifestMaker <pwm.war_file_location> <output path>");
            System.exit(-1);
        }

        output("executing Build Checksum Maker " + args[0] + " " + args[1]);

        if (!new File(args[0]).exists()) {
            output("error: can't find pwm.war");
            System.exit(-1);
        }

        if (!new File(args[1]).exists()) {
            output("error: can't find output path");
            System.exit(-1);
        }

        if (!new File(args[1]).isDirectory()) {
            output("error: output path must be a directory");
            System.exit(-1);
        }


        final ZipFile war = new ZipFile(args[0]);
        final Map<String,String> props = new TreeMap<String,String>();
        for (Enumeration warEnum = war.entries(); warEnum.hasMoreElements();) {
            final ZipEntry entry = (ZipEntry)warEnum.nextElement();
            if (!entry.isDirectory()) {
                boolean ignore = false;

                for (final String badFile : IGNORE_LIST) {
                    if (entry.getName().endsWith(badFile)) {
                        ignore = true;
                    }
                }

                if (!ignore) {
                    final InputStream is = war.getInputStream(entry);
                    final String md5sum = Helper.md5sum(is);
                    props.put(entry.getName(), md5sum);
                }
            }
        }

        // sort the props.
        final Properties outputProps = new Properties();
        outputProps.putAll(props);

        outputProps.store(new FileOutputStream(new File(args[1] + File.separator + Constants.DEFAULT_BUILD_CHECKSUM_FILENAME)),"");

        output("build Build Checksum Maker completed successfully");
    }

    private static void output(final String output) {
        System.out.println(output);
    }
}
