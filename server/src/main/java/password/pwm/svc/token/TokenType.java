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

package password.pwm.svc.token;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public enum TokenType
{
    FORGOTTEN_PW( "password.pwm.servlet.ForgottenPasswordServlet" ),
    ACTIVATION( "password.pwm.servlet.ActivateUserServlet" ),
    UPDATE( ),
    NEWUSER( ),;

    private final Set<String> otherNames;

    TokenType( final String... otherNames )
    {
        final Set<String> otherNamesSet = new HashSet<>();
        if ( otherNames != null )
        {
            otherNamesSet.addAll( Arrays.asList( otherNames ) );
        }
        otherNamesSet.add( getName() );
        this.otherNames = Collections.unmodifiableSet( otherNamesSet );
    }

    public String getName( )
    {
        return this.toString();
    }

    public boolean matchesName( final String input )
    {
        return otherNames.contains( input );
    }
}
