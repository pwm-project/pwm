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

package password.pwm.util.java;

import org.apache.commons.io.output.NullOutputStream;
import org.junit.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class XmlFactoryBenchmarkExtendedTest
{
    @Test
    public void
    launchBenchmark()
            throws Exception
    {
        final Options opt = new OptionsBuilder()
                .include( this.getClass().getName() + ".*" )
                .mode ( Mode.AverageTime )
                .timeUnit( TimeUnit.MILLISECONDS )
                .warmupTime( TimeValue.seconds( 10 ) )
                .measurementIterations( 10 )
                .threads( 1 )
                .forks( 1 )
                .shouldFailOnError( true )
                .shouldDoGC( true )
                .jvmArgs( "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n" )
                .build();

        new Runner( opt ).run();
    }

    @Benchmark
    public void benchmarkW3c ()
            throws Exception
    {
        benchmarkImpl( XmlFactory.FactoryType.W3C );
    }

    @Benchmark
    public void benchmarkJDom ()
            throws Exception
    {
        benchmarkImpl( XmlFactory.FactoryType.JDOM );
    }

    private void benchmarkImpl ( final XmlFactory.FactoryType factoryType )
            throws Exception
    {
        final XmlFactory xmlFactory = XmlFactory.getFactory( factoryType );
        final InputStream xmlFactoryTestXmlFile = XmlFactoryTest.class.getResourceAsStream( "XmlFactoryTest.xml" );
        final XmlDocument xmlDocument = xmlFactory.parseXml( xmlFactoryTestXmlFile );
        xmlFactory.outputDocument( xmlDocument, new NullOutputStream() );
    }
}
