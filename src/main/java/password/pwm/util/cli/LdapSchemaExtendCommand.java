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


import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import password.pwm.ldap.schema.SchemaManager;
import password.pwm.ldap.schema.SchemaOperationResult;

import java.io.Console;
import java.util.Arrays;

public class LdapSchemaExtendCommand extends AbstractCliCommand {
    private static final String OPTION_LDAPURL = "ldapURL";
    private static final String OPTION_BIND_DN = "bindDN";
    private static final String OPTION_BIND_PW = "bindPassword";

    public void doCommand()
            throws Exception
    {
        final String ldapUrl = (String)cliEnvironment.getOptions().get(OPTION_LDAPURL);
        final String bindDN = (String)cliEnvironment.getOptions().get(OPTION_BIND_DN);
        final String bindPW;
        if (cliEnvironment.getOptions().containsKey(OPTION_BIND_PW)) {
            bindPW = (String)cliEnvironment.getOptions().get(OPTION_BIND_PW);
        } else {
            final Console console = System.console();
            console.writer().write("enter " +  OPTION_BIND_PW + ":");
            console.writer().flush();
            bindPW = new String(console.readPassword());
        }
        final ChaiProvider chaiProvider = ChaiProviderFactory.createProvider(ldapUrl, bindDN, bindPW);
        final SchemaOperationResult operationResult = SchemaManager.extendSchema(chaiProvider);
        final boolean checkOk = operationResult.isSuccess();
        if (checkOk) {
            out("schema extension complete.  all extensions in place = " + checkOk);
        } else {
            out("schema extension did not complete.\n" + operationResult.getOperationLog());
        }
    }


    public CliParameters getCliParameters()
    {
        final CliParameters.Option ldapUrlOption = new CliParameters.Option() {
            public boolean isOptional()
            {
                return false;
            }

            public type getType()
            {
                return type.STRING;
            }

            public String getName()
            {
                return OPTION_LDAPURL;
            }
        };

        final CliParameters.Option bindDN = new CliParameters.Option() {
            public boolean isOptional()
            {
                return false;
            }

            public type getType()
            {
                return type.STRING;
            }

            public String getName()
            {
                return OPTION_BIND_DN;
            }
        };

        final CliParameters.Option bindPassword = new CliParameters.Option() {
            public boolean isOptional()
            {
                return true;
            }

            public type getType()
            {
                return type.STRING;
            }

            public String getName()
            {
                return OPTION_BIND_PW;
            }
        };

        CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "LdapSchemaExtend";
        cliParameters.description = "Extend an LDAP schema with standard extensions";
        cliParameters.options = Arrays.asList(new CliParameters.Option[]{ldapUrlOption, bindDN, bindPassword});
        cliParameters.needsPwmApplication = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }

}
