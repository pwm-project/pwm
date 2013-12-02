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
package password.pwm.util.operations;

import com.novell.ldapchai.exception.*;
import password.pwm.*;
import password.pwm.bean.UserIdentity;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.DataStorageMethod;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.ldap.LdapOperationsHelper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;
import password.pwm.util.operations.otp.AbstractOtpOperator;
import password.pwm.util.operations.otp.DbOtpOperator;
import password.pwm.util.operations.otp.LdapOtpOperator;
import password.pwm.util.operations.otp.LocalDbOtpOperator;
import password.pwm.util.operations.otp.OtpOperator;
import password.pwm.util.otp.OTPUserConfiguration;
import java.util.*;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class OtpService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(OtpService.class);

    private final Map<DataStorageMethod, OtpOperator> operatorMap = new EnumMap<DataStorageMethod, OtpOperator>(DataStorageMethod.class);
    private PwmApplication pwmApplication;

    public OtpService() {
    }

    @Override
    public STATUS status() {
        return STATUS.OPEN;
    }

    @Override
    public void init(PwmApplication pwmApplication) throws PwmException {
        this.pwmApplication = pwmApplication;
        operatorMap.put(DataStorageMethod.LDAP, new LdapOtpOperator(pwmApplication));
        operatorMap.put(DataStorageMethod.LOCALDB, new LocalDbOtpOperator(pwmApplication.getLocalDB(), pwmApplication.getConfig()));
        operatorMap.put(DataStorageMethod.DB, new DbOtpOperator(pwmApplication));
    }

    @Override
    public void close() {
        for (final OtpOperator operator : operatorMap.values()) {
            operator.close();
        }
        operatorMap.clear();
    }

    @Override
    public List<HealthRecord> healthCheck() {
        return Collections.emptyList();
    }

    public OTPUserConfiguration readOTPUserConfiguration(final UserIdentity theUser)
            throws PwmUnrecoverableException, ChaiUnavailableException {
        final Configuration config = pwmApplication.getConfig();
        final long methodStartTime = System.currentTimeMillis();
        OTPUserConfiguration otpConfig = null;

        final List<DataStorageMethod> otpSecretStorageLocations = config.getOtpSecretStorageLocations(PwmSetting.OTP_SECRET_READ_PREFERENCE);
        if (otpSecretStorageLocations != null) {
            final String userGUID;
            if (otpSecretStorageLocations.contains(DataStorageMethod.DB) || otpSecretStorageLocations.contains(
                    DataStorageMethod.LOCALDB)) {
                userGUID = LdapOperationsHelper.readLdapGuidValue(pwmApplication,theUser);
            } else {
                userGUID = null;
            }
            Iterator<DataStorageMethod> locationIterator = otpSecretStorageLocations.iterator();
            while (otpConfig == null && locationIterator.hasNext()) {
                final DataStorageMethod location = locationIterator.next();
                final OtpOperator operator = operatorMap.get(location);
                if (operator != null) {
                    otpConfig = operator.readOtpUserConfiguration(theUser, userGUID);
                } else {
                    LOGGER.warn(String.format("Storage location %s not implemented", location.toString()));
                }
            }
        }

        LOGGER.trace("readOTPUserConfiguration completed in " + TimeDuration.fromCurrent(methodStartTime).asCompactString());
        return otpConfig;
    }

    public void writeOTPUserConfiguration(final UserIdentity theUser, final String userGUID, final OTPUserConfiguration otp) throws PwmOperationalException, ChaiUnavailableException, ChaiValidationException {
        LOGGER.trace(String.format("Enter: writeOTPUserConfiguration(%s, %s, %s)", theUser, userGUID, otp));

        int attempts = 0, successes = 0;
        final Configuration config = pwmApplication.getConfig();
        final List<DataStorageMethod> otpSecretStorageLocations = config.getOtpSecretStorageLocations(PwmSetting.OTP_SECRET_READ_PREFERENCE);
        if (otpSecretStorageLocations != null) {
            for (DataStorageMethod otpSecretStorageLocation : otpSecretStorageLocations) {
                attempts++;
                final OtpOperator operator = operatorMap.get(otpSecretStorageLocation);
                if (operator != null) {
                    try {
                        operator.writeOtpUserConfiguration(theUser, userGUID, otp);
                        successes++;
                    } catch (PwmUnrecoverableException ex) {
                        LOGGER.error(ex.getMessage(), ex);
                    }
                } else {
                    LOGGER.warn(String.format("Storage location %s not implemented", otpSecretStorageLocation.toString()));
                }
            }
        }

        if (attempts == 0) {
            final String errorMsg = "no OTP secret save methods are available or configured";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        if (attempts != successes) { // should be impossible to get here, but just in case.
            final String errorMsg = "OTP secret storage only partially successful; attempts=" + attempts + ", successes=" + successes;
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }
    }

    public void clearOTPUserConfiguration(final UserIdentity theUser, final String userGUID) throws PwmOperationalException, ChaiUnavailableException {
        LOGGER.trace(String.format("Enter: clearOTPUserConfiguration(%s, %s)", theUser, userGUID));

        int attempts = 0, successes = 0;
        final Configuration config = pwmApplication.getConfig();
        final List<DataStorageMethod> otpSecretStorageLocations = config.getOtpSecretStorageLocations(PwmSetting.OTP_SECRET_READ_PREFERENCE);
        if (otpSecretStorageLocations != null) {
            final Iterator<DataStorageMethod> locationIterator = otpSecretStorageLocations.iterator();
            while (locationIterator.hasNext()) {
                attempts++;
                final DataStorageMethod location = locationIterator.next();
                final OtpOperator operator = operatorMap.get(location);
                if (operator != null) {
                    try {
                        operator.clearOtpUserConfiguration(theUser, userGUID);
                        successes++;
                    } catch (PwmUnrecoverableException ex) {
                        LOGGER.error(ex.getMessage(), ex);
                    }
                } else {
                    LOGGER.warn(String.format("Storage location %s not implemented", location.toString()));
                }
            }
        }

        if (attempts == 0) {
            final String errorMsg = "no OTP secret clear methods are available or configured";
            /* TODO: replace error message */
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        if (attempts != successes) { // should be impossible to get here, but just in case.
            final String errorMsg = "OTP secret clearing only partially successful; attempts=" + attempts + ", successes=" + successes;
            /* TODO: replace error message */
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }
    }

    public boolean checkIfOtpSetupNeeded(
            final PwmSession pwmSession,
            final UserIdentity theUser,
            final OTPUserConfiguration otpConfig
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        OTPUserConfiguration otp = readOTPUserConfiguration(theUser);
        return (otp == null || otp.getSecret() == null);
    }

    public boolean supportsRecoveryCodes() {
        Configuration config = pwmApplication.getConfig();
        AbstractOtpOperator.StorageFormat format = AbstractOtpOperator.StorageFormat.valueOf(config.readSettingAsString(PwmSetting.OTP_SECRET_STORAGEFORMAT));
        return format.supportsRecoveryCodes();
    }

    public ServiceInfo serviceInfo()
    {
        return new ServiceInfo(Collections.<DataStorageMethod>emptyList());
    }
}
