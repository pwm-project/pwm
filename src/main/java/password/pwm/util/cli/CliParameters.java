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

package password.pwm.util.cli;

import java.util.List;

public class CliParameters {
    public boolean readOnly;
    public boolean needsPwmApplication;
    public boolean needsLocalDB;

    public String commandName;
    public String description;
    public List<Option> options;

    interface Option {
        enum type {
            EXISTING_FILE,
            NEW_FILE,
            STRING
        }

        boolean isOptional();

        type getType();

        String getName();
    }

    public static final Option REQUIRED_NEW_OUTPUT_FILE = new Option() {
        public boolean isOptional()
        {
            return false;
        }

        public type getType()
        {
            return type.NEW_FILE;
        }

        public String getName()
        {
            return "outputFile";
        }
    };
}
