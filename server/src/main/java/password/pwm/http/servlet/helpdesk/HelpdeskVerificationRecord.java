/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2021 The PWM Project
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

package password.pwm.http.servlet.helpdesk;

import org.jetbrains.annotations.NotNull;
import password.pwm.PwmConstants;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.http.PwmRequestContext;
import password.pwm.ldap.UserInfoFactory;
import password.pwm.user.UserInfo;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;

record HelpdeskVerificationRecord(
        UserIdentity identity,
        IdentityVerificationMethod method,
        Instant timestamp
)
        implements Comparable<HelpdeskVerificationRecord>
{
    HelpdeskVerificationRecord(
            final UserIdentity identity,
            final IdentityVerificationMethod method,
            final Instant timestamp
    )
    {
        this.timestamp = Objects.requireNonNull( timestamp );
        this.identity = Objects.requireNonNull( identity );
        this.method = Objects.requireNonNull( method );
    }

    private static final Comparator<HelpdeskVerificationRecord> COMPARATOR = Comparator.comparing(
                    HelpdeskVerificationRecord::identity )
            .thenComparing( HelpdeskVerificationRecord::method )
            .thenComparing( HelpdeskVerificationRecord::timestamp );

    boolean matches( final UserIdentity identity, final IdentityVerificationMethod method )
    {
        return this.method == method && Objects.equals( this.identity, identity );
    }


    @Override
    public int compareTo( @NotNull final HelpdeskVerificationRecord o )
    {
        return COMPARATOR.compare( this, o );
    }

    public HelpdeskVerificationDisplayRecord toViewableRecord( final PwmRequestContext pwmRequestContext )
            throws PwmUnrecoverableException
    {
        final UserInfo userInfo = UserInfoFactory.newUserInfoUsingProxy(
                pwmRequestContext.getPwmApplication(),
                pwmRequestContext.getSessionLabel(),
                this.identity(),
                PwmConstants.DEFAULT_LOCALE );
        final String username = userInfo.getUsername();
        final String profile =
                pwmRequestContext.getPwmDomain().getConfig().getLdapProfiles().get( this.identity().getLdapProfileID() )
                .getDisplayName( pwmRequestContext.getLocale() );
        final String method = this.method().getLabel( pwmRequestContext.getPwmDomain().getConfig(),
                pwmRequestContext.getLocale() );

        return new HelpdeskVerificationDisplayRecord( this.timestamp(), profile, username,
                method );
    }
}
