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

package password.pwm.util.form;

import password.pwm.bean.SessionLabel;
import password.pwm.config.value.data.FormConfiguration;
import password.pwm.error.PwmUnrecoverableException;
import password.pwm.ldap.UserInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FormState
{
    private final List<FormConfiguration> formConfigurations;
    private final Map<String, String> values;

    private FormState( final List<FormConfiguration> formConfigurations, final Map<String, String> values )
    {
        this.formConfigurations = Collections.unmodifiableList( formConfigurations );
        this.values = values;
    }

    public static FormState initialize( final List<FormConfiguration> formConfigurations )
    {
        final FormState formState = new FormState(
                Collections.unmodifiableList( formConfigurations ),
                new HashMap<>()
        );
        return formState;
    }

    public void readDataFromSources( final SessionLabel sessionLabel, final UserInfo userInfo )
            throws PwmUnrecoverableException
    {
        FormUtility.populateFormMapFromLdap( formConfigurations, sessionLabel, userInfo );
    }
}
