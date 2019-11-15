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

package password.pwm.config.value;

import password.pwm.PwmConstants;
import password.pwm.config.StoredValue;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;
import password.pwm.util.java.LazySupplier;
import password.pwm.util.secure.SecureEngine;

import java.io.Serializable;
import java.util.Locale;

public abstract class AbstractValue implements StoredValue
{
    private transient LazySupplier<String> valueHash = new LazySupplier<>( this::valueHashImpl );

    public String toString()
    {
        return toDebugString( null );
    }

    @Override
    public String toDebugString( final Locale locale )
    {
        return JsonUtil.serialize( ( Serializable ) this.toNativeObject(), JsonUtil.Flag.PrettyPrint );
    }

    @Override
    public Serializable toDebugJsonObject( final Locale locale )
    {
        return ( Serializable ) this.toNativeObject();
    }

    @Override
    public int currentSyntaxVersion()
    {
        return 0;
    }

    @Override
    public String valueHash()
    {
        return valueHash.get();
    }

    protected String valueHashImpl()
    {
        try
        {
            return SecureEngine.hash( JsonUtil.serialize( ( Serializable ) this.toNativeObject() ), PwmConstants.SETTING_CHECKSUM_HASH_METHOD );
        }
        catch ( final PwmUnrecoverableException e )
        {
            throw new IllegalStateException( e );
        }
    }
}
