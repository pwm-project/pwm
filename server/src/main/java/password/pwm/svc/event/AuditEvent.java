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

import password.pwm.config.SettingReader;
import password.pwm.i18n.Admin;
import password.pwm.i18n.Message;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.util.java.JsonUtil;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

public enum AuditEvent
{

    // system events
    STARTUP( Message.EventLog_Startup, Admin.EventLog_Narrative_Startup, AuditEventType.SYSTEM ),
    SHUTDOWN( Message.EventLog_Shutdown, Admin.EventLog_Narrative_Shutdown, AuditEventType.SYSTEM ),
    RESTART( Message.EventLog_Restart, Admin.EventLog_Narrative_Restart, AuditEventType.SYSTEM ),
    FATAL_EVENT( Message.EventLog_FatalEvent, Admin.EventLog_Narrative_FatalEvent, AuditEventType.SYSTEM ),
    INTRUDER_LOCK( Message.EventLog_IntruderLockout, Admin.EventLog_Narrative_IntruderLockout, AuditEventType.SYSTEM ),
    INTRUDER_ATTEMPT( Message.EventLog_IntruderAttempt, Admin.EventLog_Narrative_IntruderAttempt, AuditEventType.SYSTEM ),

    // user events not stored in user event history
    MODIFY_CONFIGURATION( Message.EventLog_ModifyConfiguration, Admin.EventLog_Narrative_ModifyConfiguration, AuditEventType.USER ),
    AUTHENTICATE( Message.EventLog_Authenticate, Admin.EventLog_Narrative_Authenticate, AuditEventType.USER ),
    AGREEMENT_PASSED( Message.EventLog_AgreementPassed, Admin.EventLog_Narrative_AgreementPassed, AuditEventType.USER ),
    TOKEN_ISSUED( Message.EventLog_TokenIssued, Admin.EventLog_Narrative_TokenIssued, AuditEventType.USER ),
    TOKEN_CLAIMED( Message.EventLog_TokenClaimed, Admin.EventLog_Narrative_TokenClaimed, AuditEventType.USER ),
    CLEAR_RESPONSES( Message.EventLog_ClearResponses, Admin.EventLog_Narrative_ClearResponses, AuditEventType.USER ),
    DELETE_ACCOUNT( Message.EventLog_DeleteAccount, Admin.EventLog_Narrative_DeleteAccount, AuditEventType.USER ),

    // user events stored in user event history
    CHANGE_PASSWORD( Message.EventLog_ChangePassword, Admin.EventLog_Narrative_ChangePassword, AuditEventType.USER ),
    UNLOCK_PASSWORD( Message.EventLog_UnlockPassword, Admin.EventLog_Narrative_UnlockPassword, AuditEventType.USER ),
    RECOVER_PASSWORD( Message.EventLog_RecoverPassword, Admin.EventLog_Narrative_RecoverPassword, AuditEventType.USER ),
    SET_RESPONSES( Message.EventLog_SetupResponses, Admin.EventLog_Narrative_SetupResponses, AuditEventType.USER ),
    SET_OTP_SECRET( Message.Eventlog_SetupOtpSecret, Admin.Eventlog_Narrative_SetupOtpSecret, AuditEventType.USER ),
    ACTIVATE_USER( Message.EventLog_ActivateUser, Admin.EventLog_Narrative_ActivateUser, AuditEventType.USER ),
    CREATE_USER( Message.EventLog_CreateUser, Admin.EventLog_Narrative_CreateUser, AuditEventType.USER ),
    UPDATE_PROFILE( Message.EventLog_UpdateProfile, Admin.EventLog_Narrative_UpdateProfile, AuditEventType.USER ),
    INTRUDER_USER_LOCK( Message.EventLog_IntruderUserLock, Admin.EventLog_Narrative_IntruderUserLock, AuditEventType.USER ),
    INTRUDER_USER_ATTEMPT( Message.EventLog_IntruderUserAttempt, Admin.EventLog_Narrative_IntruderUserAttempt, AuditEventType.USER ),

