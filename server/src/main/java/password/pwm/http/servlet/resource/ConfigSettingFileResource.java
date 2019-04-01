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
