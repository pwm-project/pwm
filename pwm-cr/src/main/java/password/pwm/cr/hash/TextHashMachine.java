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
