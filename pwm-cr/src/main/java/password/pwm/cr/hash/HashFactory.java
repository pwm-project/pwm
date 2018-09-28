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

package password.pwm.cr.hash;

import password.pwm.cr.api.StoredResponseItem;

import java.util.Map;

public class HashFactory
{
    StoredResponseItem responseItemForRawValue(
            final String response,
            final ResponseHashAlgorithm responseHashAlgorithm,
            final Map<HashParameter, String> parameters

    )
    {
        return null;
    }

    public static boolean testResponseItem(
            final StoredResponseItem storedResponseItem,
            final String answer
    )
    {
        final ResponseHashMachine responseHashMachine = machineForStoredResponse( storedResponseItem );
        return responseHashMachine.test( storedResponseItem, answer );
    }


    private static ResponseHashMachine machineForStoredResponse( final StoredResponseItem storedResponseItem )
    {
        final String algName = storedResponseItem.getFormat();
        final ResponseHashAlgorithm alg;
        try
        {
            alg = ResponseHashAlgorithm.valueOf( algName );
        }
        catch ( IllegalArgumentException e )
        {
            throw new IllegalArgumentException( "unknown format type '" + algName + "'" );
        }
        final Class algClass = alg.getImplementingClass();
        final ResponseHashMachineSpi responseHashMachine;
        try
        {
            responseHashMachine = ( ResponseHashMachineSpi ) algClass.newInstance();
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( "unexpected error instantiating response hash machine spi class: " + e.getMessage() );
        }
        responseHashMachine.init( alg );
        return responseHashMachine;
    }
}
