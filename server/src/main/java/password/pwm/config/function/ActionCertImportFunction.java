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

package password.pwm.config.function;

import com.google.gson.reflect.TypeToken;
import password.pwm.bean.UserIdentity;
import password.pwm.config.PwmSetting;
import password.pwm.config.stored.StoredConfigurationImpl;
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
    String getUri( final StoredConfigurationImpl storedConfiguration, final PwmSetting pwmSetting, final String profile, final String extraData ) throws PwmOperationalException
    {
        final Map<String, Integer> extraDataMap = JsonUtil.deserialize( extraData, new TypeToken<Map<String, Integer>>()
        {
        } );

        final ActionValue actionValue = ( ActionValue ) storedConfiguration.readSetting( pwmSetting, profile );
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
        catch ( IllegalArgumentException e )
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
            final StoredConfigurationImpl storedConfiguration,
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

        final ActionValue actionValue = ( ActionValue ) storedConfiguration.readSetting( pwmSetting, profile );
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
