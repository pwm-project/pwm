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

package password.pwm.http.servlet.configmanager;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import lombok.Builder;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigItemKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.secure.X509Utils;
import password.pwm.ws.server.RestResultBean;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.io.Serializable;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@WebServlet(
        name = "ConfigManagerCertificateServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/manager/certificates",
        }
)
public class ConfigManagerCertificatesServlet extends AbstractPwmServlet
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( ConfigManagerCertificatesServlet.class );

    public enum ConfigManagerCertificateAction implements AbstractPwmServlet.ProcessAction
    {
        certificateData( HttpMethod.GET ),;

        private final HttpMethod method;

        ConfigManagerCertificateAction( final HttpMethod method )
        {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    protected ConfigManagerCertificateAction readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        try
        {
            return ConfigManagerCertificateAction.valueOf( request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
        }
        catch ( final IllegalArgumentException e )
        {
            return null;
        }
    }

    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        ConfigManagerServlet.verifyConfigAccess( pwmRequest );

        final ConfigManagerCertificateAction action = readProcessAction( pwmRequest );
        final ArrayList<CertificateDebugDataItem> certificateDebugDataItems = new ArrayList<>( makeCertificateDebugData( pwmRequest.getConfig() ) );

        if ( action != null && action == ConfigManagerCertificateAction.certificateData )
        {
            final RestResultBean restResultBean = RestResultBean.withData( certificateDebugDataItems );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }
        pwmRequest.setAttribute( PwmRequestAttribute.ConfigHasCertificates, !certificateDebugDataItems.isEmpty() );
        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_CERTIFICATES );
    }

    List<CertificateDebugDataItem> makeCertificateDebugData( final Configuration configuration ) throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = configuration.getStoredConfiguration();
        final Set<StoredConfigItemKey> modifiedSettings = storedConfiguration.modifiedItems();

        final List<CertificateDebugDataItem> certificateDebugDataItems = new ArrayList<>();

        for ( final StoredConfigItemKey ref : modifiedSettings )
        {
            if ( ref.getRecordType() == StoredConfigItemKey.RecordType.SETTING )
            {
                final PwmSetting pwmSetting = PwmSetting.forKey( ref.getRecordID() );
                if ( pwmSetting.getSyntax() == PwmSettingSyntax.X509CERT )
                {
                    final StoredValue storedValue;
                    if ( pwmSetting.getCategory().hasProfiles() )
                    {
                        storedValue = storedConfiguration.readSetting( pwmSetting, ref.getProfileID() );
                    }
                    else
                    {
                        storedValue = storedConfiguration.readSetting( pwmSetting, null );
                    }
                    final X509Certificate[] arrayCerts = ( X509Certificate[] ) storedValue.toNativeObject();
                    final List<X509Certificate> certificates = arrayCerts == null ? Collections.emptyList() : Arrays.asList( arrayCerts );
                    certificateDebugDataItems.addAll( makeItems( pwmSetting, ref.getProfileID(), certificates ) );
                }
                else if ( pwmSetting.getSyntax() == PwmSettingSyntax.ACTION )
                {
                    final StoredValue storedValue;
                    if ( pwmSetting.getCategory().hasProfiles() )
                    {
                        storedValue = storedConfiguration.readSetting( pwmSetting, ref.getProfileID() );
                    }
                    else
                    {
                        storedValue = storedConfiguration.readSetting( pwmSetting, null );
                    }
                    final List<ActionConfiguration> actionConfigurations = ( List ) storedValue.toNativeObject();
                    for ( final ActionConfiguration actionConfiguration : actionConfigurations )
                    {
                        for ( final ActionConfiguration.WebAction webAction : actionConfiguration.getWebActions() )
                        {
                            final List<X509Certificate> certificates = webAction.getCertificates();
                            certificateDebugDataItems.addAll( makeItems( pwmSetting, ref.getProfileID(), certificates ) );
                        }
                    }
                }
            }
        }

        Collections.sort( certificateDebugDataItems );
        return Collections.unmodifiableList( certificateDebugDataItems );
    }

    Collection<CertificateDebugDataItem> makeItems(
            final PwmSetting setting,
            final String profileId,
            final List<X509Certificate> certificates
    ) throws PwmUnrecoverableException
    {
        if ( certificates == null )
        {
            return Collections.emptyList();
        }

        final List<CertificateDebugDataItem> certificateDebugDataItems = new ArrayList<>();
        for ( final X509Certificate certificate : certificates )
        {
            final CertificateDebugDataItem certificateDebugDataItem = makeItem( setting, profileId, certificate );
            certificateDebugDataItems.add( certificateDebugDataItem );
        }
        return certificateDebugDataItems;
    }

    CertificateDebugDataItem makeItem(
            final PwmSetting setting,
            final String profileId,
            final X509Certificate certificate
    )
            throws PwmUnrecoverableException
    {
        final CertificateDebugDataItem.CertificateDebugDataItemBuilder builder = CertificateDebugDataItem.builder();
        builder.menuLocation( setting.toMenuLocationDebug( profileId, PwmConstants.DEFAULT_LOCALE ) );
        builder.subject( certificate.getSubjectDN().toString() );
        builder.serial( certificate.getSerialNumber().toString() );
        builder.algorithm( certificate.getSigAlgName() );
        builder.issueDate( certificate.getNotBefore().toInstant() );
        builder.expirationDate( certificate.getNotAfter().toInstant() );
        try
        {
            builder.detail( X509Utils.makeDetailText( certificate ) );
        }
        catch ( final CertificateEncodingException e )
        {
            LOGGER.error( () -> "unexpected error parsing certificate detail text: " + e.getMessage() );
        }
        return builder.build();
    }

    @Value
    @Builder
    public static class CertificateDebugDataItem implements Serializable, Comparable
    {
        private String menuLocation;
        private String subject;
        private String serial;
        private String algorithm;
        private Instant expirationDate;
        private Instant issueDate;
        private String detail;

        @Override
        public int compareTo( final Object o )
        {
            if ( this == o || this.equals( o ) )
            {
                return 0;
            }

            return expirationDate.compareTo( ( ( CertificateDebugDataItem ) o ).getExpirationDate() );
        }
    }
}
