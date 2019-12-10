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

package password.pwm.config.function;

import com.google.gson.reflect.TypeToken;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationModifier;
import password.pwm.config.value.ActionValue;
import password.pwm.config.value.data.ActionConfiguration;
import password.pwm.error.ErrorInformation;
import password.pwm.error.PwmError;
import password.pwm.error.PwmOperationalException;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.java.JsonUtil;

import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

public class ActionCertImportFunction extends AbstractUriCertImportFunction
{
    private static final String KEY_ITERATION = "iteration";
    private static final String KEY_WEB_ACTION_ITERATION = "webActionIter";

    @Override
    String getUri(
            final StoredConfigurationModifier modifier,
            final PwmSetting pwmSetting,
            final String profile,
            final String extraData
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Map<String, Integer> extraDataMap = JsonUtil.deserialize( extraData, new TypeToken<Map<String, Integer>>()
        {
        } );

        final ActionValue actionValue = ( ActionValue ) modifier.newStoredConfiguration().readSetting( pwmSetting, profile );
        final ActionConfiguration action = ( actionValue.toNativeObject() ).get( extraDataMap.get( KEY_ITERATION ) );
        final ActionConfiguration.WebAction webAction = action.getWebActions().get( extraDataMap.get( KEY_WEB_ACTION_ITERATION ) );

        final String uriString = webAction.getUrl();

        if ( uriString == null || uriString.isEmpty() )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.CONFIG_FORMAT_ERROR,
                    "Setting " + pwmSetting.toMenuLocationDebug( profile, null )
                            + " action URL must first be configured" );
            throw new PwmOperationalException( errorInformation );
        }
        try
        {
            URI.create( uriString );
        }
        catch ( final IllegalArgumentException e )
        {
            final ErrorInformation errorInformation = new ErrorInformation(
                    PwmError.CONFIG_FORMAT_ERROR, "Setting "
                    + pwmSetting.toMenuLocationDebug( profile, null ) + " action URL has an invalid URL syntax" );
            throw new PwmOperationalException( errorInformation );
        }
        return uriString;
    }

    void store(
            final List<X509Certificate> certs,
            final StoredConfigurationModifier storedConfiguration,
            final PwmSetting pwmSetting,
            final String profile,
            final String extraData,
            final UserIdentity userIdentity
    )
            throws PwmOperationalException, PwmUnrecoverableException
    {
        final Map<String, Integer> extraDataMap = JsonUtil.deserialize( extraData, new TypeToken<Map<String, Integer>>()
        {
        } );

        final ActionValue actionValue = ( ActionValue ) storedConfiguration.newStoredConfiguration().readSetting( pwmSetting, profile );
        final List<ActionConfiguration> actionConfigurations = actionValue.toNativeObject();
        final ActionConfiguration action = actionConfigurations.get( extraDataMap.get( KEY_ITERATION ) );
        final ActionConfiguration.WebAction webAction = action.getWebActions().get( extraDataMap.get( KEY_WEB_ACTION_ITERATION ) );

        final ActionConfiguration.WebAction clonedAction = webAction.toBuilder()
                .certificates( certs )
                .build();

        action.getWebActions().set( extraDataMap.get( KEY_WEB_ACTION_ITERATION ), clonedAction );

        final ActionValue newActionValue = new ActionValue( actionConfigurations );
        storedConfiguration.writeSetting( pwmSetting, profile, newActionValue, userIdentity );
    }
}
