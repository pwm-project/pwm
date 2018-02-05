package password.pwm;

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
    localAddress,

    ;

    static Options asOptions()
    {
        final Options options = new Options();
        asOptionMap().values().forEach( options::addOption );
        return options;
    }

    static Map<Argument,Option> asOptionMap() {
        final Map<Argument,Option> optionMap = new TreeMap<>(  );

        optionMap.put(Argument.applicationPath, Option.builder(Argument.applicationPath.name()  )
                .desc( "application path (required)" )
                .numberOfArgs( 1 )
                .build());

        optionMap.put(Argument.workPath, Option.builder(Argument.workPath.name() )
                .desc( "temporary work path" )
                .numberOfArgs( 1 )
                .build());

        optionMap.put(Argument.version, Option.builder(Argument.version.name()  )
                .desc( "show version" )
                .numberOfArgs( 0 )
                .build());

        optionMap.put(Argument.port, Option.builder( Argument.port.name() )
                .desc( "web server port (default " + Resource.defaultPort.getValue() + ")" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put(Argument.localAddress, Option.builder( Argument.localAddress.name() )
                .desc( "local network address (default localhost)" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put(Argument.context, Option.builder(Argument.context.name() )
                .desc( "context (url path) name (default " + Resource.defaultContext.getValue() + ")" )
                .numberOfArgs( 1 )
                .build() );

        optionMap.put(Argument.help, Option.builder(Argument.help.name())
                .desc( "show this help" )
                .numberOfArgs( 0 )
                .build());

        optionMap.put(Argument.properties, Option.builder(Argument.properties.name())
                .desc( "read arguments from properties file" )
                .numberOfArgs( 1 )
                .build());

        optionMap.put(Argument.war, Option.builder(Argument.war.name())
                .desc( "source war file (default embedded)" )
                .numberOfArgs( 1 )
                .build());

        return Collections.unmodifiableMap(optionMap);
    }

}
