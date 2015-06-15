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
