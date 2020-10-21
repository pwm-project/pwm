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

package password.pwm.config.option;

import password.pwm.ws.server.RestAuthenticationType;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

public enum WebServiceUsage
{
    Challenges( RestAuthenticationType.NAMED_SECRET, RestAuthenticationType.LDAP ),
    CheckPassword( RestAuthenticationType.NAMED_SECRET, RestAuthenticationType.LDAP ),
    ForgottenPassword( RestAuthenticationType.PUBLIC ),
    Health( RestAuthenticationType.PUBLIC ),
    Profile( RestAuthenticationType.NAMED_SECRET, RestAuthenticationType.LDAP ),
    RandomPassword( RestAuthenticationType.PUBLIC, RestAuthenticationType.NAMED_SECRET, RestAuthenticationType.LDAP ),
    SetPassword( RestAuthenticationType.NAMED_SECRET, RestAuthenticationType.LDAP ),
    SigningForm( RestAuthenticationType.NAMED_SECRET ),
    Statistics( RestAuthenticationType.PUBLIC, RestAuthenticationType.NAMED_SECRET, RestAuthenticationType.LDAP ),
    Status( RestAuthenticationType.NAMED_SECRET, RestAuthenticationType.LDAP ),
    VerifyOtp( RestAuthenticationType.NAMED_SECRET, RestAuthenticationType.LDAP ),
    VerifyResponses( RestAuthenticationType.NAMED_SECRET, RestAuthenticationType.LDAP ),;

    private final Set<RestAuthenticationType> type;

    WebServiceUsage( final RestAuthenticationType... type )
    {
        final EnumSet<RestAuthenticationType> typeSet = EnumSet.noneOf( RestAuthenticationType.class );
        typeSet.addAll( Arrays.asList( type ) );
        this.type = Collections.unmodifiableSet( typeSet );
    }

    public Set<RestAuthenticationType> getTypes()
    {
        return type;
    }

    public static Set<WebServiceUsage> forType( final RestAuthenticationType type )
    {
        return Collections.unmodifiableSet(
                Arrays.stream( WebServiceUsage.values() )
                        .filter( webServiceUsage -> webServiceUsage.getTypes().contains( type ) )
                        .collect( Collectors.toSet() )
        );
    }
}
