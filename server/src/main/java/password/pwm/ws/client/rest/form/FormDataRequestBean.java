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

package password.pwm.ws.client.rest.form;

import lombok.Builder;
import lombok.Getter;
import password.pwm.config.value.data.FormConfiguration;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class FormDataRequestBean implements Serializable
{

    @Getter
    @Builder
    public static class FormInfo implements Serializable
    {
        private FormType module;
        private String moduleProfileID;
        private Mode mode;
        private String sessionID;
    }

    private FormInfo formInfo;
    private String userDN;
    private String ldapProfileID;

    public enum FormType
    {
        NewUser,
    }

    public enum Mode
    {
        edit,
        read,
        verify,
        write,
    }

    private Map<String, String> formValues;
    private List<FormConfiguration> formConfigurations;
}
