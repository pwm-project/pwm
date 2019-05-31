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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

class TextHashMachine extends AbstractHashMachine implements ResponseHashMachineSpi
{

    TextHashMachine( )
    {
    }

    @Override
    public void init( final ResponseHashAlgorithm algorithm )
    {

    }

    @Override
    public Map<String, String> defaultParameters( )
    {
        final Map<String, String> defaultParamMap = new HashMap<>();
        defaultParamMap.put( HashParameter.caseSensitive.toString(), Boolean.toString( false ) );
        return Collections.unmodifiableMap( defaultParamMap );
    }

    @Override
    public StoredResponseItem generate( final String input )
    {
        return null;
    }

    @Override
    public boolean test( final StoredResponseItem hash, final String input )
    {
        if ( input == null || hash == null )
        {
            return false;
        }
        return false;
    }
}
