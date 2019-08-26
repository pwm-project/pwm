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

package password.pwm.http.bean;

import password.pwm.config.option.SessionBeanMode;
import password.pwm.config.stored.StoredConfigurationImpl;

import java.util.Collections;
import java.util.Set;

public class ConfigManagerBean extends PwmSessionBean
{
    private transient StoredConfigurationImpl storedConfiguration;
    private boolean passwordVerified;
    private boolean configUnlockedWarningShown;

    private String prePasswordEntryUrl;

    public ConfigManagerBean( )
    {
    }

    public Type getType( )
    {
        return Type.AUTHENTICATED;
    }


    public StoredConfigurationImpl getStoredConfiguration( )
    {
        return storedConfiguration;
    }

    public void setConfiguration( final StoredConfigurationImpl storedConfiguration )
    {
        this.storedConfiguration = storedConfiguration;
    }

    public boolean isPasswordVerified( )
    {
        return passwordVerified;
    }

    public void setPasswordVerified( final boolean passwordVerified )
    {
        this.passwordVerified = passwordVerified;
    }

    public String getPrePasswordEntryUrl( )
    {
        return prePasswordEntryUrl;
    }

    public void setPrePasswordEntryUrl( final String prePasswordEntryUrl )
    {
        this.prePasswordEntryUrl = prePasswordEntryUrl;
    }

    public boolean isConfigUnlockedWarningShown( )
    {
        return configUnlockedWarningShown;
    }

    public void setConfigUnlockedWarningShown( final boolean configUnlockedWarningShown )
    {
        this.configUnlockedWarningShown = configUnlockedWarningShown;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.singleton( SessionBeanMode.LOCAL );
    }

}
