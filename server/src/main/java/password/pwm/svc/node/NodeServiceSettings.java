/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2020 The PWM Project
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

package password.pwm.svc.node;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.util.java.TimeDuration;

@Value
@AllArgsConstructor( access = AccessLevel.PRIVATE )
class NodeServiceSettings
{
    private final TimeDuration heartbeatInterval;
    private final TimeDuration nodeTimeout;
    private final TimeDuration nodePurgeInterval;

    static NodeServiceSettings fromConfigForDB( final Configuration configuration )
    {
        return new NodeServiceSettings(
                TimeDuration.of( Integer.parseInt( configuration.readAppProperty( AppProperty.CLUSTER_DB_HEARTBEAT_SECONDS ) ), TimeDuration.Unit.SECONDS ),
                TimeDuration.of( Integer.parseInt( configuration.readAppProperty( AppProperty.CLUSTER_DB_NODE_TIMEOUT_SECONDS ) ), TimeDuration.Unit.SECONDS ),
                TimeDuration.of( Integer.parseInt( configuration.readAppProperty( AppProperty.CLUSTER_DB_NODE_PURGE_SECONDS ) ), TimeDuration.Unit.SECONDS )
        );
    }

    static NodeServiceSettings fromConfigForLDAP( final Configuration configuration )
    {
        return new NodeServiceSettings(
                TimeDuration.of( Integer.parseInt( configuration.readAppProperty( AppProperty.CLUSTER_LDAP_HEARTBEAT_SECONDS ) ), TimeDuration.Unit.SECONDS ),
                TimeDuration.of( Integer.parseInt( configuration.readAppProperty( AppProperty.CLUSTER_LDAP_NODE_TIMEOUT_SECONDS ) ), TimeDuration.Unit.SECONDS ),
                TimeDuration.of( Integer.parseInt( configuration.readAppProperty( AppProperty.CLUSTER_LDAP_NODE_PURGE_SECONDS ) ), TimeDuration.Unit.SECONDS )
        );
    }
}
