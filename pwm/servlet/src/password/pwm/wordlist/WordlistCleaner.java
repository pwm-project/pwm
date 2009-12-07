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

package password.pwm.wordlist;

import java.io.*;
import java.util.Set;
import java.util.TreeSet;

/**
 * A main() utility for sorting wordlists.  It performs the following tasks:
 *  - sorts the wordlist
 *  - sorts the seedlist
 *  - removes dupes from either list
 *  - removes words from the wordlist that are already in the seedlist
 *
 * Run with gobs of memory.
 */
public class WordlistCleaner {
// --------------------------- main() method ---------------------------

    public static void main(final String[] args)
            throws Exception
    {
        final String inputFile = "h:/words/wordlist.zip";

        final Set<String> wordSet = new TreeSet<String>();

        {
            final ZipReader reader = new ZipReader(new File(inputFile));
            String line;
            while ((line = reader.nextLine()) != null) {
                line = line.trim().toLowerCase();
                    wordSet.add(line);
            }
        }

        System.out.println(wordSet.size());

        outputFile(new File("h:/words/wordlist.txt"),wordSet);
    }

    private static void outputFile(final File theFile, final Set<String> set)
            throws IOException
    {
        final Writer writer =  new BufferedWriter(new FileWriter(theFile));
        for (final String l : set) {
            writer.append(l);
            writer.append("\n");
        }
    }
}
