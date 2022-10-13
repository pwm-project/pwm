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

package password.pwm.svc.report;

import password.pwm.bean.UserIdentity;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.user.UserInfo;
import password.pwm.util.java.TimeDuration;
import password.pwm.util.logging.PwmLogLevel;

import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

public class UserReportRecordReaderTask implements Callable<UserReportRecord>
{
    private final ReportProcess reportProcess;
    private final UserIdentity userIdentity;

    public UserReportRecordReaderTask( final ReportProcess reportProcess, final UserIdentity userIdentity )
    {
        this.reportProcess = reportProcess;
        this.userIdentity = userIdentity;
    }

    @Override
    public UserReportRecord call()
            throws PwmUnrecoverableException
    {
        if ( reportProcess.isCancelled() )
        {
            throw new CancellationException( "report process job cancelled" );
        }

        try
        {
            return readUserReportRecord( userIdentity );
        }
        catch ( final Exception e )
        {
            final String msg = "error while reading report record for user " + userIdentity.toDisplayString() + ": error " + e.getMessage();
            throw new PwmUnrecoverableException( new ErrorInformation( PwmError.ERROR_LDAP_DATA_ERROR, msg ), e );
        }
    }

    private UserReportRecord readUserReportRecord(
            final UserIdentity userIdentity
    )
            throws PwmUnrecoverableException
    {
        final Instant startTime = Instant.now();
        final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxyForOfflineUser(
                reportProcess.getPwmDomain().getPwmApplication(),
                reportProcess.getSessionLabel(),
                userIdentity );

        final UserReportRecord record = UserReportRecord.fromUserInfo( userInfo );

        reportProcess.log( PwmLogLevel.TRACE, () -> "completed output of user " + userIdentity.toDisplayString(),
                TimeDuration.fromCurrent( startTime ) );

        return record;
    }
}
