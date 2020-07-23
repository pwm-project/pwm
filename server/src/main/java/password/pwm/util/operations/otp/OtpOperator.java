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

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package password.pwm.util.operations.otp;

import com.novell.ldapchai.ChaiUser;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;

/**
 * @author mpieters
 */
public interface OtpOperator
{

    OTPUserRecord readOtpUserConfiguration(
            UserIdentity theUser,
            String userGUID
    )
            throws PwmUnrecoverableException;

    void writeOtpUserConfiguration(
            PwmRequest pwmRequest,
            UserIdentity theUser,
            String userGuid,
            OTPUserRecord otpConfig
    )
            throws PwmUnrecoverableException;

    void clearOtpUserConfiguration(
            PwmRequest pwmRequest,
            UserIdentity theUser,
            ChaiUser chaiUser,
            String userGuid
    )
            throws PwmUnrecoverableException;

    void close( );
}
