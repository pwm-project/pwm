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

import com.novell.ldapchai.ChaiUser;
import com.novell.ldapchai.exception.*;
import password.pwm.*;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.config.option.CrStorageMethod;
import password.pwm.error.*;
import password.pwm.health.HealthRecord;
import password.pwm.util.Helper;
import password.pwm.util.PwmLogger;
import password.pwm.util.TimeDuration;

import java.util.*;

import password.pwm.util.operations.otp.LdapOtpOperator;
import password.pwm.util.operations.otp.LocalDbOtpOperator;
import password.pwm.util.operations.otp.OtpOperator;
import password.pwm.util.otp.OTPUserConfiguration;

/**
 * @author Menno Pieters, Jason D. Rivard
 */
public class OtpService implements PwmService {

    private static final PwmLogger LOGGER = PwmLogger.getLogger(OtpService.class);

    private final Map<CrStorageMethod, OtpOperator> operatorMap = new EnumMap<CrStorageMethod, OtpOperator>(CrStorageMethod.class);
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
        //operatorMap.put(Configuration.STORAGE_METHOD.DB, new DbCrOperator(pwmApplication));
        operatorMap.put(CrStorageMethod.LDAP, new LdapOtpOperator(pwmApplication.getConfig()));
        operatorMap.put(CrStorageMethod.LOCALDB, new LocalDbOtpOperator(pwmApplication.getLocalDB(), pwmApplication.getConfig()));
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

    public OTPUserConfiguration readOTPUserConfiguration(final ChaiUser theUser)
            throws PwmUnrecoverableException, ChaiUnavailableException {
        final Configuration config = pwmApplication.getConfig();
        final long methodStartTime = System.currentTimeMillis();
        OTPUserConfiguration otpConfig = null;

        final List<CrStorageMethod> otpSecretStorageLocations = config.getOtpSecretStorageLocations(PwmSetting.OTP_SECRET_READ_PREFERENCE);
        if (otpSecretStorageLocations != null) {
            final String userGUID;
            if (otpSecretStorageLocations.contains(CrStorageMethod.DB) || otpSecretStorageLocations.contains(CrStorageMethod.LOCALDB)) {
                userGUID = Helper.readLdapGuidValue(pwmApplication, theUser.getEntryDN());
            } else {
                userGUID = null;
            }
            Iterator<CrStorageMethod> locationIterator = otpSecretStorageLocations.iterator();
            while (otpConfig == null && locationIterator.hasNext()) {
                final CrStorageMethod location = locationIterator.next();
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

    public void writeOTPUserConfiguration(final ChaiUser theUser, final String userGUID, final OTPUserConfiguration otp) throws PwmOperationalException, ChaiUnavailableException, ChaiValidationException {
        LOGGER.trace(String.format("Enter: writeOTPUserConfiguration(%s, %s, %s)", theUser, userGUID, otp));

        int attempts = 0, successes = 0;
        final Configuration config = pwmApplication.getConfig();
        final List<CrStorageMethod> otpSecretStorageLocations = config.getOtpSecretStorageLocations(PwmSetting.OTP_SECRET_READ_PREFERENCE);
        if (otpSecretStorageLocations != null) {
            final Iterator<CrStorageMethod> locationIterator = otpSecretStorageLocations.iterator();
            while (locationIterator.hasNext()) {
                attempts++;
                final CrStorageMethod location = locationIterator.next();
                final OtpOperator operator = operatorMap.get(location);
                if (operator != null) {
                    try {
                        operator.writeOtpUserConfiguration(theUser, userGUID, otp);
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
            final String errorMsg = "no OTP secreat save methods are available or configured";
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }

        if (attempts != successes) { // should be impossible to get here, but just in case.
            final String errorMsg = "OTP secret storage only partially successful; attempts=" + attempts + ", successes=" + successes;
            final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_WRITING_OTP_SECRET, errorMsg);
            throw new PwmOperationalException(errorInfo);
        }
    }

    public void clearOTPUserConfiguration(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final String userGUID
    )
            throws PwmOperationalException, ChaiUnavailableException {
        final Configuration config = pwmApplication.getConfig();
        int attempts = 0, successes = 0;

        /*

         LOGGER.trace(pwmSession, "beginning clear response operation for user " + theUser.getEntryDN() + " guid=" + userGUID);

         final List<Configuration.STORAGE_METHOD> writeMethods = config.getResponseStorageLocations(PwmSetting.FORGOTTEN_PASSWORD_WRITE_PREFERENCE);
         if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.EDIRECTORY_STORE_NMAS_RESPONSES)) {
         writeMethods.add(Configuration.STORAGE_METHOD.NMAS);
         }

         for (final Configuration.STORAGE_METHOD loopWriteMethod : writeMethods) {
         try {
         attempts++;
         operatorMap.get(loopWriteMethod).clearResponses(theUser, userGUID);
         successes++;
         } catch (PwmUnrecoverableException e) {
         LOGGER.error(pwmSession, "error clearing responses via " + loopWriteMethod + ", error: " + e.getMessage());
         }
         }

         if (attempts == 0) {
         final String errorMsg = "no response save methods are available or configured";
         final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, errorMsg);
         throw new PwmOperationalException(errorInfo);
         }

         if (attempts != successes) { // should be impossible to get here, but just in case.
         final String errorMsg = "response clear partially successful; attempts=" + attempts + ", successes=" + successes;
         final ErrorInformation errorInfo = new ErrorInformation(PwmError.ERROR_CLEARING_RESPONSES, errorMsg);
         throw new PwmOperationalException(errorInfo);
         }
         */
    }

    public boolean checkIfOtpSetupNeeded(
            final PwmSession pwmSession,
            final ChaiUser theUser,
            final OTPUserConfiguration otpConfig
    )
            throws ChaiUnavailableException, PwmUnrecoverableException {
        /* TODO */

        /*
         LOGGER.trace(pwmSession, "beginning check to determine if responses need to be configured for user");

         final String userDN = theUser.getEntryDN();

         final ChaiProvider provider = pwmApplication.getProxyChaiProvider();
         final Configuration config = pwmApplication.getConfig();

         if (!Helper.testUserMatchQueryString(provider, userDN, config.readSettingAsString(PwmSetting.QUERY_MATCH_CHECK_RESPONSES))) {
         LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " is not eligible for checkIfResponseConfigNeeded due to query match");
         return false;
         }

         // check to be sure there are actually challenges in the challenge set
         if (challengeSet == null || challengeSet.getChallenges().isEmpty()) {
         LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: no challenge sets configured for user " + userDN);
         return false;
         }

         try {
         // check if responses exist
         if (responseInfoBean == null) {
         throw new Exception("no responses configured");
         }

         // check if responses meet the challenge set policy for the user
         //usersResponses.meetsChallengeSetRequirements(challengeSet);
         LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " has good responses");
         return false;
         } catch (Exception e) {
         LOGGER.debug(pwmSession, "checkIfResponseConfigNeeded: " + userDN + " does not have good responses: " + e.getMessage());
         return true;
         }
         */
        return true;
    }

}
