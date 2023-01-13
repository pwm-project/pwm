/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.bean;

import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

public record VersionNumber(
        int major,
        int minor,
        int patch
)
        implements Comparable<VersionNumber>
{
    private static final String VERSION_DELIMITER = ".";
    private static final String VERSION_PREFIX = "v";

    // split by , . or -
    private static final Pattern PARSER_PATTERN = Pattern.compile( "\\.|-|," );

    public static final VersionNumber ZERO = VersionNumber.of( 0, 0, 0 );

    private static final Comparator<VersionNumber> COMPARATOR = Comparator
            .comparingInt( VersionNumber::major )
            .thenComparingInt( VersionNumber::minor )
            .thenComparingInt( VersionNumber::patch );

    @Override
    public int compareTo( final VersionNumber o )
    {
        return COMPARATOR.compare( this, o );
    }

    public String toString()
    {
        return prettyVersionString();
    }

    public String prettyVersionString()
    {
        return VERSION_PREFIX + major + VERSION_DELIMITER + minor + VERSION_DELIMITER + patch;
    }

    public static VersionNumber parse( final String input )
    {
        Objects.requireNonNull( input );

        final String prefixStripped = input.toLowerCase().startsWith( VERSION_PREFIX.toLowerCase() )
                ? input.substring( VERSION_PREFIX.length() )
                : input;

        final String[] split = PARSER_PATTERN.split( prefixStripped );

        final int major = Integer.parseInt( split[0] );
        final int minor = split.length > 1 ? Integer.parseInt( split[1] ) : 0;
        final int patch = split.length > 2 ? Integer.parseInt( split[2] ) : 0;

        return new VersionNumber( major, minor, patch );
    }

    public static VersionNumber of( final int major, final int minor, final int patch )
    {
        return new VersionNumber( major, minor, patch );
    }
}
