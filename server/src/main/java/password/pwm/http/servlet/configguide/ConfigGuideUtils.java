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

package password.pwm.http.servlet.configguide;

import com.novell.ldapchai.provider.ChaiConfiguration;
import com.novell.ldapchai.provider.ChaiProvider;
import com.novell.ldapchai.provider.ChaiSetting;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import password.pwm.PwmApplication;
import password.pwm.PwmApplicationMode;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.stored.ConfigurationProperty;
import password.pwm.config.stored.ConfigurationReader;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.stored.StoredConfigurationFactory;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmException;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.ContextManager;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.bean.ConfigGuideBean;
import password.pwm.i18n.Message;
import password.pwm.ldap.schema.SchemaManager;
import password.pwm.ldap.schema.SchemaOperationResult;
import password.pwm.util.LDAPPermissionCalculator;
import password.pwm.util.java.JavaHelper;
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
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final StoredConfigurationModifier storedConfiguration = StoredConfigurationModifier.newModifier( ConfigGuideForm.generateStoredConfig( configGuideBean ) );
        final String configPassword = configGuideBean.getFormData().get( ConfigGuideFormField.PARAM_CONFIG_PASSWORD );
        if ( configPassword != null && configPassword.length() > 0 )
        {
            StoredConfigurationUtil.setPassword( storedConfiguration, configPassword );
        }
        else
        {
            storedConfiguration.writeConfigProperty( ConfigurationProperty.PASSWORD_HASH, null );
        }

        storedConfiguration.writeConfigProperty( ConfigurationProperty.CONFIG_IS_EDITABLE, "false" );
        ConfigGuideUtils.writeConfig( contextManager, storedConfiguration.newStoredConfiguration() );
    }

    static void writeConfig(
            final ContextManager contextManager,
            final StoredConfiguration storedConfiguration
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final ConfigurationReader configReader = contextManager.getConfigReader();
        final PwmApplication pwmApplication = contextManager.getPwmApplication();

        try
        {
            final StoredConfigurationModifier modifier = StoredConfigurationModifier.newModifier( storedConfiguration );
            // add a random security key
            StoredConfigurationUtil.initNewRandomSecurityKey( modifier );

            configReader.saveConfiguration( modifier.newStoredConfiguration(), pwmApplication, null );

            contextManager.requestPwmApplicationRestart();
        }
        catch ( final PwmException e )
        {
            throw new PwmOperationalException( e.getErrorInformation() );
        }
        catch ( final Exception e )
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
        catch ( final Exception e )
        {
            LOGGER.error( () -> "unable to create schema extender object: " + e.getMessage() );
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
            final Configuration tempConfig = new Configuration( ConfigGuideForm.generateStoredConfig( configGuideBean ) );
            X509Utils.readRemoteCertificates( host, port, tempConfig );
        }
    }


    public static void restUploadConfig( final PwmRequest pwmRequest )
            throws PwmUnrecoverableException, IOException, ServletException
    {
        final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
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
                    final StoredConfiguration storedConfig = StoredConfigurationFactory.fromXml( uploadedFile );
                    final List<String> configErrors = StoredConfigurationUtil.validateValues( storedConfig );
                    if ( !JavaHelper.isEmpty( configErrors ) )
                    {
                        throw new PwmOperationalException( new ErrorInformation( PwmError.CONFIG_FORMAT_ERROR, configErrors.get( 0 ) ) );
                    }
                    ConfigGuideUtils.writeConfig( ContextManager.getContextManager( req.getSession() ), storedConfig );
                    LOGGER.trace( pwmRequest, () -> "read config from file: " + storedConfig.toString() );
                    final RestResultBean restResultBean = RestResultBean.forSuccessMessage( pwmRequest, Message.Success_Unknown );
                    pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
                    req.getSession().invalidate();
                }
                catch ( final PwmException e )
                {
                    final RestResultBean restResultBean = RestResultBean.fromError( e.getErrorInformation(), pwmRequest );
                    pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
                    LOGGER.error( pwmRequest, () -> e.getErrorInformation().toDebugStr() );
                }
            }
            else
            {
                final ErrorInformation errorInformation = new ErrorInformation( PwmError.CONFIG_UPLOAD_FAILURE, "error reading config file: no file present in upload" );
                final RestResultBean restResultBean = RestResultBean.fromError( errorInformation, pwmRequest );
                pwmRequest.getPwmResponse().outputJsonResult( restResultBean );
                LOGGER.error( pwmRequest, () -> errorInformation.toDebugStr() );
            }
        }
    }

}
