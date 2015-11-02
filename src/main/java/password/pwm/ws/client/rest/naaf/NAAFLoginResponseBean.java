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
