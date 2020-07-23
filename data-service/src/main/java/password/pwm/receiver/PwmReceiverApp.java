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

import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.StringUtil;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PwmReceiverApp
{
    private static final PwmReceiverLogger LOGGER = PwmReceiverLogger.forClass( PwmReceiverApp.class );
    private static final String ENV_NAME = "DATA_SERVICE_PROPS";

    private Storage storage;
    private ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Settings settings;
    private Status status = new Status();

    public PwmReceiverApp( )
    {
        final String propsFile = System.getenv( ENV_NAME );
        if ( StringUtil.isEmpty( propsFile ) )
        {
            final String errorMsg = "Missing environment variable '" + ENV_NAME + "', can't load configuration";
            status.setErrorState( errorMsg );
            LOGGER.error( errorMsg );
            return;
        }

        try
        {
            settings = Settings.readFromFile( propsFile );
        }
        catch ( final IOException e )
        {
            final String errorMsg = "can't read configuration: " + JavaHelper.readHostileExceptionMessage( e );
            status.setErrorState( errorMsg );
            LOGGER.error( errorMsg, e );
            return;
        }

        try
        {
            storage = new Storage( settings );
        }
        catch ( final Exception e )
        {
            final String errorMsg = "can't start storage system: " + JavaHelper.readHostileExceptionMessage( e );
            status.setErrorState( errorMsg );
            LOGGER.error( errorMsg, e );
            return;
        }

        if ( settings.getSetting( Settings.Setting.ftpSite ) != null && !settings.getSetting( Settings.Setting.ftpSite ).isEmpty() )
        {
            final Runnable ftpThread = ( ) ->
            {
                final FtpDataIngestor ftpDataIngestor = new FtpDataIngestor( this, settings );
                ftpDataIngestor.readData( storage );
            };
            scheduledExecutorService.scheduleAtFixedRate( ftpThread, 0, 1, TimeUnit.HOURS );
        }
    }

    public Settings getSettings( )
    {
        return settings;
    }

    public Storage getStorage( )
    {
        return storage;
    }

    void close( )
    {
        storage.close();
        scheduledExecutorService.shutdown();
    }

    public Status getStatus( )
    {
        return status;
    }

}
