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

package password.pwm.http.servlet.configmanager;

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.config.DomainConfig;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.stored.StoredConfigKey;
import password.pwm.config.stored.StoredConfiguration;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestAttribute;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.util.java.CollectionUtil;
import password.pwm.util.java.JavaHelper;
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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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

        @Override
        public Collection<HttpMethod> permittedMethods( )
        {
            return Collections.singletonList( method );
        }
    }

    @Override
    protected Optional<ConfigManagerCertificateAction> readProcessAction( final PwmRequest request )
            throws PwmUnrecoverableException
    {
        return JavaHelper.readEnumFromString( ConfigManagerCertificateAction.class, request.readParameterAsString( PwmConstants.PARAM_ACTION_REQUEST ) );
    }

    @Override
    protected void processAction( final PwmRequest pwmRequest )
            throws ServletException, IOException, PwmUnrecoverableException
    {
        ConfigManagerServlet.verifyConfigAccess( pwmRequest );

        final Optional<ConfigManagerCertificateAction> action = readProcessAction( pwmRequest );
        final ArrayList<CertificateDebugDataItem> certificateDebugDataItems = new ArrayList<>( makeCertificateDebugData( pwmRequest.getDomainConfig() ) );

        if ( action.isPresent() && action.get() == ConfigManagerCertificateAction.certificateData )
        {
            final RestResultBean restResultBean = RestResultBean.withData( certificateDebugDataItems );
            pwmRequest.outputJsonResult( restResultBean );
            return;
        }
        pwmRequest.setAttribute( PwmRequestAttribute.ConfigHasCertificates, !certificateDebugDataItems.isEmpty() );
        pwmRequest.forwardToJsp( JspUrl.CONFIG_MANAGER_CERTIFICATES );
    }

    List<CertificateDebugDataItem> makeCertificateDebugData( final DomainConfig domainConfig ) throws PwmUnrecoverableException
    {
        final StoredConfiguration storedConfiguration = domainConfig.getStoredConfiguration();
        final List<StoredConfigKey> modifiedSettings = CollectionUtil.iteratorToStream( storedConfiguration.keys() )
                .filter( keys -> keys.isRecordType( StoredConfigKey.RecordType.SETTING ) )
                .collect( Collectors.toUnmodifiableList() );

        final List<CertificateDebugDataItem> certificateDebugDataItems = new ArrayList<>();

        for ( final StoredConfigKey key : modifiedSettings )
        {
            final PwmSetting pwmSetting = key.toPwmSetting();
            if ( pwmSetting.getSyntax() == PwmSettingSyntax.X509CERT )
            {
                final StoredValue storedValue = storedConfiguration.readStoredValue( key ).orElseThrow();
                final List<X509Certificate> certificates = ValueTypeConverter.valueToX509Certificates( pwmSetting, storedValue );
                certificateDebugDataItems.addAll( makeItems( pwmSetting, key.getProfileID(), certificates ) );
            }
            else if ( pwmSetting.getSyntax() == PwmSettingSyntax.ACTION )
            {
                final StoredValue storedValue = storedConfiguration.readStoredValue( key ).orElseThrow();
                final List<ActionConfiguration> actionConfigurations = ValueTypeConverter.valueToAction( pwmSetting, storedValue );
                for ( final ActionConfiguration actionConfiguration : actionConfigurations )
                {
                    for ( final ActionConfiguration.WebAction webAction : actionConfiguration.getWebActions() )
                    {
                        final List<X509Certificate> certificates = webAction.getCertificates();
                        certificateDebugDataItems.addAll( makeItems( pwmSetting, key.getProfileID(), certificates ) );
                    }
                }
            }
        }

        certificateDebugDataItems.sort( CertificateDebugDataItem.getExpirationComparator() );
        return Collections.unmodifiableList( certificateDebugDataItems );
    }

    Collection<CertificateDebugDataItem> makeItems(
            final PwmSetting setting,
            final String profileId,
            final List<X509Certificate> certificates
    )
            throws PwmUnrecoverableException
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
    public static class CertificateDebugDataItem implements Serializable
    {
        private String menuLocation;
        private String subject;
        private String serial;
        private String algorithm;
        private Instant expirationDate;
        private Instant issueDate;
        private String detail;

        private static final Comparator<CertificateDebugDataItem> EXPIRATION_COMPARATOR = Comparator.comparing(
                CertificateDebugDataItem::getExpirationDate,
                Comparator.nullsLast( Comparator.naturalOrder() )
        );

        public static Comparator<CertificateDebugDataItem> getExpirationComparator()
        {
            return EXPIRATION_COMPARATOR;
        }
    }
}
