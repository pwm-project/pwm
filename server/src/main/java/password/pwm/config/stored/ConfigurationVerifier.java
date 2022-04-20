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

package password.pwm.config.stored;

import password.pwm.PwmConstants;
import password.pwm.bean.DomainID;
import password.pwm.config.PwmSetting;
import password.pwm.config.value.StoredValue;
import password.pwm.config.value.ValueTypeConverter;
import password.pwm.error.PwmError;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.CollectionUtil;

import java.util.List;
import java.util.Optional;

class ConfigurationVerifier
{
    private static final List<Verifier> VERIFIERS = List.of(
            new BlockDeprecatedPermittedIpMaskSetting()
    );

    static void verifyConfiguration(
            final StoredConfiguration storedConfiguration
    )
            throws PwmUnrecoverableException
    {
        for ( final Verifier verifier : VERIFIERS )
        {
            verifier.verify( storedConfiguration );
        }
    }

    interface Verifier
    {
        void verify( StoredConfiguration storedConfiguration )
                throws PwmUnrecoverableException;

    }

    private static class BlockDeprecatedPermittedIpMaskSetting implements Verifier
    {
        @Override
        public void verify( final StoredConfiguration storedConfiguration )
                throws PwmUnrecoverableException
        {
            final StoredConfigKey interestedKey = StoredConfigKey.forSetting(
                    PwmSetting.IP_PERMITTED_RANGE,
                    null,
                    DomainID.systemId() );
            final Optional<StoredValue> storedValue = storedConfiguration.readStoredValue( interestedKey );
            if ( storedValue.isPresent() )
            {
                final List<String> oldValues = ValueTypeConverter.valueToStringArray( storedValue.get() );
                if ( !CollectionUtil.isEmpty( oldValues ) )
                {
                    final String firstValue = oldValues.get( 0 );
                    final String errorMsg = "Deprecated configuration setting '"
                            + PwmSetting.IP_PERMITTED_RANGE.toMenuLocationDebug( null, PwmConstants.DEFAULT_LOCALE )
                            + "' has a value of '" + firstValue
                            + "'.  This setting is no longer used, and the configuration must not contain a value for this setting.";
                    throw PwmUnrecoverableException.newException( PwmError.CONFIG_FORMAT_ERROR, errorMsg );
                }
            }
        }
    }

}
