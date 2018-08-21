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

package password.pwm.http.bean;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import password.pwm.bean.TokenDestinationItem;
import password.pwm.bean.UserIdentity;
import password.pwm.config.option.SessionBeanMode;

import java.util.Collections;
import java.util.Set;

@Data
@EqualsAndHashCode( callSuper = false )
public class ActivateUserBean extends PwmSessionBean
{
    @SerializedName( "tp" )
    private boolean tokenPassed;

    @SerializedName( "ap" )
    private boolean agreementPassed;

    @SerializedName( "v" )
    private boolean formValidated;

    @SerializedName( "u" )
    private UserIdentity userIdentity;

    @SerializedName( "ts" )
    private boolean tokenSent;

    @SerializedName( "td" )
    private TokenDestinationItem tokenDestination;


    public Type getType( )
    {
        return Type.PUBLIC;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.singleton( SessionBeanMode.LOCAL );
    }
}
