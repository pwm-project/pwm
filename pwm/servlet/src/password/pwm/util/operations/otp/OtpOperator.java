/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package password.pwm.util.operations.otp;

import com.novell.ldapchai.ChaiUser;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.otp.OTPUserConfiguration;

/**
 *
 * @author mpieters
 */
public interface OtpOperator {
    
    public OTPUserConfiguration readOtpUserConfiguration(final UserIdentity theUser, final String userGUID)
            throws PwmUnrecoverableException;

    public void writeOtpUserConfiguration(final UserIdentity theUser, final String userGuid, final OTPUserConfiguration otpConfig)
            throws PwmUnrecoverableException;

    public void clearOtpUserConfiguration(final UserIdentity theUser, final String userGuid)
            throws PwmUnrecoverableException;

    public void close();
}
