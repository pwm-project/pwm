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

package password.pwm.util.localdb;

import com.novell.ldapchai.ChaiUser;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Layout;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmEnvironment;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.StringValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.logging.PwmLogLevel;

import java.io.File;

public class TestHelper
{
    public static void setupLogging()
    {
        final String pwmPackageName = PwmApplication.class.getPackage().getName();
        final Logger pwmPackageLogger = Logger.getLogger( pwmPackageName );
        final String chaiPackageName = ChaiUser.class.getPackage().getName();
        final Logger chaiPackageLogger = Logger.getLogger( chaiPackageName );
        final Layout patternLayout = new PatternLayout( "%d{yyyy-MM-dd HH:mm:ss}, %-5p, %c{2}, %m%n" );
        final ConsoleAppender consoleAppender = new ConsoleAppender( patternLayout );
        final Level level = Level.TRACE;
        pwmPackageLogger.addAppender( consoleAppender );
        pwmPackageLogger.setLevel( level );
        chaiPackageLogger.addAppender( consoleAppender );
        chaiPackageLogger.setLevel( level );
    }

    public static PwmApplication makeTestPwmApplication( final File tempFolder )
            throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = StoredConfigurationFactory.newConfig();
        final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
        modifier.writeSetting( PwmSetting.EVENTS_JAVA_STDOUT_LEVEL, null, new StringValue( PwmLogLevel.FATAL.toString() ), null );
        final Configuration configuration = new Configuration( modifier.newStoredConfiguration() );
        return makeTestPwmApplication( tempFolder, configuration );
    }

    public static PwmApplication makeTestPwmApplication( final File tempFolder, final Configuration configuration )
            throws PwmUnrecoverableException
    {
        Logger.getRootLogger().setLevel( Level.OFF );
        final PwmEnvironment pwmEnvironment = new PwmEnvironment.Builder( configuration, tempFolder )
                .setApplicationMode( PwmApplicationMode.READ_ONLY )
                .setInternalRuntimeInstance( true )
                .createPwmEnvironment();
        return PwmApplication.createPwmApplication( pwmEnvironment );
    }
}
