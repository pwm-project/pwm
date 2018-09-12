/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

public class CliParameters
{
    public boolean readOnly;
    public boolean needsPwmApplication;
    public boolean needsLocalDB;

    public String commandName;
    public String description;
    public List<Option> options;

    public interface Option
    {
        enum Type
        {
            EXISTING_FILE,
            NEW_FILE,
            STRING
        }

        boolean isOptional( );

        Type getType( );

        String getName( );
    }

    public static final Option REQUIRED_NEW_OUTPUT_FILE = new Option()
    {
        public boolean isOptional( )
        {
            return false;
        }

        public Type getType( )
        {
            return Type.NEW_FILE;
        }

        public String getName( )
        {
            return "outputFile";
        }
    };

    public static final CliParameters.Option REQUIRED_EXISTING_INPUT_FILE = new CliParameters.Option()
    {
        public boolean isOptional( )
        {
            return false;
        }

        public Type getType( )
        {
            return Type.EXISTING_FILE;
        }

        public String getName( )
        {
            return "inputFile";
        }
    };

    public static final CliParameters.Option OPTIONAL_PASSWORD = new CliParameters.Option()
    {
        public boolean isOptional( )
        {
            return true;
        }

        public Type getType( )
        {
            return Type.STRING;
        }

        public String getName( )
        {
            return "password";
        }
    };
}
