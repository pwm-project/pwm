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

package password.pwm.util;

import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.PwmEnvironment;
import password.pwm.bean.SessionLabel;
import password.pwm.config.AppConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.ConfigurationFileManager;
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

    /**
     * Return properties used by the OneJar launcher to configure the web container.  This reads settings from the
     * application configuration.
     * @param appConfig a valid configuration instance
     * @return A List of properties used by the Onejar util
     * @throws Exception If anything goes wrong
     */
    private static Properties createProperties( final AppConfig appConfig )
            throws Exception
    {
        final String sslProtocolSettingValue = ExportHttpsTomcatConfigCommand.TomcatConfigWriter.getTlsProtocolsValue( appConfig );
        final Properties newProps = new Properties();
        newProps.setProperty( "sslEnabledProtocols",  sslProtocolSettingValue );
        final String ciphers = appConfig.readSettingAsString( PwmSetting.HTTPS_CIPHERS );
        if ( StringUtil.notEmpty( ciphers ) )
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
        final ConfigurationFileManager configReader = new ConfigurationFileManager( configFile, SessionLabel.ONEJAR_LABEL );
        final AppConfig config = configReader.getConfiguration();
        final PwmEnvironment pwmEnvironment = PwmEnvironment.builder()
                .config( config )
                .applicationPath( applicationPath )
                .applicationMode( PwmApplicationMode.READ_ONLY )
                .configurationFile( configFile )
                .flags( Collections.singleton( PwmEnvironment.ApplicationFlag.CommandLineInstance ) )
                .build();

        return PwmApplication.createPwmApplication( pwmEnvironment );
    }
}
