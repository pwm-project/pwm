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

package password.pwm.util;

import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.cli.commands.ExportHttpsTomcatConfigCommand;
import password.pwm.util.java.StringUtil;
import password.pwm.util.secure.HttpsServerCertificateManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.util.Collections;
import java.util.Properties;

public class OnejarHelper
{
    /**
     * Invoked (via reflection) by tomcatOneJar class in Onejar module.
     * @param applicationPath application path containing configuration file.
     * @return Properties with tomcat connector parameters.
     * @throws PwmUnrecoverableException if problem loading config
     */
    public static Properties onejarHelper(
            final String applicationPath,
            final String keystorePath,
            final String alias,
            final String password
    )
            throws Exception
    {
        final PwmApplication pwmApplication = makePwmApplication( new File( applicationPath ) );
        try
        {
            exportKeystore( pwmApplication, password, alias, new File( keystorePath ) );
            return createProperties( pwmApplication.getConfig() );
        }
        finally
        {
            pwmApplication.shutdown();
        }
    }

    private static Properties createProperties( final Configuration configuration )
            throws Exception
    {
        final String sslProtocolSettingValue = ExportHttpsTomcatConfigCommand.TomcatConfigWriter.getTlsProtocolsValue( configuration );
        final Properties newProps = new Properties();
        newProps.setProperty( "sslEnabledProtocols",  sslProtocolSettingValue );
        final String ciphers = configuration.readSettingAsString( PwmSetting.HTTPS_CIPHERS );
        if ( !StringUtil.isEmpty( ciphers ) )
        {
            newProps.setProperty( "ciphers", ciphers );
        }
        return newProps;
    }

    private static void exportKeystore(
            final PwmApplication pwmApplication,
            final String password,
            final String alias,
            final File exportFile
    )
            throws Exception
    {
        final KeyStore keyStore = HttpsServerCertificateManager.keyStoreForApplication(
                pwmApplication,
                new PasswordData( password ),
                alias );
        try ( OutputStream outputStream = new FileOutputStream( exportFile ) )
        {
            keyStore.store( outputStream, password.toCharArray() );
        }
    }

    private static PwmApplication makePwmApplication( final File applicationPath )
            throws Exception
    {
        final File configFile = new File( applicationPath + File.separator + PwmConstants.DEFAULT_CONFIG_FILE_FILENAME );
        final ConfigurationReader configReader = new ConfigurationReader( configFile );
        final Configuration config = configReader.getConfiguration();
        final PwmEnvironment pwmEnvironment = new PwmEnvironment.Builder( config, applicationPath )
                .setApplicationMode( PwmApplicationMode.READ_ONLY )
                .setConfigurationFile( configFile )
                .setFlags( Collections.singleton( PwmEnvironment.ApplicationFlag.CommandLineInstance ) )
                .createPwmEnvironment();
        return new PwmApplication( pwmEnvironment );
    }



}
