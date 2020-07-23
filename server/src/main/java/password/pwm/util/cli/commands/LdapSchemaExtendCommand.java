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

package password.pwm.util.cli.commands;


import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import password.pwm.error.PwmOperationalException;
import password.pwm.ldap.schema.SchemaManager;
import password.pwm.ldap.schema.SchemaOperationResult;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.JavaHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.secure.PwmTrustManager;
import password.pwm.util.secure.X509Utils;

import javax.net.ssl.X509TrustManager;
import java.io.Console;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LdapSchemaExtendCommand extends AbstractCliCommand
{
    private static final String OPTION_LDAPURL = "ldapURL";
    private static final String OPTION_BIND_DN = "bindDN";
    private static final String OPTION_BIND_PW = "bindPassword";

    public void doCommand( )
            throws Exception
    {
        final String ldapUrl = ( String ) cliEnvironment.getOptions().get( OPTION_LDAPURL );
        final String bindDN = ( String ) cliEnvironment.getOptions().get( OPTION_BIND_DN );
        final String bindPW;
        if ( cliEnvironment.getOptions().containsKey( OPTION_BIND_PW ) )
        {
            bindPW = ( String ) cliEnvironment.getOptions().get( OPTION_BIND_PW );
        }
        else
        {
            final Console console = System.console();
            console.writer().write( "enter " + OPTION_BIND_PW + ":" );
            console.writer().flush();
            bindPW = new String( console.readPassword() );
        }

        final X509TrustManager trustManager;
        if ( isSecureLDAP( ldapUrl ) )
        {
            final List<X509Certificate> certificates = readCertificates( ldapUrl );
            if ( JavaHelper.isEmpty( certificates ) )
            {
                out( "canceled" );
                return;
            }
            trustManager = PwmTrustManager.createPwmTrustManager( cliEnvironment.getConfig(), certificates );
        }
        else
        {
            trustManager = null;
        }

        if ( !promptForContinue( "Proceeding may cause modificiations to your LDAP schema." ) )
        {
            return;
        }

        final ChaiProviderFactory chaiProviderFactory = ChaiProviderFactory.newProviderFactory( );
        final ChaiConfiguration chaiConfiguration = ChaiConfiguration.builder( ldapUrl, bindDN, bindPW )
                .setTrustManager( new X509TrustManager[]
                        {
                                trustManager,
                        }
                )
                .build();
        final ChaiProvider chaiProvider = chaiProviderFactory.newProvider( chaiConfiguration );

        out( "beginning extension check" );
        final SchemaOperationResult operationResult = SchemaManager.extendSchema( chaiProvider );

        out( operationResult.getOperationLog() );
        final boolean checkOk = operationResult.isSuccess();

        if ( checkOk )
        {
            out( "schema extension completed successfully.\n" );
        }
        else
        {
            out( "schema extension did not complete.\n" );
        }
    }

    private boolean isSecureLDAP( final String ldapUrl ) throws URISyntaxException
    {
        final URI ldapUri = new URI( ldapUrl );
        return "ldaps".equalsIgnoreCase( ldapUri.getScheme() );
    }

    private List<X509Certificate> readCertificates( final String url )
            throws URISyntaxException, PwmOperationalException
    {
        if ( isSecureLDAP( url ) )
        {
            out( "ldaps certificates from: " + url );
            final List<X509Certificate> certificateList = X509Utils.readRemoteCertificates( new URI ( url ), cliEnvironment.getConfig() );
            out( JsonUtil.serializeCollection( X509Utils.makeDebugInfoMap( certificateList ), JsonUtil.Flag.PrettyPrint ) );
            return certificateList;
        }
        return Collections.emptyList();
    }


    public CliParameters getCliParameters( )
    {
        final CliParameters.Option ldapUrlOption = new CliParameters.Option()
        {
            public boolean isOptional( )
            {
                return false;
            }

            public Type getType( )
            {
                return Type.STRING;
            }

            public String getName( )
            {
                return OPTION_LDAPURL;
            }
        };

        final CliParameters.Option bindDN = new CliParameters.Option()
        {
            public boolean isOptional( )
            {
                return false;
            }

            public Type getType( )
            {
                return Type.STRING;
            }

            public String getName( )
            {
                return OPTION_BIND_DN;
            }
        };

        final CliParameters.Option bindPassword = new CliParameters.Option()
        {
            public boolean isOptional( )
            {
                return true;
            }

            public Type getType( )
            {
                return Type.STRING;
            }

            public String getName( )
            {
                return OPTION_BIND_PW;
            }
        };

        final CliParameters cliParameters = new CliParameters();
        cliParameters.commandName = "LdapSchemaExtend";
        cliParameters.description = "Extend an LDAP schema with standard extensions";
        cliParameters.options = Arrays.asList( ldapUrlOption, bindDN, bindPassword );
        cliParameters.needsPwmApplication = false;
        cliParameters.readOnly = true;
        return cliParameters;
    }

}
