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

package password.pwm.svc.wordlist;

import password.pwm.PwmConstants;
import password.pwm.util.logging.PwmLogger;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * @author Jason D. Rivard
 */
class ZipReader implements AutoCloseable, Closeable
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ZipReader.class );

    private final ZipInputStream zipStream;

    private BufferedReader reader;
    private ZipEntry zipEntry;
    private int lineCounter = 0;

    ZipReader( final InputStream inputStream )
            throws Exception
    {
        zipStream = new ZipInputStream( inputStream );
        nextZipEntry();
        if ( zipEntry == null )
        {
            throw new IllegalStateException( "input stream does not contain any zip file entries" );
        }
    }

    private void nextZipEntry( )
            throws IOException
    {
        if ( zipEntry != null )
        {
            LOGGER.trace( "finished reading " + zipEntry.getName() + ", lines=" + lineCounter );
        }

        zipEntry = zipStream.getNextEntry();

        while ( zipEntry != null && zipEntry.isDirectory() )
        {
            zipEntry = zipStream.getNextEntry();
        }

        if ( zipEntry != null )
        {
            lineCounter = 0;
            reader = new BufferedReader( new InputStreamReader( zipStream, PwmConstants.DEFAULT_CHARSET ) );
        }
    }

    public void close( )
    {
        try
        {
            zipStream.close();
        }
        catch ( IOException e )
        {
            LOGGER.debug( "error closing zip stream: " + e.getMessage() );
        }

        try
        {
            reader.close();
        }
        catch ( IOException e )
        {
            LOGGER.debug( "error closing zip stream: " + e.getMessage() );
        }
    }

    String currentZipName( )
    {
        return zipEntry != null ? zipEntry.getName() : "--none--";
    }

    String nextLine( )
            throws IOException
    {
        String line;

        do
        {
            line = reader.readLine();

            if ( line == null )
            {
                nextZipEntry();
            }
        }
        while ( line == null && zipEntry != null );


        if ( line != null )
        {
            lineCounter++;
        }

        return line;
    }
}
