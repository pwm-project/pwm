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

import lombok.Builder;
import lombok.Value;
import password.pwm.PwmConstants;
import password.pwm.svc.PwmService;
import password.pwm.util.java.StringUtil;

import java.io.Serializable;

@Value
@Builder( toBuilder = true )
public class SessionLabel implements Serializable
{
    private static final String SYSTEM_LABEL_SESSION_ID = "#";
    private static final String RUNTIME_LABEL_SESSION_ID = "#";

    public static final SessionLabel SYSTEM_LABEL = SessionLabel.builder().sessionID( SYSTEM_LABEL_SESSION_ID ).username( PwmConstants.PWM_APP_NAME ).build();
    public static final SessionLabel RUNTIME_LABEL = SessionLabel.builder().sessionID( RUNTIME_LABEL_SESSION_ID ).username( "internal" ).build();
    public static final SessionLabel TEST_SESSION_LABEL = SessionLabel.builder().sessionID( SYSTEM_LABEL_SESSION_ID ).username( "test" ).build();
    public static final SessionLabel CLI_SESSION_LABEL = SessionLabel.builder().sessionID( SYSTEM_LABEL_SESSION_ID ).username( "cli" ).build();
    public static final SessionLabel CONTEXT_SESSION_LABEL = SessionLabel.builder().sessionID( SYSTEM_LABEL_SESSION_ID ).username( "context" ).build();
    public static final SessionLabel ONEJAR_LABEL = SessionLabel.builder().sessionID( SYSTEM_LABEL_SESSION_ID ).username( "onejar" ).build();


    private final String sessionID;
    private final String requestID;
    private final String userID;
    private final String username;
    private final String sourceAddress;
    private final String sourceHostname;
    private final String profile;
    private final String domain;

    public static SessionLabel forPwmService( final PwmService pwmService, final DomainID domainID )
    {
        return SessionLabel.builder()
                .sessionID( SYSTEM_LABEL_SESSION_ID )
                .username( pwmService.getClass().getSimpleName() )
                .domain( domainID.stringValue() )
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
        if ( StringUtil.notEmpty( username ) )
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

}
