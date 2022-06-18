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

package password.pwm.util.cli.commands;


import com.novell.ldapchai.exception.ChaiUnavailableException;
import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiProviderFactory;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.schema.SchemaManager;
import password.pwm.ldap.schema.SchemaOperationResult;
import password.pwm.util.cli.CliException;
import password.pwm.util.cli.CliParameters;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.json.JsonProvider;
import password.pwm.util.secure.PwmTrustManager;
import password.pwm.util.secure.X509CertInfo;
import password.pwm.util.secure.X509Utils;

import javax.net.ssl.X509TrustManager;
import java.io.Console;
import java.io.IOException;
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

    @Override
    public void doCommand( )
            throws IOException, CliException
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

        try
        {
            doSchemaExtension( ldapUrl, bindDN, bindPW );
        }
        catch ( final URISyntaxException | PwmOperationalException | ChaiUnavailableException | PwmUnrecoverableException e )
        {
            throw new CliException( "error during schema extension: " + e.getMessage(), e );
        }
    }

    private void doSchemaExtension( final String ldapUrl, final String bindDN, final String bindPW )
            throws URISyntaxException, PwmOperationalException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final X509TrustManager trustManager;
        if ( isSecureLDAP( ldapUrl ) )
        {
            final List<X509Certificate> certificates = readCertificates( ldapUrl );
            if ( CollectionUtil.isEmpty( certificates ) )
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

        if ( !promptForContinue( "Proceeding may cause modifications to your LDAP schema." ) )
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
            throws URISyntaxException, PwmOperationalException, IOException
    {
        if ( isSecureLDAP( url ) )
        {
            out( "ldaps certificates from: " + url );
            final List<X509Certificate> certificateList = X509Utils.readRemoteCertificates( new URI ( url ), cliEnvironment.getConfig() );
            out( JsonFactory.get().serializeCollection( X509CertInfo.makeDebugInfoMap( certificateList ), JsonProvider.Flag.PrettyPrint ) );
            return certificateList;
        }
        return Collections.emptyList();
    }


    @Override
    public CliParameters getCliParameters( )
    {
        final CliParameters.Option ldapUrlOption = new CliParameters.Option()
        {
            @Override
            public boolean isOptional( )
            {
                return false;
            }

            @Override
            public Type getType( )
            {
                return Type.STRING;
            }

            @Override
            public String getName( )
            {
                return OPTION_LDAPURL;
            }
        };

        final CliParameters.Option bindDN = new CliParameters.Option()
        {
            @Override
            public boolean isOptional( )
            {
                return false;
            }

            @Override
            public Type getType( )
            {
                return Type.STRING;
            }

            @Override
            public String getName( )
            {
                return OPTION_BIND_DN;
            }
        };

        final CliParameters.Option bindPassword = new CliParameters.Option()
        {
            @Override
            public boolean isOptional( )
            {
                return true;
            }

            @Override
            public Type getType( )
            {
                return Type.STRING;
            }

            @Override
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
