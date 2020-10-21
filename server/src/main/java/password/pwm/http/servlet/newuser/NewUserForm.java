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
