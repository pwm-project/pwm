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

package password.pwm.bean;

import password.pwm.config.PwmSetting;
import password.pwm.config.PwmSettingScope;
import password.pwm.config.value.StringValue;
import password.pwm.util.java.MiscUtil;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class DomainID implements Comparable<DomainID>, Serializable
{
    public static final List<String> DOMAIN_RESERVED_WORDS = List.of( "system", "private", "public", "pwm", "sspr", "domain", "profile", "password" );
    public static final DomainID DOMAIN_ID_DEFAULT = create( "default" );

    private static final String SYSTEM_ID = "system";
    private static final DomainID SYSTEM_DOMAIN_ID = new DomainID( SYSTEM_ID );

    // sort placing 'system' first then alphabetically.
    private static final Comparator<DomainID> COMPARATOR = Comparator.comparing( DomainID::isSystem )
            .reversed()
            .thenComparing( DomainID::stringValue );

    private final String domainID;

    private DomainID( final String domainID )
    {
        this.domainID = Objects.requireNonNull( domainID );
    }

    public static DomainID create( final String domainID )
    {
        Objects.requireNonNull( domainID );
        
        final List<String> errorMessages = StringValue.validateValue( PwmSetting.DOMAIN_LIST, domainID );
        if ( !errorMessages.isEmpty() )
        {
            throw new IllegalArgumentException( "domainID value '" + domainID + "' does not match required syntax pattern for user defined domains: " + errorMessages.get( 0 ) );
        }
        return new DomainID( domainID );
    }

    public boolean inScope( final PwmSettingScope scope )
    {
        switch ( scope )
        {
            case SYSTEM:
                return this.isSystem();

            case DOMAIN:
                return !this.isSystem();

            default:
                MiscUtil.unhandledSwitchStatement( scope );
        }

        return false;
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
        final DomainID otherDomainID = ( DomainID ) o;
        return Objects.equals( domainID, otherDomainID.domainID );
    }

    @Override
    public int hashCode()
    {
        return Objects.hash( domainID );
    }

    @Override
    public int compareTo( final DomainID o )
    {
        return COMPARATOR.compare( this, o );
    }

    @Override
    public String toString()
    {
        return domainID;
    }

    public String stringValue()
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
