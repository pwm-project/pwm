/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2012 The PWM Project
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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingSyntax;
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
        final List<HealthRecord> returnList = new ArrayList<HealthRecord>();
        for (final PwmSetting setting : PwmSetting.values()) {
            if (setting.getSyntax() == PwmSettingSyntax.X509CERT) {
                returnList.addAll(doHealthCheck(configuration,setting));
            }
        }
        return Collections.unmodifiableList(returnList);
    }

    private static List<HealthRecord> doHealthCheck(Configuration configuration, PwmSetting setting) {
        final X509Certificate[] certificates = configuration.readSettingAsCertificate(setting);
        if (certificates != null) {
            final List<HealthRecord> returnList = new ArrayList<HealthRecord>();
            for (final X509Certificate certificate : certificates) {
                returnList.addAll(doHealthCheck(certificate));
            }
            return returnList;
        }
        return Collections.emptyList();
    }

    public static List<HealthRecord> doHealthCheck(X509Certificate certificate) {
        try {
            checkCertificate(certificate);
            return Collections.emptyList();
        } catch (PwmOperationalException e) {
            final HealthRecord record = new HealthRecord(HealthStatus.WARN,"Certificates",e.getErrorInformation().toDebugStr());
            return Collections.singletonList(record);
        }
    }

    public static void checkCertificate(final X509Certificate certificate)
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
        if (durationUntilExpire.isShorterThan(PwmConstants.CERTIFICATE_WARN_PERIOD_MS)) {
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
