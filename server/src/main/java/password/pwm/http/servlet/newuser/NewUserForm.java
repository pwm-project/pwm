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

package password.pwm.http.servlet.newuser;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Getter;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.util.PasswordData;

import java.io.Serializable;
import java.util.Map;

@Getter
@AllArgsConstructor
public class NewUserForm implements Serializable
{

    @SerializedName( "f" )
    private final Map<String, String> formData;

    @SerializedName( "p" )
    private final PasswordData newUserPassword;

    @SerializedName( "c" )
    private final PasswordData confirmPassword;

    public boolean isConsistentWith( final NewUserForm otherForm ) throws PwmUnrecoverableException
    {
        if ( otherForm == null )
        {
            return false;
        }

        if ( newUserPassword != null && otherForm.newUserPassword == null || newUserPassword == null && otherForm.newUserPassword != null )
        {
            return false;
        }

        if ( newUserPassword != null && otherForm.newUserPassword != null && !newUserPassword.getStringValue().equals( otherForm.newUserPassword.getStringValue() ) )
        {
            return false;
        }

        for ( final Map.Entry<String, String> entry : formData.entrySet() )
        {
            final String formKey = entry.getKey();
            final String value = entry.getValue();
            final String otherValue = otherForm.formData.get( formKey );
            if ( value != null && !value.equals( otherValue ) )
            {
                return false;
            }
        }

        return true;
    }
}
