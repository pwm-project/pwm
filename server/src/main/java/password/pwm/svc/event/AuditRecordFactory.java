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

import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.PwmConstants;
import password.pwm.PwmDomain;
import password.pwm.bean.DomainID;
import password.pwm.bean.ProfileID;
import password.pwm.bean.SessionLabel;
import password.pwm.bean.UserIdentity;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmSession;
import password.pwm.i18n.PwmDisplayBundle;
import password.pwm.user.UserInfo;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.svc.userhistory.LdapXmlUserHistory;
import password.pwm.util.i18n.LocaleHelper;
import password.pwm.util.json.JsonFactory;
import password.pwm.util.logging.PwmLogger;
import password.pwm.util.macro.MacroRequest;
import password.pwm.util.secure.PwmRandom;

import java.time.Instant;
import java.util.Map;

public class AuditRecordFactory
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( AuditRecordFactory.class );

    private final SessionLabel sessionLabel;
    private final DomainID domainID;
    private final PwmApplication pwmApplication;
    private final MacroRequest macroRequest;

    private AuditRecordFactory(
            final SessionLabel sessionLabel,
            final DomainID domainID,
            final PwmApplication pwmApplication,
            final MacroRequest macroRequest )
    {
        this.sessionLabel = sessionLabel;
        this.domainID = domainID;
        this.pwmApplication = pwmApplication;
        this.macroRequest = macroRequest;
    }

    public static AuditRecordFactory make( final SessionLabel sessionLabel, final PwmDomain pwmDomain ) throws PwmUnrecoverableException
    {
        return new AuditRecordFactory(
                sessionLabel,
                pwmDomain.getDomainID(),
                pwmDomain.getPwmApplication(),
                MacroRequest.forNonUserSpecific( pwmDomain.getPwmApplication(), sessionLabel ) );
    }

    public static AuditRecordFactory make( final PwmRequest pwmRequest ) throws PwmUnrecoverableException
    {
        return new AuditRecordFactory(
                pwmRequest.getLabel(),
                pwmRequest.getDomainID(),
                pwmRequest.getPwmApplication(),
                pwmRequest.getMacroMachine() );
    }

    public static AuditRecordFactory make( final SessionLabel sessionLabel, final PwmDomain pwmDomain, final MacroRequest macroRequest )
    {
        return new AuditRecordFactory(
                sessionLabel,
                pwmDomain.getDomainID(),
                pwmDomain.getPwmApplication(),
                macroRequest );
    }

    public static AuditRecordFactory make( final SessionLabel sessionLabel, final PwmApplication pwmApplication )
    {
        return new AuditRecordFactory(
                sessionLabel,
                DomainID.systemId(),
                pwmApplication,
                MacroRequest.forNonUserSpecific( pwmApplication, sessionLabel ) );
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
        final AuditUserDefinition perAuditUserDefinition = userIdentityToUserDefinition( target );
        return createHelpdeskAuditRecord(
                eventCode,
                perpetrator,
                message,
                perAuditUserDefinition,
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
        final AuditUserDefinition perAuditUserDefinition = userIdentityToUserDefinition( perpetrator );

        final AuditRecordData record = baseRecord().toBuilder()
                .type( AuditEventType.HELPDESK )
                .eventCode( eventCode )
                .perpetratorID( perAuditUserDefinition.getUserID() )
                .perpetratorDN( perAuditUserDefinition.getUserDN() )
                .perpetratorLdapProfile( perAuditUserDefinition.getLdapProfile() )
                .message( message )
                .targetID( target.getUserID() )
                .targetDN( target.getUserDN() )
                .targetLdapProfile( target.getLdapProfile() )
                .sourceAddress( sourceAddress )
                .sourceHost( sourceHost )
                .domain( this.domainID )
                .build();

        return record.toBuilder().narrative( makeNarrativeString( record ) ).build();
    }

    private static AuditRecordData baseRecord()
    {
        return AuditRecordData.builder()
                .timestamp( Instant.now() )
                .guid( PwmRandom.getInstance().randomUUID().toString() )
                .build();
    }

    public UserAuditRecord createUserAuditRecord(
            final AuditEvent eventCode,
            final UserIdentity perpetrator,
            final String message,
            final String sourceAddress,
            final String sourceHost
    )
    {
        final AuditUserDefinition perAuditUserDefinition = userIdentityToUserDefinition( perpetrator );

        final AuditRecordData record = baseRecord().toBuilder()
                .type( AuditEventType.USER )
                .eventCode( eventCode )
                .perpetratorID( perAuditUserDefinition.getUserID() )
                .perpetratorDN( perAuditUserDefinition.getUserDN() )
                .perpetratorLdapProfile( perAuditUserDefinition.getLdapProfile() )
                .message( message )
                .sourceAddress( sourceAddress )
                .sourceHost( sourceHost )
                .domain( this.domainID )
                .build();

        return record.toBuilder().narrative( makeNarrativeString( record ) ).build();
    }

    public SystemAuditRecord createSystemAuditRecord(
            final AuditEvent eventCode,
            final String message
    )
    {
        final AuditRecordData record = baseRecord().toBuilder()
                .type( AuditEventType.SYSTEM )
                .eventCode( eventCode )
                .message( message )
                .instance( this.pwmApplication.getInstanceID() )
                .domain( DomainID.systemId() )
                .build();

        return record.toBuilder().narrative( makeNarrativeString( record ) ).build();
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

    public UserAuditRecord fromStoredRecord( final LdapXmlUserHistory.StoredEvent storedEvent, final UserInfo userInfoBean )
    {
        final AuditUserDefinition perAuditUserDefinition = userIdentityToUserDefinition( userInfoBean.getUserIdentity() );

        return AuditRecordData.builder()
                .timestamp( Instant.ofEpochMilli( storedEvent.getTimestamp() ) )
                .eventCode( storedEvent.getAuditEvent() )
                .type( AuditEventType.USER )
                .perpetratorID( perAuditUserDefinition.getUserID() )
                .perpetratorDN( perAuditUserDefinition.getUserDN() )
                .perpetratorLdapProfile( perAuditUserDefinition.getLdapProfile() )
                .message( storedEvent.getMessage() )
                .sourceAddress( storedEvent.getSourceAddress() )
                .sourceHost( storedEvent.getSourceHost() )
                .domain( this.domainID )
                .build();
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
                sessionLabel != null ? sessionLabel.getSourceAddress() : null,
                sessionLabel != null ? sessionLabel.getSourceHostname() : null
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
        final PwmDisplayBundle pwmDisplayBundle = auditRecord.eventCode().getNarrative();

        String outputString = LocaleHelper.getLocalizedMessage( PwmConstants.DEFAULT_LOCALE, pwmDisplayBundle, pwmApplication.getConfig() );

        if ( macroRequest != null )
        {
            outputString = macroRequest.expandMacros( outputString );
        }

        final Map<String, String> recordFields = JsonFactory.get().deserializeStringMap( JsonFactory.get().serialize( auditRecord ) );
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
        ProfileID ldapProfile = null;

        if ( userIdentity != null )
        {
            userDN = userIdentity.getUserDN();
            ldapProfile = userIdentity.getLdapProfileID();
            try
            {
                final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                        pwmApplication,
                        sessionLabel,
                        userIdentity, PwmConstants.DEFAULT_LOCALE
                );
                userID = userInfo.getUsername();
            }
            catch ( final Exception e )
            {
                LOGGER.warn( sessionLabel, () -> "unable to read userID for " + userIdentity + ", error: " + e.getMessage() );
            }
        }

        return new AuditUserDefinition( userID, userDN, ldapProfile );
    }

    @Value
    public static class AuditUserDefinition
    {
        private final String userID;
        private final String userDN;
        private final ProfileID ldapProfile;
    }
}



