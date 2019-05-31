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
import password.pwm.config.stored.StoredConfigurationImpl;
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
        Logger.getRootLogger().setLevel( Level.OFF );
        final StoredConfigurationImpl storedConfiguration = StoredConfigurationImpl.newStoredConfiguration();
        storedConfiguration.writeSetting( PwmSetting.EVENTS_JAVA_STDOUT_LEVEL, new StringValue( PwmLogLevel.FATAL.toString() ), null );
        final Configuration configuration = new Configuration( storedConfiguration );
        final PwmEnvironment pwmEnvironment = new PwmEnvironment.Builder( configuration, tempFolder )
                .setApplicationMode( PwmApplicationMode.READ_ONLY )
                .setInternalRuntimeInstance( true )
                .createPwmEnvironment();
        return new PwmApplication( pwmEnvironment );
    }
}
