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

package password.pwm.svc.event;

import password.pwm.PwmApplication;
import password.pwm.PwmDomain;
import password.pwm.bean.SessionLabel;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.user.UserInfo;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;

import java.util.Objects;

public class AuditServiceClient
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AuditServiceClient.class );

    public static void submit( final PwmApplication pwmApplication, final SessionLabel sessionLabel, final AuditRecord auditRecord )
    {
        Objects.requireNonNull( pwmApplication );
        Objects.requireNonNull( auditRecord );

        final AuditService auditService = pwmApplication.getAuditService();

        if ( auditService != null )
        {
            try
            {
                auditService.submit( sessionLabel, auditRecord );
            }
            catch ( final PwmUnrecoverableException e )
            {
                LOGGER.error( sessionLabel, () -> "unexpected error submitting audit event: '"
                        + JsonFactory.get().serialize( auditRecord ) + "' , error: " + e.getMessage(), e );
            }
        }
    }

    public static void submitUserEvent( final PwmRequest pwmRequest, final AuditEvent auditEvent, final UserInfo userInfo )
            throws PwmUnrecoverableException
    {
        final PwmDomain pwmDomain = pwmRequest.getPwmApplication().domains().get( userInfo.getUserIdentity().getDomainID() );
        final MacroRequest macroRequest =  pwmRequest.getMacroMachine();
        final AuditRecordFactory auditRecordFactory = AuditRecordFactory.make( pwmRequest.getLabel(), pwmDomain, macroRequest );
        final UserAuditRecord auditRecord = auditRecordFactory.createUserAuditRecord( auditEvent, userInfo, pwmRequest.getPwmSession() );
        submit( pwmRequest, auditRecord );
    }


    public static void submit( final PwmRequest pwmRequest, final AuditRecord auditRecord )
    {
        Objects.requireNonNull( pwmRequest );
        submit( pwmRequest.getPwmApplication(), pwmRequest.getLabel(), auditRecord );
    }

    public static void submitSystemEvent( final PwmApplication pwmApplication, final SessionLabel sessionLabel, final AuditEvent auditEvent )
    {
        submitSystemEvent( pwmApplication, sessionLabel, auditEvent, null );
    }

    public static void submitSystemEvent( final PwmApplication pwmApplication, final SessionLabel sessionLabel, final AuditEvent auditEvent, final String message )
    {
        final SystemAuditRecord auditRecord = AuditRecordFactory.make( sessionLabel, pwmApplication )
                .createSystemAuditRecord( auditEvent, message );
        submit( pwmApplication, sessionLabel, auditRecord );
    }


}
