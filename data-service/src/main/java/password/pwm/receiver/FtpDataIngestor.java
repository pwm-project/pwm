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

package password.pwm.receiver;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPSClient;
import password.pwm.PwmConstants;
import password.pwm.bean.TelemetryPublishBean;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.StringUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

class FtpDataIngestor
{

    private static final PwmReceiverLogger LOGGER = PwmReceiverLogger.forClass( FtpDataIngestor.class );

    private final Settings settings;
    private final PwmReceiverApp app;

    FtpDataIngestor( final PwmReceiverApp app, final Settings telemetrySettings )
    {
        this.app = app;
        this.settings = telemetrySettings;
    }

    void readData( final Storage storage )
    {
        app.getStatus().setLastFtpStatus( "beginning ftp ingestion" );
        LOGGER.debug( "beginning ftp ingestion" );
        app.getStatus().setLastFtpIngest( Instant.now() );
        try
        {
            final FTPClient ftpClient = getFtpClient();
            final List<String> files = getFiles( ftpClient );
            LOGGER.debug( "beginning ftp ingestion, listed " + files.size() + " files from server" );
            for ( final String fileName : files )
            {
                if ( fileName != null && fileName.endsWith( ".zip" ) )
                {
                    app.getStatus().setLastFtpIngest( Instant.now() );
                    app.getStatus().setLastFtpStatus( "reading file " + fileName );
                    LOGGER.debug( "read file " + fileName );
                    try
                    {
                        readFile( ftpClient, fileName, storage );
                    }
                    catch ( final Exception e )
                    {
                        app.getStatus().setLastFtpIngest( Instant.now() );
                        final String msg = "error while reading ftp file '" + fileName + "': " + e.getMessage();
                        app.getStatus().setLastFtpStatus( msg );
                        LOGGER.error( msg );
                    }
                }
                else
                {
                    LOGGER.info( "skipping ftp file " + fileName );
                }
            }
            ftpClient.disconnect();
            LOGGER.info( "completed ftp ingestion" );
            app.getStatus().setLastFtpStatus( "completed successfully" );
            app.getStatus().setLastFtpIngest( Instant.now() );
            app.getStatus().setLastFtpFilesRead( files.size() );
        }
        catch ( final Exception e )
        {
            app.getStatus().setLastFtpIngest( Instant.now() );
            app.getStatus().setLastFtpStatus( "error during ftp scan: " + e.getMessage() );
        }
    }

    private void readFile( final FTPClient ftpClient, final String fileName, final Storage storage ) throws Exception
    {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ftpClient.retrieveFile( fileName, byteArrayOutputStream );
        final ByteArrayInputStream inputStream = new ByteArrayInputStream( byteArrayOutputStream.toByteArray() );
        readZippedByteStream( inputStream, fileName, storage );
    }

    private void readZippedByteStream( final InputStream inputStream, final String fileName, final Storage storage ) throws Exception
    {
        try
        {
            final ZipInputStream zipInputStream = new ZipInputStream( inputStream );
            final ZipEntry zipEntry = zipInputStream.getNextEntry();
            final String zipEntryName = zipEntry.getName();
            if ( zipEntryName != null && zipEntryName.endsWith( ".json" ) )
            {
                LOGGER.info( "reading ftp file " + fileName + ":" + zipEntryName );
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                final byte[] buffer = new byte[ 1024 ];
                int len;
                while ( ( len = zipInputStream.read( buffer ) ) > 0 )
                {
                    byteArrayOutputStream.write( buffer, 0, len );
                }
                final String resultsStr = byteArrayOutputStream.toString( PwmConstants.DEFAULT_CHARSET.name() );
                final TelemetryPublishBean bean = JsonUtil.deserialize( resultsStr, TelemetryPublishBean.class );
                storage.store( bean );
            }
        }
        catch ( final Exception e )
        {
            final String msg = "error reading ftp file '" + fileName + "', error: " + e.getMessage();
            LOGGER.info( msg );
            throw new Exception( e );
        }
    }

    private List<String> getFiles( final FTPClient ftpClient ) throws IOException
    {
        final String pathname = settings.getSetting( Settings.Setting.ftpReadPath );
        final FTPFile[] files = ftpClient.listFiles( pathname );
        final List<String> returnFiles = new ArrayList<>();
        for ( final FTPFile ftpFile : files )
        {
            final String name = ftpFile.getName();
            final String fullPath = pathname + "/" + name;
            returnFiles.add( fullPath );
        }

        return Collections.unmodifiableList( returnFiles );
    }

    private FTPClient getFtpClient( ) throws IOException
    {
        final FTPClient ftpClient;
        final Settings.FtpMode ftpMode = Settings.FtpMode.valueOf( settings.getSetting( Settings.Setting.ftpMode ) );
        switch ( ftpMode )
        {
            case ftp:
                ftpClient = new FTPClient();
                break;

            case ftps:
                ftpClient = new FTPSClient();
                break;

            default:
                throw new IllegalArgumentException( "unexpected ftp mode" );
        }

        ftpClient.connect( settings.getSetting( Settings.Setting.ftpSite ) );
        LOGGER.info( "ftp connect complete" );
        if ( !StringUtil.isEmpty( settings.getSetting( Settings.Setting.ftpUser ) ) && !StringUtil.isEmpty( settings.getSetting( Settings.Setting.ftpPassword ) ) )
        {
            final boolean loggedInSuccess = ftpClient.login( settings.getSetting( Settings.Setting.ftpUser ), settings.getSetting( Settings.Setting.ftpPassword ) );
            LOGGER.info( "ftp login complete, success=" + loggedInSuccess );
        }
        ftpClient.enterLocalPassiveMode();
        return ftpClient;
    }
}
