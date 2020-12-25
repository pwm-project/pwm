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

package password.pwm.bean;

import org.jetbrains.annotations.NotNull;
import password.pwm.config.PwmSetting;

import java.io.Serializable;
import java.util.Objects;
import java.util.regex.Pattern;

public class DomainID implements Comparable<DomainID>, Serializable
{
    private static final String SYSTEM_ID = "system";
    private static final DomainID SYSTEM_DOMAIN_ID = new DomainID( SYSTEM_ID );
    private static final Pattern REGEX_PATTERN = PwmSetting.DOMAIN_LIST.getRegExPattern();

    private final String domainID;

    private DomainID( final String domainID )
    {
        this.domainID = Objects.requireNonNull( domainID );
    }

    public static DomainID create( final String domainID )
    {
        if ( !REGEX_PATTERN.matcher( domainID ).matches() )
        {
            throw new IllegalArgumentException( "domainID value does not match required syntax pattern" );
        }
        return new DomainID( domainID );
    }

    @Override
    public boolean equals( final Object o )
    {
        if ( this == o )
        {
            return true;
        }
        if ( o == null || getClass() != o.getClass() )
        {
            return false;
        }
        final DomainID domainID1 = ( DomainID ) o;
        return Objects.equals( domainID, domainID1.domainID );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( domainID );
    }

    @Override
    public int compareTo( @NotNull final DomainID o )
    {
        return this.domainID.compareTo( o.domainID );
    }

    @Override
    public String toString()
    {
        return domainID;
    }

    public static DomainID systemId()
    {
        return SYSTEM_DOMAIN_ID;
    }

    public boolean isSystem()
    {
        return SYSTEM_ID.equals( domainID );
    }
}
