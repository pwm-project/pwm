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

import java.io.Console;
import java.io.IOException;
import java.util.Scanner;

public abstract class AbstractCliCommand implements CliCommand  {
    protected CliEnvironment cliEnvironment;

    protected AbstractCliCommand()
    {
    }

    void out(final CharSequence out) {
        if (cliEnvironment != null && cliEnvironment.getDebugWriter() != null) {
            try {
                cliEnvironment.getDebugWriter().append(out);
                cliEnvironment.getDebugWriter().append("\n");
                cliEnvironment.getDebugWriter().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void execute(
            String cli,
            CliEnvironment cliEnvironment
    )
    {
        this.cliEnvironment = cliEnvironment;

        try {
            doCommand();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean promptForContinue(final String msg) {
        if (cliEnvironment.getMainOptions().isForceFlag()) {
            return true;
        }
        out(msg);
        out("");
        out("To proceed, type 'continue'");
        final Scanner scanner = new Scanner(System.in);
        final String input = scanner.nextLine();

        if (!"continue".equalsIgnoreCase(input)) {
            out("exiting...");
            return false;
        }
        return true;
    }

    abstract void doCommand() throws Exception;

    String promptForPassword() {
        final Console console = System.console();
        console.writer().write("enter password:");
        console.writer().flush();
        final String password = new String(console.readPassword());
        console.writer().write("verify password:");
        console.writer().flush();
        final String verify  = new String(console.readPassword());
        if (!password.equals(verify)) {
            out("verify password incorrect, exiting...");
            System.exit(-1);
        }
        return password;
    }
}
