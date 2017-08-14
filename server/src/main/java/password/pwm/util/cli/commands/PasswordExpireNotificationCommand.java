/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.util.cli.commands;

import password.pwm.PwmApplication;
import password.pwm.svc.pwnotify.PasswordExpireNotificationEngine;
import password.pwm.util.cli.CliParameters;

import java.util.Collections;

public class PasswordExpireNotificationCommand extends AbstractCliCommand {
    public void doCommand()
            throws Exception
    {
        final PwmApplication pwmApplication = cliEnvironment.getPwmApplication();
        final PasswordExpireNotificationEngine engine = new PasswordExpireNotificationEngine(pwmApplication);
        engine.executeJob();
    }

    @Override
    public CliParameters getCliParameters()
    {
        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "PasswordExpirationNotification";
        cliParameters.description = "Run the password expiration notification batch process";
        cliParameters.options = Collections.emptyList();
        cliParameters.needsPwmApplication = true;
        cliParameters.needsLocalDB = true;
        cliParameters.readOnly = false;
        return cliParameters;
    }
}
