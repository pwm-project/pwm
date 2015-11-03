/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

package password.pwm.util.cli;

import password.pwm.PwmApplication;
import password.pwm.config.Configuration;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.util.localdb.LocalDB;

import java.io.File;
import java.io.Writer;
import java.util.Map;

public class CliEnvironment {
    final ConfigurationReader configurationReader;
    final File configurationFile;
    final Configuration config;
    final File applicationPath;
    final PwmApplication pwmApplication;
    final LocalDB localDB;
    final Writer debugWriter;
    final Map<String,Object> options;
    final MainClass.MainOptions mainOptions;

    public CliEnvironment(
            ConfigurationReader configurationReader,
            File configurationFile,
            Configuration config,
            File applicationPath,
            PwmApplication pwmApplication,
            LocalDB localDB,
            Writer debugWriter,
            Map<String, Object> options,
            MainClass.MainOptions mainOptions
    )
    {
        this.configurationReader = configurationReader;
        this.configurationFile = configurationFile;
        this.config = config;
        this.applicationPath = applicationPath;
        this.pwmApplication = pwmApplication;
        this.localDB = localDB;
        this.debugWriter = debugWriter;
        this.options = options;
        this.mainOptions = mainOptions;
    }

    public Configuration getConfig()
    {
        return config;
    }

    public PwmApplication getPwmApplication()
    {
        return pwmApplication;
    }

    public LocalDB getLocalDB()
    {
        return localDB;
    }

    public Writer getDebugWriter()
    {
        return debugWriter;
    }

    public Map<String, Object> getOptions()
    {
        return options;
    }

    public File getApplicationPath()
    {
        return applicationPath;
    }

    public ConfigurationReader getConfigurationReader() {
        return configurationReader;
    }

    public MainClass.MainOptions getMainOptions() {
        return mainOptions;
    }
}
