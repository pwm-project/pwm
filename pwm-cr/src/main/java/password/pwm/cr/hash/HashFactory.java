/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2019 The PWM Project
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
        catch ( final IllegalArgumentException e )
        {
            throw new IllegalArgumentException( "unknown format type '" + algName + "'" );
        }
        final Class algClass = alg.getImplementingClass();
        final ResponseHashMachineSpi responseHashMachine;
        try
        {
            responseHashMachine = ( ResponseHashMachineSpi ) algClass.newInstance();
        }
        catch ( final Exception e )
        {
            throw new IllegalStateException( "unexpected error instantiating response hash machine spi class: " + e.getMessage() );
        }
        responseHashMachine.init( alg );
        return responseHashMachine;
    }
}
