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

package password.pwm.config.stored.ng;

import password.pwm.bean.UserIdentity;
import password.pwm.config.StoredValue;
import password.pwm.config.stored.StoredConfigReference;
import password.pwm.config.stored.ValueMetaData;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

class NGStorageEngineImpl
{
    private final Map<StoredConfigReference, StoredValue> storedValues = new HashMap<>();
    private final Map<StoredConfigReference, ValueMetaData> metaValues = new HashMap<>();

    NGStorageEngineImpl()
    {
    }

    NGStorageEngineImpl(
            final Map<StoredConfigReference, StoredValue> storedValues,
            final Map<StoredConfigReference, ValueMetaData> metaValues
    )
    {
        this.storedValues.putAll( storedValues );
        this.metaValues.putAll( metaValues );
    }

    StoredValue read( final StoredConfigReference storedConfigReference )
    {
        return storedValues.get( storedConfigReference );
    }

    ValueMetaData readMetaData( final StoredConfigReference storedConfigReference )
    {
        return metaValues.get( storedConfigReference );
    }

    void writeMetaData( final StoredConfigReference storedConfigReference, final ValueMetaData valueMetaData )
    {
        metaValues.put( storedConfigReference, valueMetaData );
    }

    void write( final StoredConfigReference reference, final StoredValue value, final UserIdentity userIdentity )
    {
        if ( reference != null )
        {
            if ( value != null )
            {
                storedValues.put( reference, value );
            }

            updateUserIdentity( reference, userIdentity );
        }
    }

    void reset( final StoredConfigReference reference, final UserIdentity userIdentity )
    {
        if ( reference != null )
        {
            storedValues.remove( reference );
            updateUserIdentity( reference, userIdentity );
        }
    }

    private void updateUserIdentity(
            final StoredConfigReference reference,
            final UserIdentity userIdentity
    )
    {
        metaValues.put(
                reference,
                ValueMetaData.builder().modifyDate( Instant.now() )
                        .userIdentity( userIdentity )
                        .build() );

    }
}
