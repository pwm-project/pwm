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

package password.pwm.health;

import password.pwm.AppProperty;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
import password.pwm.config.profile.LdapProfile;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.util.TimeDuration;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class CertificateChecker implements HealthChecker {
    @Override
    public List<HealthRecord> doHealthCheck(PwmApplication pwmApplication) {
        return doHealthCheck(pwmApplication.getConfig());
    }

    private static List<HealthRecord> doHealthCheck(Configuration configuration) {
        final List<HealthRecord> returnList = new ArrayList<>();
        for (final PwmSetting setting : PwmSetting.values()) {
            if (setting.getSyntax() == PwmSettingSyntax.X509CERT) {
                if (setting != PwmSetting.LDAP_SERVER_CERTS) {
                    final X509Certificate[] certs = configuration.readSettingAsCertificate(setting);
                    returnList.addAll(doHealthCheck(configuration,setting,null,certs));
                }
            }
        }
        for (final LdapProfile ldapProfile : configuration.getLdapProfiles().values()) {
           final X509Certificate[] certificates = configuration.getLdapProfiles().get(ldapProfile.getIdentifier()).readSettingAsCertificate(PwmSetting.LDAP_SERVER_CERTS);
            returnList.addAll(doHealthCheck(configuration,PwmSetting.LDAP_SERVER_CERTS,ldapProfile.getIdentifier(),certificates));
        }
        return Collections.unmodifiableList(returnList);
    }

    private static List<HealthRecord> doHealthCheck(Configuration configuration, PwmSetting setting, final String profileID, X509Certificate[] certificates) {
        final long warnDurationMs = 1000 * Long.parseLong(configuration.readAppProperty(AppProperty.HEALTH_CERTIFICATE_WARN_SECONDS));

        if (certificates != null) {
            final List<HealthRecord> returnList = new ArrayList<>();
            for (final X509Certificate certificate : certificates) {
                try {
                    checkCertificate(certificate, warnDurationMs);
                    return Collections.emptyList();
                } catch (PwmOperationalException e) {
                    final String errorDetail = e.getErrorInformation().getDetailedErrorMsg();
                    final HealthRecord record = HealthRecord.forMessage(HealthMessage.Config_Certificate,
                            setting.toMenuLocationDebug(profileID,PwmConstants.DEFAULT_LOCALE),
                            errorDetail
                    );
                    return Collections.singletonList(record);
                }
            }
            return returnList;
        }
        return Collections.emptyList();
    }

    public static void checkCertificate(final X509Certificate certificate, final long warnDurationMs)
            throws PwmOperationalException
    {
        if (certificate == null) {
            return;
        }

        try {
            certificate.checkValidity();
        } catch (CertificateException e) {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("certificate for subject ");
            errorMsg.append(certificate.getSubjectDN().getName());
            errorMsg.append(" is not valid: ");
            errorMsg.append(e.getMessage());
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CERTIFICATE_ERROR, errorMsg.toString(), new String[]{errorMsg.toString()});
            throw new PwmOperationalException(errorInformation);
        }

        final Date expireDate = certificate.getNotAfter();
        final TimeDuration durationUntilExpire = TimeDuration.fromCurrent(expireDate);
        if (durationUntilExpire.isShorterThan(warnDurationMs)) {
            final StringBuilder errorMsg = new StringBuilder();
            errorMsg.append("certificate for subject ");
            errorMsg.append(certificate.getSubjectDN().getName());
            errorMsg.append(" will expire on: ");
            errorMsg.append(PwmConstants.DEFAULT_DATETIME_FORMAT.format(expireDate));
            errorMsg.append(" (").append(durationUntilExpire.asCompactString()).append(" from now)");
            final ErrorInformation errorInformation = new ErrorInformation(PwmError.ERROR_CERTIFICATE_ERROR, errorMsg.toString(), new String[]{errorMsg.toString()});
            throw new PwmOperationalException(errorInformation);
        }
    }
}
