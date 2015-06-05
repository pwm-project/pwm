package password.pwm.ws.client.rest.naaf;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

class NAAFLoginResponseBean implements Serializable {
    private String msg;
    private String current_method;
    private String logon_process_id;
    private List<String> completed_methods;
    private List<String> plugins;
    private STATUS status;
    private List<NAAFChainBean> chains;
    private Map<String,String> questions;

    enum STATUS {
        OK,
        MORE_DATA,
        NEXT,
        FAILED,
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getCurrent_method() {
        return current_method;
    }

    public void setCurrent_method(String current_method) {
        this.current_method = current_method;
    }

    public String getLogon_process_id() {
        return logon_process_id;
    }

    public void setLogon_process_id(String logon_process_id) {
        this.logon_process_id = logon_process_id;
    }

    public List<String> getCompleted_methods() {
        return completed_methods;
    }

    public void setCompleted_methods(List<String> completed_methods) {
        this.completed_methods = completed_methods;
    }

    public List<String> getPlugins() {
        return plugins;
    }

    public void setPlugins(List<String> plugins) {
        this.plugins = plugins;
    }

    public STATUS getStatus() {
        return status;
    }

    public void setStatus(STATUS status) {
        this.status = status;
    }

    public List<NAAFChainBean> getChains() {
        return chains;
    }

    public void setChains(List<NAAFChainBean> chains) {
        this.chains = chains;
    }

    public Map<String, String> getQuestions() {
        return questions;
    }

    public void setQuestions(Map<String, String> questions) {
        this.questions = questions;
    }
}
