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

package password.pwm.bean;

import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.config.Configuration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;
import password.pwm.util.ValueObfuscator;
import password.pwm.util.java.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
@AllArgsConstructor
public class TokenDestinationItem
{
    private String id;
    private String display;
    private String value;
    private Type type;

    public enum Type
    {
        sms,
        email,
    }

    public static List<TokenDestinationItem> allFromConfig( final Configuration configuration, final UserInfo userInfo )
            throws PwmUnrecoverableException
    {
        final ValueObfuscator valueObfuscator = new ValueObfuscator( configuration );
        int counter = 0;

        final List<TokenDestinationItem> results = new ArrayList<>();

        {
            final String smsValue = userInfo.getUserSmsNumber();
            if ( !StringUtil.isEmpty( smsValue ) )
            {
                results.add( new TokenDestinationItem(
                        String.valueOf( ++counter ),
                        valueObfuscator.maskPhone( smsValue ),
                        smsValue,
                        Type.sms
                ) );
            }
        }

        {
            final String emailValue = userInfo.getUserEmailAddress();
            if ( !StringUtil.isEmpty( emailValue ) )
            {
                results.add( new TokenDestinationItem(
                        String.valueOf( ++counter ),
                        valueObfuscator.maskEmail( emailValue ),
                        emailValue,
                        Type.email
                ) );
            }
        }

        return Collections.unmodifiableList( results );
    }
}
