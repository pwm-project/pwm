/*
 * Password Management Servlets (PWM)
 * http://code.google.com/p/pwm/
 *
 * Copyright (c) 2006-2009 Novell, Inc.
 * Copyright (c) 2009-2015 The PWM Project
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

import password.pwm.config.option.HelpdeskClearResponseMode;
import password.pwm.config.option.HelpdeskUIMode;
import password.pwm.config.option.MessageSendMethod;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class HelpdeskClientDataBean implements Serializable {
    private Map<String,String> helpdesk_search_columns = new HashMap<>();
    private boolean helpdesk_setting_maskPasswords;
    private HelpdeskClearResponseMode helpdesk_setting_clearResponses;
    private HelpdeskUIMode helpdesk_setting_PwUiMode;
    private MessageSendMethod helpdesk_setting_tokenSendMethod;
    private Map<String,Map<String,String>> actions = new HashMap<>();

    public Map<String, String> getHelpdesk_search_columns() {
        return helpdesk_search_columns;
    }

    public void setHelpdesk_search_columns(Map<String, String> helpdesk_search_columns) {
        this.helpdesk_search_columns = helpdesk_search_columns;
    }

    public boolean isHelpdesk_setting_maskPasswords() {
        return helpdesk_setting_maskPasswords;
    }

    public void setHelpdesk_setting_maskPasswords(boolean helpdesk_setting_maskPasswords) {
        this.helpdesk_setting_maskPasswords = helpdesk_setting_maskPasswords;
    }

    public HelpdeskClearResponseMode getHelpdesk_setting_clearResponses() {
        return helpdesk_setting_clearResponses;
    }

    public void setHelpdesk_setting_clearResponses(HelpdeskClearResponseMode helpdesk_setting_clearResponses) {
        this.helpdesk_setting_clearResponses = helpdesk_setting_clearResponses;
    }

    public HelpdeskUIMode getHelpdesk_setting_PwUiMode() {
        return helpdesk_setting_PwUiMode;
    }

    public void setHelpdesk_setting_PwUiMode(HelpdeskUIMode helpdesk_setting_PwUiMode) {
        this.helpdesk_setting_PwUiMode = helpdesk_setting_PwUiMode;
    }

    public MessageSendMethod getHelpdesk_setting_tokenSendMethod() {
        return helpdesk_setting_tokenSendMethod;
    }

    public void setHelpdesk_setting_tokenSendMethod(MessageSendMethod helpdesk_setting_tokenSendMethod) {
        this.helpdesk_setting_tokenSendMethod = helpdesk_setting_tokenSendMethod;
    }

    public Map<String, Map<String, String>> getActions() {
        return actions;
    }

    public void setActions(Map<String, Map<String, String>> actions) {
        this.actions = actions;
    }
}
