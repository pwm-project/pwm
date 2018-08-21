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

package password.pwm.svc.cluster;

import password.pwm.PwmApplication;
import password.pwm.error.PwmException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.health.HealthRecord;
import password.pwm.svc.PwmService;
import password.pwm.util.logging.PwmLogger;

import java.util.Collections;
import java.util.List;

public class ClusterService implements PwmService
{

    private static final PwmLogger LOGGER = PwmLogger.forClass( ClusterService.class );

    private PwmApplication pwmApplication;
    private STATUS status = STATUS.NEW;
    private ClusterProvider clusterProvider;

    @Override
    public STATUS status( )
    {
        return status;
    }

    @Override
    public void init( final PwmApplication pwmApplication ) throws PwmException
    {
        status = STATUS.OPENING;
        this.pwmApplication = pwmApplication;

        try
        {
            if ( this.pwmApplication.getConfig().hasDbConfigured() )
            {
                clusterProvider = new DatabaseClusterProvider( pwmApplication );
            }
        }
        catch ( PwmException e )
        {
            LOGGER.error( "error starting up cluster provider service: " + e.getMessage() );
            status = STATUS.CLOSED;
            return;
        }

        status = STATUS.OPEN;
    }

    @Override
    public void close( )
    {
        if ( clusterProvider != null )
        {
            clusterProvider.close();
            clusterProvider = null;
        }
        clusterProvider = null;
        status = STATUS.CLOSED;
    }

    @Override
    public List<HealthRecord> healthCheck( )
    {
        return null;
    }

    @Override
    public ServiceInfoBean serviceInfo( )
    {
        return null;
    }

    public boolean isMaster( )
    {
        if ( clusterProvider != null )
        {
            return clusterProvider.isMaster();
        }

        return false;
    }

    public List<NodeInfo> nodes( ) throws PwmUnrecoverableException
    {
        if ( status == STATUS.OPEN && clusterProvider != null )
        {
            return clusterProvider.nodes();
        }
        return Collections.emptyList();
    }
}