    // helpdesk events
    HELPDESK_SET_PASSWORD( Message.EventLog_HelpdeskSetPassword, Admin.EventLog_Narrative_HelpdeskSetPassword, AuditEventType.HELPDESK ),
    HELPDESK_UNLOCK_PASSWORD( Message.EventLog_HelpdeskUnlockPassword, Admin.EventLog_Narrative_HelpdeskUnlockPassword, AuditEventType.HELPDESK ),
    HELPDESK_CLEAR_RESPONSES( Message.EventLog_HelpdeskClearResponses, Admin.EventLog_Narrative_HelpdeskClearResponses, AuditEventType.HELPDESK ),
    HELPDESK_CLEAR_OTP_SECRET( Message.EventLog_HelpdeskClearOtpSecret, Admin.EventLog_Narrative_HelpdeskClearOtpSecret, AuditEventType.HELPDESK ),
    HELPDESK_ACTION( Message.EventLog_HelpdeskAction, Admin.EventLog_Narrative_HelpdeskAction, AuditEventType.HELPDESK ),
    HELPDESK_DELETE_USER( Message.EventLog_HelpdeskDeleteUser, Admin.EventLog_Narrative_HelpdeskDeleteUser, AuditEventType.HELPDESK ),
    HELPDESK_VIEW_DETAIL( Message.EventLog_HelpdeskViewDetail, Admin.EventLog_Narrative_HelpdeskViewDetail, AuditEventType.HELPDESK ),
    HELPDESK_VERIFY_OTP( Message.EventLog_HelpdeskVerifyOtp, Admin.EventLog_Narrative_HelpdeskVerifyOtp, AuditEventType.HELPDESK ),
    HELPDESK_VERIFY_OTP_INCORRECT( Message.EventLog_HelpdeskVerifyOtpIncorrect, Admin.EventLog_Narrative_HelpdeskVerifyOtpIncorrect, AuditEventType.HELPDESK ),
    HELPDESK_VERIFY_TOKEN( Message.EventLog_HelpdeskVerifyToken, Admin.EventLog_Narrative_HelpdeskVerifyToken, AuditEventType.HELPDESK ),
    HELPDESK_VERIFY_TOKEN_INCORRECT( Message.EventLog_HelpdeskVerifyTokenIncorrect, Admin.EventLog_Narrative_HelpdeskVerifyTokenIncorrect, AuditEventType.HELPDESK ),
    HELPDESK_VERIFY_ATTRIBUTES( Message.EventLog_HelpdeskVerifyAttributes, Admin.EventLog_Narrative_HelpdeskVerifyAttributes, AuditEventType.HELPDESK ),
    HELPDESK_VERIFY_ATTRIBUTES_INCORRECT(
            Message.EventLog_HelpdeskVerifyAttributesIncorrect, Admin.EventLog_Narrative_HelpdeskVerifyAttributesIncorrect, AuditEventType.HELPDESK ),;

    private static final String JSON_KEY_XDAS_TAXONOMY = "xdasTaxonomy";
    private static final String JSON_KEY_XDAS_OUTCOME = "xdasOutcome";


    private final Message message;
    private final PwmDisplayBundle narrative;

    private final String xdasTaxonomy;
    private final String xdasOutcome;

    private final AuditEventType type;

    AuditEvent( final Message message, final PwmDisplayBundle narrative, final AuditEventType type )
    {
        this.message = message;
        this.type = type;
        this.narrative = narrative;
        this.xdasTaxonomy = getResourceData().get( JSON_KEY_XDAS_TAXONOMY );
        this.xdasOutcome = getResourceData().get( JSON_KEY_XDAS_OUTCOME );
    }

    public Message getMessage( )
    {
        return message;
    }

    public static Optional<AuditEvent> forKey( final String key )
    {
        for ( final AuditEvent loopEvent : AuditEvent.values() )
        {
            final Message message = loopEvent.getMessage();
            if ( message != null )
            {
                final String resourceKey = message.getKey();
                if ( resourceKey.equals( key ) )
                {
                    return Optional.of( loopEvent );
                }
            }
        }

        return Optional.empty();
    }

    public String getLocalizedString( final SettingReader config, final Locale locale )
    {
        if ( this.getMessage() == null )
        {
            return "[unknown event]";
        }
        return Message.getLocalizedMessage( locale, this.getMessage(), config );
    }

    public PwmDisplayBundle getNarrative( )
    {
        return narrative;
    }

    public AuditEventType getType( )
    {
        return type;
    }

    public String getXdasTaxonomy( )
    {
        return xdasTaxonomy;
    }

    public String getXdasOutcome( )
    {
        return xdasOutcome;
    }

    private Map<String, String> getResourceData( )
    {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle( AuditEvent.class.getName() );
        final String jsonObj = resourceBundle.getString( this.toString() );
        return JsonUtil.deserializeStringMap( jsonObj );
    }
}
