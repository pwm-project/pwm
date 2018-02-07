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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import password.pwm.bean.TokenVerificationProgress;
import password.pwm.config.option.SessionBeanMode;
import password.pwm.http.servlet.newuser.NewUserForm;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
public class NewUserBean extends PwmSessionBean
{
    @SerializedName( "p" )
    private String profileID;

    @SerializedName( "f" )
    private NewUserForm newUserForm = new NewUserForm( new HashMap<>(), null, null );

    @SerializedName( "r" )
    private Map<String, String> remoteInputData;

    @SerializedName( "ap" )
    private boolean agreementPassed;

    @SerializedName( "fp" )
    private boolean formPassed;

    @SerializedName( "t" )
    private Instant createStartTime;

    @SerializedName( "u" )
    private boolean urlSpecifiedProfile;

    @SerializedName( "v" )
    private final TokenVerificationProgress tokenVerificationProgress = new TokenVerificationProgress();

    @Override
    public Type getType( )
    {
        return Type.PUBLIC;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.unmodifiableSet( new HashSet<>( Arrays.asList( SessionBeanMode.LOCAL, SessionBeanMode.CRYPTCOOKIE ) ) );
    }


    public TokenVerificationProgress getTokenVerificationProgress( )
    {
        return tokenVerificationProgress;
    }
}
