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

package password.pwm.svc.cluster;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.util.java.TimeDuration;

import java.util.concurrent.TimeUnit;

@Getter
@AllArgsConstructor( access = AccessLevel.PRIVATE )
class DatabaseClusterSettings
{
    private final boolean enable;
    private final TimeDuration heartbeatInterval;
    private final TimeDuration nodeTimeout;
    private final TimeDuration nodePurgeInterval;

    static DatabaseClusterSettings fromConfig( final Configuration configuration )
    {
        return new DatabaseClusterSettings(
                Boolean.parseBoolean( configuration.readAppProperty( AppProperty.CLUSTER_DB_ENABLE ) ),
                new TimeDuration( Integer.parseInt( configuration.readAppProperty( AppProperty.CLUSTER_DB_HEARTBEAT_SECONDS ) ), TimeUnit.SECONDS ),
                new TimeDuration( Integer.parseInt( configuration.readAppProperty( AppProperty.CLUSTER_DB_NODE_TIMEOUT_SECONDS ) ), TimeUnit.SECONDS ),
                new TimeDuration( Integer.parseInt( configuration.readAppProperty( AppProperty.CLUSTER_DB_NODE_PURGE_SECONDS ) ), TimeUnit.SECONDS )
        );
    }
}
