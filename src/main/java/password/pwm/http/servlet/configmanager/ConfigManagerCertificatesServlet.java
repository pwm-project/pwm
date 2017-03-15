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

package password.pwm.http.servlet.configmanager;

import com.novell.ldapchai.exception.ChaiUnavailableException;
import password.pwm.PwmConstants;
import password.pwm.config.ActionConfiguration;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigReference;
import password.pwm.config.stored.StoredConfigurationImpl;
import password.pwm.config.stored.StoredConfigurationUtil;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.HttpMethod;
import password.pwm.http.JspUrl;
import password.pwm.http.PwmRequest;
import password.pwm.http.servlet.AbstractPwmServlet;
import password.pwm.util.secure.X509Utils;
import password.pwm.util.logging.PwmLogger;
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
import java.util.List;

@WebServlet(
        name = "ConfigManagerCertificateServlet",
        urlPatterns = {
                PwmConstants.URL_PREFIX_PRIVATE + "/config/manager/certificates",
        }
)
public class ConfigManagerCertificatesServlet extends AbstractPwmServlet {
    private static final PwmLogger LOGGER = PwmLogger.forClass(ConfigManagerCertificatesServlet.class);

    public enum ConfigManagerCertificateAction implements AbstractPwmServlet.ProcessAction {
        certificateData(HttpMethod.GET),

        ;

        private final HttpMethod method;

        ConfigManagerCertificateAction(final HttpMethod method) {
            this.method = method;
        }

        public Collection<HttpMethod> permittedMethods() {
            return Collections.singletonList(method);
        }
    }

    protected ConfigManagerCertificateAction readProcessAction(final PwmRequest request)
            throws PwmUnrecoverableException {
        try {
            return ConfigManagerCertificateAction.valueOf(request.readParameterAsString(PwmConstants.PARAM_ACTION_REQUEST));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    protected void processAction(final PwmRequest pwmRequest)
            throws ServletException, IOException, ChaiUnavailableException, PwmUnrecoverableException
    {
        final ConfigManagerCertificateAction action = readProcessAction(pwmRequest);
        final ArrayList<CertificateDebugDataItem> certificateDebugDataItems = new ArrayList<>(makeCertificateDebugData(pwmRequest.getConfig()));

        if (action != null && action == ConfigManagerCertificateAction.certificateData) {
            final RestResultBean restResultBean = new RestResultBean(certificateDebugDataItems);
            pwmRequest.outputJsonResult(restResultBean);
            return;
        }
        pwmRequest.setAttribute(PwmRequest.Attribute.ConfigHasCertificates, !certificateDebugDataItems.isEmpty());
        pwmRequest.forwardToJsp(JspUrl.CONFIG_MANAGER_CERTIFICATES);
    }

    List<CertificateDebugDataItem> makeCertificateDebugData(final Configuration configuration) throws PwmUnrecoverableException {
        final StoredConfigurationImpl storedConfiguration = configuration.getStoredConfiguration();
        final List<StoredConfigReference> modifiedSettings = StoredConfigurationUtil.modifiedSettings(storedConfiguration);

        final List<CertificateDebugDataItem> certificateDebugDataItems = new ArrayList<>();

        for (final StoredConfigReference ref : modifiedSettings) {
            if (ref.getRecordType() == StoredConfigReference.RecordType.SETTING) {
                final PwmSetting pwmSetting = PwmSetting.forKey(ref.getRecordID());
                if (pwmSetting.getSyntax() == PwmSettingSyntax.X509CERT) {
                    final StoredValue storedValue;
                    if (pwmSetting.getCategory().hasProfiles()) {
                        storedValue = storedConfiguration.readSetting(pwmSetting, ref.getProfileID());
                    } else {
                        storedValue = storedConfiguration.readSetting(pwmSetting);
                    }
                    final X509Certificate[] certificates = (X509Certificate[])storedValue.toNativeObject();
                    certificateDebugDataItems.addAll(makeItems(pwmSetting, ref.getProfileID(), certificates));
                } else if (pwmSetting.getSyntax() == PwmSettingSyntax.ACTION) {
                    final StoredValue storedValue;
                    if (pwmSetting.getCategory().hasProfiles()) {
                        storedValue = storedConfiguration.readSetting(pwmSetting, ref.getProfileID());
                    } else {
                        storedValue = storedConfiguration.readSetting(pwmSetting);
                    }
                    final List<ActionConfiguration> actionConfigurations = (List)storedValue.toNativeObject();
                    for (final ActionConfiguration actionConfiguration : actionConfigurations) {
                        final X509Certificate[] certificates = actionConfiguration.getCertificates();
                        certificateDebugDataItems.addAll(makeItems(pwmSetting, ref.getProfileID(), certificates));
                    }
                }
            }
        }

        Collections.sort(certificateDebugDataItems);
        return Collections.unmodifiableList(certificateDebugDataItems);
    }

    Collection<CertificateDebugDataItem> makeItems(
            final PwmSetting setting,
            final String profileId,
            final X509Certificate[] certificates
    ) throws PwmUnrecoverableException {
        if (certificates == null) {
            return Collections.emptyList();
        }

        final List<CertificateDebugDataItem> certificateDebugDataItems = new ArrayList<>();
        for (final X509Certificate certificate : certificates) {
            final CertificateDebugDataItem certificateDebugDataItem = makeItem(setting, profileId, certificate);
            certificateDebugDataItems.add(certificateDebugDataItem);
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
        final CertificateDebugDataItem item = new CertificateDebugDataItem();
        item.setMenuLocation(setting.toMenuLocationDebug(profileId, PwmConstants.DEFAULT_LOCALE));
        item.setSubject(certificate.getSubjectDN().toString());
        item.setSerial(certificate.getSerialNumber().toString());
        item.setAlgorithm(certificate.getSigAlgName());
        item.setIssueDate(certificate.getNotBefore().toInstant());
        item.setExpirationDate(certificate.getNotAfter().toInstant());
        try {
            item.setDetail(X509Utils.makeDetailText(certificate));
        } catch (CertificateEncodingException e) {
            LOGGER.error("unexpected error parsing certificate detail text: " + e.getMessage());
        }
        return item;
    }

    public static class CertificateDebugDataItem implements Serializable, Comparable {
        private String menuLocation;
        private String subject;
        private String serial;
        private String algorithm;
        private Instant expirationDate;
        private Instant issueDate;
        private String detail;

        public String getMenuLocation() {
            return menuLocation;
        }

        public void setMenuLocation(final String menuLocation) {
            this.menuLocation = menuLocation;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(final String subject) {
            this.subject = subject;
        }

        public String getSerial() {
            return serial;
        }

        public void setSerial(final String serial) {
            this.serial = serial;
        }

        public String getAlgorithm() {
            return algorithm;
        }

        public void setAlgorithm(final String algorithm) {
            this.algorithm = algorithm;
        }

        public Instant getExpirationDate() {
            return expirationDate;
        }

        public void setExpirationDate(final Instant expirationDate) {
            this.expirationDate = expirationDate;
        }

        public Instant getIssueDate() {
            return issueDate;
        }

        public void setIssueDate(final Instant issueDate) {
            this.issueDate = issueDate;
        }

        public String getDetail() {
            return detail;
        }

        public void setDetail(final String detail) {
            this.detail = detail;
        }

        @Override
        public int compareTo(final Object o) {
            return expirationDate.compareTo(((CertificateDebugDataItem)o).getExpirationDate());
        }
    }
}
