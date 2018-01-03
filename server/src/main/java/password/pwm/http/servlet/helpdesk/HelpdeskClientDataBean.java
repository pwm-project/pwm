/*
 * Password Management Servlets (PWM)
 * http://www.pwm-project.org
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2017 The PWM Project
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

package password.pwm.http.servlet.helpdesk;

import lombok.Data;
import password.pwm.config.option.HelpdeskClearResponseMode;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.option.IdentityVerificationMethod;
import password.pwm.config.option.MessageSendMethod;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@SuppressWarnings( "checkstyle:MemberName" )
public class HelpdeskClientDataBean implements Serializable
{
    private Map<String, String> helpdesk_search_columns = new HashMap<>();
    private boolean helpdesk_setting_maskPasswords;
    private HelpdeskClearResponseMode helpdesk_setting_clearResponses;
    private HelpdeskUIMode helpdesk_setting_PwUiMode;
    private MessageSendMethod helpdesk_setting_tokenSendMethod;
    private Map<String, ActionInformation> actions = new HashMap<>();
    private Map<String, Collection<IdentityVerificationMethod>> verificationMethods = new HashMap<>();
    private List<FormInformation> verificationForm;

    @Data
    public static class ActionInformation implements Serializable
    {
        private String name;
        private String description;
    }

    @Data
    public static class FormInformation implements Serializable
    {
        private String name;
        private String label;
    }
}
