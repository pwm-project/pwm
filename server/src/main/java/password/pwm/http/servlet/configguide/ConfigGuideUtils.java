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

package password.pwm.http.servlet.configguide;

import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiSetting;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.PwmSession;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.schema.SchemaManager;
import password.pwm.ldap.schema.SchemaOperationResult;
import password.pwm.util.LDAPPermissionCalculator;
import password.pwm.util.java.Percent;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.X509Utils;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;

public class ConfigGuideUtils
{

    private static final PwmLogger LOGGER = PwmLogger.getLogger( ConfigGuideUtils.class.getName() );

    static void writeConfig(
            final ContextManager contextManager,
            final ConfigGuideBean configGuideBean
    ) throws PwmOperationalException, PwmUnrecoverableException
    {
        final StoredConfigurationImpl storedConfiguration = ConfigGuideForm.generateStoredConfig( configGuideBean );
        final String configPassword = configGuideBean.getFormData().get( ConfigGuideFormField.PARAM_CONFIG_PASSWORD );
        if ( configPassword != null && configPassword.length() > 0 )
        {
            storedConfiguration.setPassword( configPassword );
        }
        else
        {
            storedConfiguration.writeConfigProperty( ConfigurationProperty.PASSWORD_HASH, null );
        }

        storedConfiguration.writeConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE, "false" );
        ConfigGuideUtils.writeConfig( contextManager, storedConfiguration );
    }

    static void writeConfig(
            final ContextManager contextManager,
            final StoredConfigurationImpl storedConfiguration
    ) throws PwmOperationalException, PwmUnrecoverableException
    {
        final ConfigurationReader configReader = contextManager.getConfigReader();
        final PwmApplication pwmApplication = contextManager.getPwmApplication();

        try
        {
            // add a random security key
            storedConfiguration.initNewRandomSecurityKey();

            configReader.saveConfiguration( storedConfiguration, pwmApplication, null );

            contextManager.requestPwmApplicationRestart();
        }
        catch ( PwmException e )
        {
            throw new PwmOperationalException( e.getErrorInformation() );
        }
        catch ( Exception e )
        {
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.ERROR_INVALID_CONFIG, "unable to save configuration: " + e.getLocalizedMessage() );
            throw new PwmOperationalException( errorInformation );
        }
    }

    public static SchemaOperationResult extendSchema(
            final PwmApplication pwmApplication,
            final ConfigGuideBean configGuideBean,
            final boolean doSchemaExtension
    )
    {
        final Map<ConfigGuideFormField, String> form = configGuideBean.getFormData();
        final boolean ldapServerSecure = "true".equalsIgnoreCase( form.get( ConfigGuideFormField.PARAM_LDAP_SECURE ) );
        final String ldapUrl = "ldap" + ( ldapServerSecure ? "s" : "" )
                + "://"
                + form.get( ConfigGuideFormField.PARAM_LDAP_HOST )
                + ":"
                + form.get( ConfigGuideFormField.PARAM_LDAP_PORT );
        try
        {
            final ChaiConfiguration chaiConfiguration = ChaiConfiguration.builder(
                    ldapUrl,
                    form.get( ConfigGuideFormField.PARAM_LDAP_PROXY_DN ),
                    form.get( ConfigGuideFormField.PARAM_LDAP_PROXY_PW )
            )
                    .setSetting( ChaiSetting.PROMISCUOUS_SSL, "true" )
                    .build();

            final ChaiProvider chaiProvider = pwmApplication.getLdapConnectionService().getChaiProviderFactory().newProvider( chaiConfiguration );
            if ( doSchemaExtension )
            {
                return SchemaManager.extendSchema( chaiProvider );
            }
            else
            {
                return SchemaManager.checkExistingSchema( chaiProvider );
            }
        }
        catch ( Exception e )
        {
            LOGGER.error( "unable to create schema extender object: " + e.getMessage() );
            return null;
        }
    }

    static void forwardToJSP(
            final PwmRequest pwmRequest
    )
            throws IOException, ServletException, PwmUnrecoverableException
    {
        final ConfigGuideBean configGuideBean = pwmRequest.getPwmApplication().getSessionStateService().getBean( pwmRequest, ConfigGuideBean.class );

        if ( configGuideBean.getStep() == GuideStep.LDAP_PERMISSIONS )
        {
            final LDAPPermissionCalculator ldapPermissionCalculator = new LDAPPermissionCalculator( ConfigGuideForm.generateStoredConfig( configGuideBean ) );
            pwmRequest.setAttribute( PwmRequestAttribute.LdapPermissionItems, ldapPermissionCalculator );
        }

        final HttpServletRequest req = pwmRequest.getHttpServletRequest();
        final ServletContext servletContext = req.getSession().getServletContext();
        String destURL = '/' + PwmConstants.URL_JSP_CONFIG_GUIDE;
        destURL = destURL.replace( "%1%", configGuideBean.getStep().toString().toLowerCase() );
        servletContext.getRequestDispatcher( destURL ).forward( req, pwmRequest.getPwmResponse().getHttpServletResponse() );
    }

    public static Percent stepProgress( final GuideStep step )
    {
        final int ordinal = step.ordinal();
        final int total = GuideStep.values().length - 2;
        return new Percent( ordinal, total );
    }

    static void checkLdapServer( final ConfigGuideBean configGuideBean )
            throws PwmOperationalException, IOException, PwmUnrecoverableException
    {
        final Map<ConfigGuideFormField, String> formData = configGuideBean.getFormData();
        final String host = formData.get( ConfigGuideFormField.PARAM_LDAP_HOST );
        final int port = Integer.parseInt( formData.get( ConfigGuideFormField.PARAM_LDAP_PORT ) );

        {
            // socket test
            final InetAddress inetAddress = InetAddress.getByName( host );
            final SocketAddress socketAddress = new InetSocketAddress( inetAddress, port );
            final Socket socket = new Socket();

            final int timeout = 2000;
            socket.connect( socketAddress, timeout );
        }

        if ( Boolean.parseBoolean( formData.get( ConfigGuideFormField.PARAM_LDAP_SECURE ) ) )
        {
            X509Utils.readRemoteCertificates( host, port );
        }
    }


    public static void restUploadConfig( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
        final PwmSession pwmSession = pwmRequest.getPwmSession();
        final HttpServletRequest req = pwmRequest.getHttpServletRequest();

        if ( pwmApplication.getApplicationMode() == PwmApplicationMode.RUNNING )
        {
            final String errorMsg = "config upload is not permitted when in running mode";
            final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_UPLOAD_FAILURE, errorMsg, new String[]
                    {
                            errorMsg,
                    }
            );
            pwmRequest.respondWithError( errorInformation, true );
        }

        if ( ServletFileUpload.isMultipartContent( req ) )
        {
            final InputStream uploadedFile = pwmRequest.readFileUploadStream( PwmConstants.PARAM_FILE_UPLOAD );
            if ( uploadedFile != null )
            {
                try
                {
                    final StoredConfigurationImpl storedConfig = StoredConfigurationImpl.fromXml( uploadedFile );
                    final List<String> configErrors = storedConfig.validateValues();
                    if ( configErrors != null && !configErrors.isEmpty() )
                    {
                        throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, configErrors.get( 0 ) ) );
                    }
                    ConfigGuideUtils.writeConfig( ContextManager.getContextManager( req.getSession() ), storedConfig );
                    LOGGER.trace( pwmSession, "read config from file: " + storedConfig.toString() );
                    final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
                    pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
                    req.getSession().invalidate();
                }
                catch ( PwmException e )
                {
                    final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
                    pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
                    LOGGER.error( pwmSession, e.getErrorInformation().toDebugStr() );
                }
            }
            else
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_UPLOAD_FAILURE, "error reading config file: no file present in upload" );
                final RestResultBean restResultBean = RestResultBean.fromError( errorInformation, pwmRequest );
                pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
                LOGGER.error( pwmSession, errorInformation.toDebugStr() );
            }
        }
    }

}
