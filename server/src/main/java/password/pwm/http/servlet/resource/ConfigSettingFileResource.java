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

package password.pwm.http.servlet.resource;

import password.pwm.PwmConstants;
import password.pwm.config.Configuration;
import password.pwm.config.PwmSetting;
import password.pwm.util.java.StringUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ConfigSettingFileResource implements FileResource
{
    private final String bodyText;
    private final String requestURI;

    public ConfigSettingFileResource( final PwmSetting pwmSetting, final Configuration configuration, final String requestURI )
    {
        this.bodyText = configuration.readSettingAsString( pwmSetting );
        this.requestURI = requestURI;
    }


    @Override
    public InputStream getInputStream()
            throws IOException
    {
        return new ByteArrayInputStream( bodyText.getBytes( PwmConstants.DEFAULT_CHARSET ) );
    }

    @Override
    public long length()
    {
        return bodyText.length();
    }

    @Override
    public long lastModified()
    {
        return 0;
    }

    @Override
    public boolean exists()
    {
        return !StringUtil.isEmpty( bodyText );
    }

    @Override
    public String getName()
    {
        return requestURI;
    }
}
