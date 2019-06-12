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

package password.pwm.config.option;

import password.pwm.ws.server.RestAuthenticationType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
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

    private Set<RestAuthenticationType> type;

    WebServiceUsage( final RestAuthenticationType... type )
    {
        this.type = type == null ? Collections.emptySet() : Collections.unmodifiableSet( new HashSet<>( Arrays.asList( type ) ) );
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
