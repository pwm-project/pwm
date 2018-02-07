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

package password.pwm.util;

import password.pwm.AppProperty;
import password.pwm.config.Configuration;
import password.pwm.util.java.StringUtil;

public class ValueObfuscator
{
    private final Configuration configuration;

    public ValueObfuscator( final Configuration configuration )
    {
        this.configuration = configuration;
    }

    public String maskEmail( final String email )
    {
        if ( StringUtil.isEmpty( email ) )
        {
            return "";
        }

        final String regex = configuration.readAppProperty( AppProperty.TOKEN_MASK_EMAIL_REGEX );
        final String replace = configuration.readAppProperty( AppProperty.TOKEN_MASK_EMAIL_REPLACE );
        return email.replaceAll( regex, replace );
    }

    public String maskPhone( final String phone )
    {
        if ( StringUtil.isEmpty( phone ) )
        {
            return "";
        }

        final String regex = configuration.readAppProperty( AppProperty.TOKEN_MASK_SMS_REGEX );
        final String replace = configuration.readAppProperty( AppProperty.TOKEN_MASK_SMS_REPLACE );
        return phone.replaceAll( regex, replace );
    }
}



