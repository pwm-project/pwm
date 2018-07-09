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

package password.pwm.onejar;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

enum Argument
{
    applicationPath,
    workPath,
    version,
    help,
    war,
    port,
    context,
    properties,
    localAddress,;

    static Options asOptions( )
    {
        final Options options = new Options();
        asOptionMap().values().forEach( options::addOption );
        return options;
    }

    static Map<Argument, Option> asOptionMap( )
    {
        final Map<Argument, Option> optionMap = new TreeMap<>();

        optionMap.put( Argument.applicationPath, Option.builder( Argument.applicationPath.name() )
                .desc( "application path (required)" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put( Argument.workPath, Option.builder( Argument.workPath.name() )
                .desc( "temporary work path" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put( Argument.version, Option.builder( Argument.version.name() )
                .desc( "show version" )
                .numberOfArgs( 0 )
                .build() );

        optionMap.put( Argument.port, Option.builder( Argument.port.name() )
                .desc( "web server port (default " + Resource.defaultPort.getValue() + ")" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put( Argument.localAddress, Option.builder( Argument.localAddress.name() )
                .desc( "local network address (default localhost)" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put( Argument.context, Option.builder( Argument.context.name() )
                .desc( "context (url path) name (default " + Resource.defaultContext.getValue() + ")" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put( Argument.help, Option.builder( Argument.help.name() )
                .desc( "show this help" )
                .numberOfArgs( 0 )
                .build() );

        optionMap.put( Argument.properties, Option.builder( Argument.properties.name() )
                .desc( "read arguments from properties file" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put( Argument.war, Option.builder( Argument.war.name() )
                .desc( "source war file (default embedded)" )
                .numberOfArgs( 1 )
                .build() );

        return Collections.unmodifiableMap( optionMap );
    }

}
