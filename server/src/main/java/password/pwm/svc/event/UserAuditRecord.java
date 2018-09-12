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

import java.io.Serializable;
import java.time.Instant;

/**
 * UserAuditRecord data.
 */
public class UserAuditRecord extends AuditRecord implements Serializable
{
    protected String perpetratorID;
    protected String perpetratorDN;
    protected String perpetratorLdapProfile;
    protected String sourceAddress;
    protected String sourceHost;

    @SuppressWarnings( "checkstyle:ParameterNumber" )
    protected UserAuditRecord(
            final Instant timestamp,
            final AuditEvent eventCode,
            final String perpetratorID,
            final String perpetratorDN,
            final String perpetratorLdapProfile,
            final String message,
            final String sourceAddress,
            final String sourceHost
    )
    {
        super( timestamp, eventCode, message );
        this.perpetratorID = perpetratorID;
        this.perpetratorDN = perpetratorDN;
        this.perpetratorLdapProfile = perpetratorLdapProfile;
        this.sourceAddress = sourceAddress;
        this.sourceHost = sourceHost;
    }


    public String getPerpetratorID( )
    {
        return perpetratorID;
    }

    public String getPerpetratorDN( )
    {
        return perpetratorDN;
    }

    public String getSourceAddress( )
    {
        return sourceAddress;
    }

    public String getSourceHost( )
    {
        return sourceHost;
    }

    public String getPerpetratorLdapProfile( )
    {
        return perpetratorLdapProfile;
    }
}
