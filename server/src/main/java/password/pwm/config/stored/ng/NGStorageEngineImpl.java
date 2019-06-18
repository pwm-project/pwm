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
