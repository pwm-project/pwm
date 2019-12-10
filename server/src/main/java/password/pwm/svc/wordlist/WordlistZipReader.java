/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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

package password.pwm.svc.wordlist;

import org.apache.commons.io.input.CountingInputStream;
import password.pwm.PwmConstants;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogger;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Jason D. Rivard
 */
class WordlistZipReader implements AutoCloseable, Closeable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( WordlistZipReader.class );

    private final ZipInputStream zipStream;
    private final CountingInputStream countingInputStream;
    private final AtomicLong lineCounter = new AtomicLong( 0 );

    private BufferedReader reader;
    private ZipEntry zipEntry;

    WordlistZipReader( final InputStream inputStream ) throws PwmUnrecoverableException
    {
        Objects.requireNonNull( inputStream );

        countingInputStream = new CountingInputStream( inputStream );
        zipStream = new ZipInputStream( countingInputStream );
        nextZipEntry();
        if ( zipEntry == null )
        {
            throw new IllegalStateException( "input stream does not contain any zip file entries" );
        }
    }

    private void nextZipEntry( )
            throws PwmUnrecoverableException
    {
        try
        {
            zipEntry = zipStream.getNextEntry();

            while ( zipEntry != null && zipEntry.isDirectory() )
            {
                zipEntry = zipStream.getNextEntry();
            }

            if ( zipEntry != null )
            {
                reader = new BufferedReader( new InputStreamReader( zipStream, PwmConstants.DEFAULT_CHARSET ) );
            }
        }
        catch ( final IOException e )
        {
            throw PwmUnrecoverableException.newException( PwmError.ERROR_WORDLIST_IMPORT_ERROR, "error reading wordlist zip: " + e.getMessage() );
        }
    }

    public void close( )
    {
        try
        {
            zipStream.close();
        }
        catch ( final IOException e )
        {
            LOGGER.debug( () -> "error closing zip stream: " + e.getMessage() );
        }

        try
        {
            reader.close();
        }
        catch ( final IOException e )
        {
            LOGGER.debug( () -> "error closing zip stream: " + e.getMessage() );
        }
    }

    String currentZipName( )
    {
        return zipEntry != null ? zipEntry.getName() : "--none--";
    }

    String nextLine( )
            throws PwmUnrecoverableException
    {
        String line;

        do
        {
            try
            {
                line = reader.readLine();
            }
            catch ( final IOException e )
            {
                throw PwmUnrecoverableException.newException( PwmError.ERROR_WORDLIST_IMPORT_ERROR, "error reading zip wordlist file: " + e.getMessage() );
            }

            if ( line == null )
            {
                nextZipEntry();
            }
        }
        while ( line == null && zipEntry != null );


        if ( line != null )
        {
            lineCounter.incrementAndGet();
        }

        return line;
    }

    long getLineCount()
    {
        return lineCounter.get();
    }

    long getByteCount()
    {
        return countingInputStream.getByteCount();
    }
}
