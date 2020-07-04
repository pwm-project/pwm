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
