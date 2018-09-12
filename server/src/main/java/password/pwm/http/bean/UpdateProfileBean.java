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
import password.pwm.config.option.SessionBeanMode;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Data
@EqualsAndHashCode( callSuper = false )
public class UpdateProfileBean extends PwmSessionBean
{

    @SerializedName( "a" )
    private boolean agreementPassed;

    @SerializedName( "p" )
    private boolean confirmationPassed;

    @SerializedName( "s" )
    private boolean formSubmitted;

    @SerializedName( "l" )
    private boolean formLdapLoaded;

    @SerializedName( "f" )
    private Map<String, String> formData = new LinkedHashMap<>();

    @SerializedName( "ct" )
    private String currentTokenField;

    @SerializedName( "ft" )
    private Set<String> completedTokenFields = new HashSet<>();

    @SerializedName( "ts" )
    private boolean tokenSent;

    public Type getType( )
    {
        return Type.AUTHENTICATED;
    }

    @Override
    public Set<SessionBeanMode> supportedModes( )
    {
        return Collections.unmodifiableSet( new HashSet<>( Arrays.asList( SessionBeanMode.LOCAL ) ) );
    }
}
