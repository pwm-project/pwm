/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import java.io.File;

public class WordlistConfiguration {
    final private File wordlistFile;
    final private int loadFactor;
    final private boolean caseSensitive;
    final private int checkSize;

    public WordlistConfiguration(
            final File wordlistFile,
            final int loadFactor,
            final boolean caseSensitive,
            final int checkSize
    ) {
        this.wordlistFile = wordlistFile;
        this.loadFactor = loadFactor;
        this.caseSensitive = caseSensitive;
        this.checkSize = checkSize;
    }

    public File getWordlistFile() {
        return wordlistFile;
    }

    public int getLoadFactor() {
        return loadFactor;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }

    public int getCheckSize() {
        return checkSize;
    }
}
