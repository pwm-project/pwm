/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
        @Override
        public boolean isOptional( )
        {
            return false;
        }

        @Override
        public Type getType( )
        {
            return Type.NEW_FILE;
        }

        @Override
        public String getName( )
        {
            return "outputFile";
        }
    };

    public static final CliParameters.Option REQUIRED_EXISTING_INPUT_FILE = new CliParameters.Option()
    {
        @Override
        public boolean isOptional( )
        {
            return false;
        }

        @Override
        public Type getType( )
        {
            return Type.EXISTING_FILE;
        }

        @Override
        public String getName( )
        {
            return "inputFile";
        }
    };

    public static final CliParameters.Option OPTIONAL_PASSWORD = new CliParameters.Option()
    {
        @Override
        public boolean isOptional( )
        {
            return true;
        }

        @Override
        public Type getType( )
        {
            return Type.STRING;
        }

        @Override
        public String getName( )
        {
            return "password";
        }
    };
}
