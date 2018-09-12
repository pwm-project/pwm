/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2018 The PWM Project
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

package password.pwm.svc.event;

import password.pwm.config.Configuration;
import password.pwm.i18n.Admin;
import password.pwm.i18n.Message;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.util.java.JsonUtil;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

public enum AuditEvent
{

    // system events
    STARTUP( Message.EventLog_Startup, Admin.EventLog_Narrative_Startup, Type.SYSTEM ),
    SHUTDOWN( Message.EventLog_Shutdown, Admin.EventLog_Narrative_Shutdown, Type.SYSTEM ),
    FATAL_EVENT( Message.EventLog_FatalEvent, Admin.EventLog_Narrative_FatalEvent, Type.SYSTEM ),
    INTRUDER_LOCK( Message.EventLog_IntruderLockout, Admin.EventLog_Narrative_IntruderLockout, Type.SYSTEM ),
    INTRUDER_ATTEMPT( Message.EventLog_IntruderAttempt, Admin.EventLog_Narrative_IntruderAttempt, Type.SYSTEM ),

    // user events not stored in user event history
    MODIFY_CONFIGURATION( Message.EventLog_ModifyConfiguration, Admin.EventLog_Narrative_ModifyConfiguration, Type.USER ),
    AUTHENTICATE( Message.EventLog_Authenticate, Admin.EventLog_Narrative_Authenticate, Type.USER ),
    AGREEMENT_PASSED( Message.EventLog_AgreementPassed, Admin.EventLog_Narrative_AgreementPassed, Type.USER ),
    TOKEN_ISSUED( Message.EventLog_TokenIssued, Admin.EventLog_Narrative_TokenIssued, Type.USER ),
    TOKEN_CLAIMED( Message.EventLog_TokenClaimed, Admin.EventLog_Narrative_TokenClaimed, Type.USER ),
    CLEAR_RESPONSES( Message.EventLog_ClearResponses, Admin.EventLog_Narrative_ClearResponses, Type.USER ),
    DELETE_ACCOUNT( Message.EventLog_DeleteAccount, Admin.EventLog_Narrative_DeleteAccount, Type.USER ),

    // user events stored in user event history
    CHANGE_PASSWORD( Message.EventLog_ChangePassword, Admin.EventLog_Narrative_ChangePassword, Type.USER ),
    UNLOCK_PASSWORD( Message.EventLog_UnlockPassword, Admin.EventLog_Narrative_UnlockPassword, Type.USER ),
    RECOVER_PASSWORD( Message.EventLog_RecoverPassword, Admin.EventLog_Narrative_RecoverPassword, Type.USER ),
    SET_RESPONSES( Message.EventLog_SetupResponses, Admin.EventLog_Narrative_SetupResponses, Type.USER ),
    SET_OTP_SECRET( Message.Eventlog_SetupOtpSecret, Admin.Eventlog_Narrative_SetupOtpSecret, Type.USER ),
    ACTIVATE_USER( Message.EventLog_ActivateUser, Admin.EventLog_Narrative_ActivateUser, Type.USER ),
    CREATE_USER( Message.EventLog_CreateUser, Admin.EventLog_Narrative_CreateUser, Type.USER ),
    UPDATE_PROFILE( Message.EventLog_UpdateProfile, Admin.EventLog_Narrative_UpdateProfile, Type.USER ),
    INTRUDER_USER_LOCK( Message.EventLog_IntruderUserLock, Admin.EventLog_Narrative_IntruderUserLock, Type.USER ),
    INTRUDER_USER_ATTEMPT( Message.EventLog_IntruderUserAttempt, Admin.EventLog_Narrative_IntruderUserAttempt, Type.USER ),

    // helpdesk events
    HELPDESK_SET_PASSWORD( Message.EventLog_HelpdeskSetPassword, Admin.EventLog_Narrative_HelpdeskSetPassword, Type.HELPDESK ),
    HELPDESK_UNLOCK_PASSWORD( Message.EventLog_HelpdeskUnlockPassword, Admin.EventLog_Narrative_HelpdeskUnlockPassword, Type.HELPDESK ),
    HELPDESK_CLEAR_RESPONSES( Message.EventLog_HelpdeskClearResponses, Admin.EventLog_Narrative_HelpdeskClearResponses, Type.HELPDESK ),
    HELPDESK_CLEAR_OTP_SECRET( Message.EventLog_HelpdeskClearOtpSecret, Admin.EventLog_Narrative_HelpdeskClearOtpSecret, Type.HELPDESK ),
    HELPDESK_ACTION( Message.EventLog_HelpdeskAction, Admin.EventLog_Narrative_HelpdeskAction, Type.HELPDESK ),
    HELPDESK_DELETE_USER( Message.EventLog_HelpdeskDeleteUser, Admin.EventLog_Narrative_HelpdeskDeleteUser, Type.HELPDESK ),
    HELPDESK_VIEW_DETAIL( Message.EventLog_HelpdeskViewDetail, Admin.EventLog_Narrative_HelpdeskViewDetail, Type.HELPDESK ),
    HELPDESK_VERIFY_OTP( Message.EventLog_HelpdeskVerifyOtp, Admin.EventLog_Narrative_HelpdeskVerifyOtp, Type.HELPDESK ),
    HELPDESK_VERIFY_OTP_INCORRECT( Message.EventLog_HelpdeskVerifyOtpIncorrect, Admin.EventLog_Narrative_HelpdeskVerifyOtpIncorrect, Type.HELPDESK ),
    HELPDESK_VERIFY_TOKEN( Message.EventLog_HelpdeskVerifyToken, Admin.EventLog_Narrative_HelpdeskVerifyToken, Type.HELPDESK ),
    HELPDESK_VERIFY_TOKEN_INCORRECT( Message.EventLog_HelpdeskVerifyTokenIncorrect, Admin.EventLog_Narrative_HelpdeskVerifyTokenIncorrect, Type.HELPDESK ),
    HELPDESK_VERIFY_ATTRIBUTES( Message.EventLog_HelpdeskVerifyAttributes, Admin.EventLog_Narrative_HelpdeskVerifyAttributes, Type.HELPDESK ),
    HELPDESK_VERIFY_ATTRIBUTES_INCORRECT( Message.EventLog_HelpdeskVerifyAttributesIncorrect, Admin.EventLog_Narrative_HelpdeskVerifyAttributesIncorrect, Type.HELPDESK ),;

    private static final String JSON_KEY_XDAS_TAXONOMY = "xdasTaxonomy";
    private static final String JSON_KEY_XDAS_OUTCOME = "xdasOutcome";


    private final Message message;
    private final PwmDisplayBundle narrative;

    private String xdasTaxonomy;
    private String xdasOutcome;

    private Type type;

    AuditEvent( final Message message, final PwmDisplayBundle narrative, final Type type )
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

    public static AuditEvent forKey( final String key )
    {
        for ( final AuditEvent loopEvent : AuditEvent.values() )
        {
            final Message message = loopEvent.getMessage();
            if ( message != null )
            {
                final String resourceKey = message.getKey();
                if ( resourceKey.equals( key ) )
                {
                    return loopEvent;
                }
            }
        }

        return null;
    }

    public String getLocalizedString( final Configuration config, final Locale locale )
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

    public Type getType( )
    {
        return type;
    }

    public enum Type
    {
        USER( UserAuditRecord.class ),
        SYSTEM( SystemAuditRecord.class ),
        HELPDESK( HelpdeskAuditRecord.class ),;

        private final Class clazz;

        Type( final Class clazz )
        {
            this.clazz = clazz;
        }

        public Class getDataClass( )
        {
            return clazz;
        }
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
