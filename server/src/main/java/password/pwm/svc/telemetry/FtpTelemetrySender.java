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

package password.pwm.svc.telemetry;

import lombok.Value;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.TelemetryPublishBean;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.MiscUtil;
import password.pwm.util.json.JsonProvider;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FtpTelemetrySender implements TelemetrySender
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( FtpTelemetrySender.class );

    private SessionLabel sessionLabel;
    private Settings settings;

    @Override
    public void init( final PwmApplication pwmApplication, final SessionLabel sessionLabel, final String initString )
    {
        this.sessionLabel = sessionLabel;
        settings = JsonFactory.get().deserialize( initString, Settings.class );
    }

    @Override
    public void publish( final TelemetryPublishBean telemetryPublishBean ) throws PwmUnrecoverableException
    {
        ftpPut( telemetryPublishBean );
    }

    private void ftpPut( final TelemetryPublishBean telemetryPublishBean ) throws PwmUnrecoverableException
    {
        final FTPClient ftpClient;
        switch ( settings.getFtpMode() )
        {
            case ftp:
                ftpClient = new FTPClient();
                break;

            case ftps:
                ftpClient = new FTPSClient();
                break;

            default:
                MiscUtil.unhandledSwitchStatement( settings.getFtpMode() );
                throw new UnsupportedOperationException();
        }


        // connect
        try
        {
            LOGGER.trace( sessionLabel, () -> "establishing " + settings.getFtpMode() + " connection to " + settings.getHost() );
            ftpClient.connect( settings.getHost() );

            final int reply = ftpClient.getReplyCode();
            if ( !FTPReply.isPositiveCompletion( reply ) )
            {
                disconnectFtpClient( ftpClient );
                final String msg = "error " + reply + " connecting to " + settings.getHost();
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TELEMETRY_SEND_ERROR, msg ) );
            }

            LOGGER.trace( sessionLabel, () -> "connected to " + settings.getHost() );
        }
        catch ( final IOException e )
        {
            disconnectFtpClient( ftpClient );
            final String msg = "unable to connect to " + settings.getHost() + ", error: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TELEMETRY_SEND_ERROR, msg ) );
        }

        // set modes
        try
        {
            ftpClient.enterLocalPassiveMode();
            ftpClient.setFileType( FTP.BINARY_FILE_TYPE );

            final int reply = ftpClient.getReplyCode();
            if ( !FTPReply.isPositiveCompletion( reply ) )
            {
                disconnectFtpClient( ftpClient );
                final String msg = "error setting file type mode to binary, error=" + reply;
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TELEMETRY_SEND_ERROR, msg ) );
            }
        }
        catch ( final IOException e )
        {
            disconnectFtpClient( ftpClient );
            final String msg = "unable to connect to " + settings.getHost() + ", error: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TELEMETRY_SEND_ERROR, msg ) );
        }

        // authenticate
        try
        {
            ftpClient.login( settings.getUsername(), settings.getPassword() );

            final int reply = ftpClient.getReplyCode();
            if ( !FTPReply.isPositiveCompletion( reply ) )
            {
                disconnectFtpClient( ftpClient );
                final String msg = "error authenticating as " + settings.getUsername() + " to " + settings.getHost() + ", error=" + reply;
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TELEMETRY_SEND_ERROR, msg ) );
            }

            LOGGER.trace( sessionLabel, () -> "authenticated to " + settings.getHost() + " as " + settings.getUsername() );
        }
        catch ( final IOException e )
        {
            disconnectFtpClient( ftpClient );
            final String msg = "error authenticating as " + settings.getUsername() + " to " + settings.getHost() + ", error: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TELEMETRY_SEND_ERROR, msg ) );
        }


        // upload
        try
        {
            final String filePath = settings.getPath() + "/" + telemetryPublishBean.getId() + ".zip";
            final byte[] fileBytes = dataToJsonZipFile( telemetryPublishBean );
            final ByteArrayInputStream fileStream = new ByteArrayInputStream( fileBytes );

            LOGGER.trace( sessionLabel, () -> "preparing to transfer " + fileBytes.length + " bytes to file path " + filePath );

            final Instant startTime = Instant.now();
            ftpClient.storeFile( filePath, fileStream );

            final int reply = ftpClient.getReplyCode();
            if ( !FTPReply.isPositiveCompletion( reply ) )
            {
                disconnectFtpClient( ftpClient );
                final String msg = "error uploading file  to " + settings.getHost() + ", error=" + reply;
                throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TELEMETRY_SEND_ERROR, msg ) );
            }

            LOGGER.trace( sessionLabel, () -> "completed transfer of " + fileBytes.length + " in ", TimeDuration.fromCurrent( startTime ) );
        }
        catch ( final IOException e )
        {
            disconnectFtpClient( ftpClient );
            final String msg = "error uploading file  to " + settings.getHost() + ", error: " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_TELEMETRY_SEND_ERROR, msg ) );
        }
    }

    private void disconnectFtpClient( final FTPClient ftpClient )
    {
        if ( ftpClient.isConnected() )
        {
            try
            {
                ftpClient.disconnect();
                LOGGER.trace( sessionLabel, () -> "disconnected" );
            }
            catch ( final IOException e )
            {
                LOGGER.trace( sessionLabel, () -> "error while disconnecting ftp client: " + e.getMessage() );
            }
        }
    }

    @Value
    private static class Settings implements Serializable
    {
        private FtpMode ftpMode;
        private String host;
        private String username;
        private String password;
        private String path;

        enum FtpMode
        {
            ftp,
            ftps,
        }
    }

    private static byte[] dataToJsonZipFile( final TelemetryPublishBean telemetryPublishBean ) throws IOException
    {
        final String jsonData = JsonFactory.get().serialize( telemetryPublishBean, JsonProvider.Flag.PrettyPrint );
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final ZipOutputStream zipOutputStream = new ZipOutputStream( byteArrayOutputStream );
        final ZipEntry e = new ZipEntry( telemetryPublishBean.getId() + ".json" );
        zipOutputStream.putNextEntry( e );

        final byte[] data = jsonData.getBytes( PwmConstants.DEFAULT_CHARSET );
        zipOutputStream.write( data, 0, data.length );
        zipOutputStream.closeEntry();
        zipOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }

}
