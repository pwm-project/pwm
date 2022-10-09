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

package password.pwm.util.java;

import org.jrivard.xmlchai.AccessMode;
import org.jrivard.xmlchai.XmlChai;
import org.jrivard.xmlchai.XmlDocument;
import org.jrivard.xmlchai.XmlFactory;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.io.InputStream;
import java.io.OutputStream;
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
        benchmarkImpl( );
    }

    private void benchmarkImpl ()
            throws Exception
    {
        final XmlFactory xmlFactory = XmlChai.getFactory();
        final InputStream xmlFactoryTestXmlFile = XmlFactoryTest.class.getResourceAsStream( "XmlFactoryTest.xml" );
        final XmlDocument xmlDocument = xmlFactory.parse( xmlFactoryTestXmlFile, AccessMode.IMMUTABLE );
        xmlFactory.output( xmlDocument, OutputStream.nullOutputStream() );
    }
}
