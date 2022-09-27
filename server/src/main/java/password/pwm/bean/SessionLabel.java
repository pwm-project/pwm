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

package password.pwm.bean;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Value;
import password.pwm.PwmApplication;
import password.pwm.PwmEnvironment;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequest;
import password.pwm.http.PwmRequestUtil;
import password.pwm.http.PwmSession;
import password.pwm.svc.PwmService;
import password.pwm.user.UserInfo;
import password.pwm.util.java.AtomicLoopLongIncrementer;
import password.pwm.util.java.StringUtil;
import password.pwm.util.logging.PwmLogEvent;
import password.pwm.util.logging.PwmLogger;

import javax.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.util.Objects;

@Value
@Builder( toBuilder = true, access = AccessLevel.PRIVATE )
/**
 * Increasingly miss-named data class that represents request/operation actor and origin data.
 */
public class SessionLabel implements Serializable
{
    private static final PwmLogger LOGGER = PwmLogger.forClass( SessionLabel.class );

    private static final String SYSTEM_LABEL_SESSION_ID = "#";
    private static final String RUNTIME_LABEL_SESSION_ID = "!";
    private static final String HEALTH_LABEL_SESSION_ID = "H";

    public static final SessionLabel SYSTEM_LABEL = SessionLabel.forNonUserType( ActorType.system, DomainID.systemId() );
    public static final SessionLabel HEALTH_LABEL = SessionLabel.forNonUserType( ActorType.health, DomainID.systemId() );
    public static final SessionLabel TEST_SESSION_LABEL = SessionLabel.forNonUserType( ActorType.test, DomainID.systemId() );
    public static final SessionLabel CLI_SESSION_LABEL = SessionLabel.forNonUserType( ActorType.cli, DomainID.systemId() );
    public static final SessionLabel CONTEXT_SESSION_LABEL = SessionLabel.forNonUserType( ActorType.context, DomainID.systemId() );
    public static final SessionLabel ONEJAR_LABEL = SessionLabel.forNonUserType( ActorType.onejar, DomainID.systemId() );

    private final String sessionID;
    private final String requestID;
    private final String username;
    private final String sourceAddress;
    private final String sourceHostname;
    private final String profile;
    private final String domain;
    private final ActorType actorType;

    public enum ActorType
    {
        user( null ),
        system( "#" ),
        runtime( "!" ),
        health( "-HEALTH" ),
        test( "-TEST" ),
        cli( "-CLI" ),
        onejar( "-ONEJAR" ),
        context( "-CONTEXT" ),
        rest( null ),;

        private final String defaultSessionId;

        ActorType( final String defaultSessionId )
        {
            this.defaultSessionId = defaultSessionId;
        }

        public String defaultSessionId()
        {
            return defaultSessionId;
        }
    }

    private static SessionLabel forNonUserType( final ActorType actorType, final DomainID domainID )
    {
        Objects.requireNonNull( actorType );

        final String sessionID = actorType.defaultSessionId();
        final String domainSting = domainID == null ? DomainID.systemId().stringValue() : domainID.stringValue();

        return SessionLabel.builder()
                .actorType( actorType )
                .domain( domainSting )
                .sessionID( sessionID )
                .username( actorType.name() ).build();
    }

    public static SessionLabel forRestRequest(
            final PwmApplication pwmApplication,
            final HttpServletRequest req,
            final AtomicLoopLongIncrementer requestCounter,
            final DomainID domainID
    )
    {
        final String id = "rest-" + requestCounter.next();

        return SessionLabel.forNonUserType( ActorType.rest, domainID ).toBuilder()
                .sessionID( id )
                .requestID( id )
                .sourceAddress( PwmRequestUtil.readUserNetworkAddress( req, pwmApplication.getConfig() ).orElse( "" ) )
                .sourceHostname( PwmRequestUtil.readUserHostname( req, pwmApplication.getConfig() ).orElse( "" ) )
                .build();
    }


    public static SessionLabel forSystem( final PwmEnvironment pwmEnvironment, final DomainID domainID )
    {
        return forNonUserType( pwmEnvironment != null && pwmEnvironment.isInternalRuntimeInstance()
                ? SessionLabel.ActorType.runtime
                : SessionLabel.ActorType.system, domainID );
    }

    public static SessionLabel forPwmService( final PwmService pwmService, final DomainID domainID )
    {
        return forNonUserType( ActorType.system, domainID ).toBuilder()
                .username( pwmService.getClass().getSimpleName() )
                .domain( domainID.stringValue() )
                .build();
    }

    public static SessionLabel forPwmRequest( final PwmRequest pwmRequest )
    {
        final SessionLabel.SessionLabelBuilder builder = SessionLabel.builder();

        builder.actorType( ActorType.user );
        builder.sourceAddress( pwmRequest.getSrcAddress().orElse( null ) );
        builder.sourceHostname( pwmRequest.getSrcHostname().orElse( null ) );
        builder.requestID( pwmRequest.getPwmRequestID() );
        builder.domain( pwmRequest.getDomainID().stringValue() );

        if ( pwmRequest.hasSession() )
        {
            final PwmSession pwmSession = pwmRequest.getPwmSession();
            builder.sessionID( pwmSession.getSessionStateBean().getSessionID() );

            if ( pwmRequest.isAuthenticated() )
            {
                try
                {
                    final UserInfo userInfo = pwmSession.getUserInfo();
                    final UserIdentity userIdentity = userInfo.getUserIdentity();

                    builder.username( userInfo.getUsername() );
                    builder.profile( userIdentity == null ? null : userIdentity.getLdapProfileID().stringValue() );
                }
                catch ( final PwmUnrecoverableException e )
                {
                    LOGGER.error( () -> "unexpected error reading username: " + e.getMessage(), e );
                }
            }
        }
        else
        {
            builder.sessionID( "-" );
        }

        return builder.build();
    }

    public static SessionLabel fromPwmLogEvent( final PwmLogEvent pwmLogEvent )
    {
        return SessionLabel.builder()
                .sessionID( pwmLogEvent.getSessionID() )
                .requestID( pwmLogEvent.getRequestID() )
                .username( pwmLogEvent.getUsername() )
                .sourceAddress( pwmLogEvent.getSourceAddress() )
                .domain( pwmLogEvent.getDomain() )
                .build();
    }

    public String toDebugLabel( )
    {
        final StringBuilder sb = new StringBuilder();
        final String sessionID = getSessionID();
        final String username = getUsername();

        if ( StringUtil.notEmpty( sessionID ) )
        {
            sb.append( sessionID );
        }
        if ( StringUtil.notEmpty( domain ) )
        {
            if ( sb.length() > 0 )
            {
                sb.append( ',' );
            }
            sb.append( domain );
        }

        if ( actorType == ActorType.user && StringUtil.notEmpty( username ) )
        {
            if ( sb.length() > 0 )
            {
                sb.append( ',' );
            }
            sb.append( username );
        }

        if ( sb.length() > 0 )
        {
            sb.insert( 0, "{" );
            sb.append( "} " );
        }

        return sb.toString();
    }

    public boolean isRuntime()
    {
        return this.actorType == ActorType.runtime;
    }

    public boolean isHealth()
    {
        return this.actorType == ActorType.health;
    }
}
