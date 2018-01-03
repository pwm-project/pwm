/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.ldap.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.util.LocaleHelper;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroMachine;

import java.time.Instant;
import java.util.Map;

public class AuditRecordFactory
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AuditRecordFactory.class );

    private final PwmApplication pwmApplication;
    private final MacroMachine macroMachine;

    public AuditRecordFactory( final PwmApplication pwmApplication ) throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;
        this.macroMachine = MacroMachine.forNonUserSpecific( pwmApplication, null );
    }

    public AuditRecordFactory( final PwmApplication pwmApplication, final MacroMachine macroMachine )
    {
        this.pwmApplication = pwmApplication;
        this.macroMachine = macroMachine;
    }

    public AuditRecordFactory( final PwmApplication pwmApplication, final PwmSession pwmSession ) throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmApplication;
        this.macroMachine = pwmSession.getSessionManager().getMacroMachine( pwmApplication );
    }

    public AuditRecordFactory( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        this.pwmApplication = pwmRequest.getPwmApplication();
        this.macroMachine = pwmRequest.getPwmSession().getSessionManager().getMacroMachine( pwmApplication );
    }

    public HelpdeskAuditRecord createHelpdeskAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final String message,
            final UserIdentity target,
            final String sourceAddress,
            final String sourceHost
    )
    {

        final AuditUserDefinition targetAuditUserDefintition = userIdentityToUserDefinition( target );
        return createHelpdeskAuditRecord(
                eventCode,
                perpetrator,
                message,
                targetAuditUserDefintition,
                sourceAddress,
                sourceHost
        );
    }

    public HelpdeskAuditRecord createHelpdeskAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final String message,
            final AuditUserDefinition target,
            final String sourceAddress,
            final String sourceHost
    )
    {
        final AuditUserDefinition perpAuditUserDefintition = userIdentityToUserDefinition( perpetrator );

        final HelpdeskAuditRecord record = new HelpdeskAuditRecord(
                Instant.now(),
                eventCode,
                perpAuditUserDefintition.getUserID(),
                perpAuditUserDefintition.getUserDN(),
                perpAuditUserDefintition.getLdapProfile(),
                message,
                target.getUserID(),
                target.getUserDN(),
                target.getLdapProfile(),
                sourceAddress,
                sourceHost
        );
        record.narrative = makeNarrativeString( record );
        return record;
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final String message,
            final String sourceAddress,
            final String sourceHost
    )
    {
        final AuditUserDefinition perpAuditUserDefintition = userIdentityToUserDefinition( perpetrator );

        final UserAuditRecord record = new UserAuditRecord(
                Instant.now(),
                eventCode,
                perpAuditUserDefintition.getUserID(),
                perpAuditUserDefintition.getUserDN(),
                perpAuditUserDefintition.getLdapProfile(),
                message,
                sourceAddress,
                sourceHost
        );
        record.narrative = this.makeNarrativeString( record );
        return record;
    }

    public SystemAuditRecord createSystemAuditRecord(
            final AuditEvent eventCode,
            final String message
    )
    {
        final SystemAuditRecord record = new SystemAuditRecord( eventCode, message, pwmApplication.getInstanceID() );
        record.narrative = this.makeNarrativeString( record );
        return record;
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final SessionLabel sessionLabel
    )
    {
        return createUserAuditRecord(
                eventCode,
                perpetrator,
                sessionLabel,
                null
        );
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final SessionLabel sessionLabel,
            final String message
    )
    {
        return createUserAuditRecord(
                eventCode,
                perpetrator,
                message,
                sessionLabel != null ? sessionLabel.getSrcAddress() : null,
                sessionLabel != null ? sessionLabel.getSrcHostname() : null
        );
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserInfo userInfo,
            final PwmSession pwmSession
    )
    {
        return createUserAuditRecord(
                eventCode,
                userInfo.getUserIdentity(),
                null,
                pwmSession.getSessionStateBean().getSrcAddress(),
                pwmSession.getSessionStateBean().getSrcHostname()
        );
    }


    private String makeNarrativeString( final AuditRecord auditRecord )
    {
        final PwmDisplayBundle pwmDisplayBundle = auditRecord.getEventCode().getNarrative();

        String outputString = LocaleHelper.getLocalizedMessage( PwmConstants.DEFAULT_LOCALE, pwmDisplayBundle, pwmApplication.getConfig() );

        if ( macroMachine != null )
        {
            outputString = macroMachine.expandMacros( outputString );
        }

        final Map<String, String> recordFields = JsonUtil.deserializeStringMap( JsonUtil.serialize( auditRecord ) );
        for ( final Map.Entry<String, String> entry : recordFields.entrySet() )
        {
            final String key = entry.getKey();
            final String value = entry.getValue();
            final String parametrizedKey = "%" + key + "%";
            outputString = outputString.replace( parametrizedKey, value );
        }

        return outputString;
    }

    private AuditUserDefinition userIdentityToUserDefinition( final UserIdentity userIdentity )
    {
        String userDN = null;
        String userID = null;
        String ldapProfile = null;

        if ( userIdentity != null )
        {
            userDN = userIdentity.getUserDN();
            ldapProfile = userIdentity.getLdapProfileID();
            try
            {
                final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                        pwmApplication,
                        SessionLabel.SYSTEM_LABEL,
                        userIdentity, PwmConstants.DEFAULT_LOCALE
                );
                userID = userInfo.getUsername();
            }
            catch ( Exception e )
            {
                LOGGER.warn( "unable to read userID for " + userIdentity + ", error: " + e.getMessage() );
            }
        }

        return new AuditUserDefinition( userID, userDN, ldapProfile );
    }

    public static class AuditUserDefinition
    {
        private final String userID;
        private final String userDN;
        private final String ldapProfile;

        public AuditUserDefinition( final String userID, final String userDN, final String ldapProfile )
        {
            this.userID = userID;
            this.userDN = userDN;
            this.ldapProfile = ldapProfile;
        }

        public String getUserID( )
        {
            return userID;
        }

        public String getUserDN( )
        {
            return userDN;
        }

        public String getLdapProfile( )
        {
            return ldapProfile;
        }
    }
}
