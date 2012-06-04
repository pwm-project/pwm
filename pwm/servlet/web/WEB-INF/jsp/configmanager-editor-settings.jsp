<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
  ~
  ~ This program is free software; you can redistribute it and/or modify
  ~ it under the terms of the GNU General Public License as published by
  ~ the Free Software Foundation; either version 2 of the License, or
  ~ (at your option) any later version.
  ~
  ~ This program is distributed in the hope that it will be useful,
  ~ but WITHOUT ANY WARRANTY; without even the implied warranty of
  ~ MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  ~ GNU General Public License for more details.
  ~
  ~ You should have received a copy of the GNU General Public License
  ~ along with this program; if not, write to the Free Software
  ~ Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
  --%>

<%@ page import="password.pwm.bean.ConfigManagerBean" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.StoredConfiguration" %>
<%@ page import="password.pwm.servlet.ConfigManagerServlet" %>
<%@ page import="java.util.Locale" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final Locale locale = password.pwm.PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean(); %>
<% final password.pwm.config.PwmSetting.Level level = configManagerBean.getLevel(); %>
<% final boolean showDesc = configManagerBean.isShowDescr(); %>
<% final password.pwm.config.PwmSetting.Category category = configManagerBean.getCategory(); %>
<% final boolean hasNotes = configManagerBean.getConfiguration().readProperty(StoredConfiguration.PROPERTY_KEY_NOTES) != null; %>

<h1 style="text-align:center;"><%=category.getLabel(locale)%>
</h1>
<% if (showDesc) { %><p><%= category.getDescription(locale)%></p><% } %>
<% if (category.settingsForCategory(PwmSetting.Level.ADVANCED).size() > 0 && !level.equals(PwmSetting.Level.ADVANCED)) { %>
<p>
    <img src="<%=request.getContextPath()%>/resources/warning.gif" alt="warning"/>
    <span style="font-weight: bold;">Some settings are not displayed.</span>&nbsp;&nbsp;Select "Advanced Options" from the View menu to show all settings.
</p>
<% } %>
<% if (hasNotes) { %>
<p id="show-notes-warning">
    <img src="<%=request.getContextPath()%>/resources/warning.gif" alt="warning"/>
    <span style="font-weight: bold;">Notes for this configuration exist.</span>&nbsp;&nbsp;Select "Show Notes" from the View menu to see configuration notes.
</p>
<script type="text/javascript">
    if (getCookie("seen-notes") == "true") {
        getObject('show-notes-warning').style.display = 'none';
    }
</script>
<% } %>
<% for (final PwmSetting loopSetting : PwmSetting.values()) { %>
<% final boolean showSetting = loopSetting.getCategory() == category && ((level == PwmSetting.Level.ADVANCED || loopSetting.getLevel() == PwmSetting.Level.BASIC) || !configManagerBean.getConfiguration().isDefaultValue(loopSetting)); %>
<% if (showSetting) { %>
<div id="titlePane_<%=loopSetting.getKey()%>" style="margin-top:0; padding-top:0; border-top:0">
<div class="message message-info" style="width: 580px; font-weight: bolder; font-family: Trebuchet MS,sans-serif">
    <label for="value_<%=loopSetting.getKey()%>"><%=loopSetting.getLabel(locale)%>
        <% if (loopSetting.getLevel() == PwmSetting.Level.ADVANCED) { %>
        (Advanced)
        <% }%>
    </label>
    <img src="<%=request.getContextPath()%>/resources/reset.gif" alt="Reset" title="Reset to default value"
         id="resetButton-<%=loopSetting.getKey()%>"
         style="visibility:hidden; vertical-align:bottom; float: right"
         onclick="handleResetClick('<%=loopSetting.getKey()%>')"/>
    <script type="text/javascript">
        require(["dijit/Tooltip"],function(){
            new dijit.Tooltip({
                connectId: ["resetButton-<%=loopSetting.getKey()%>"],
                label: 'Return this setting to its default value.'
            });
        });
    </script>
</div>
<div class="message message-info" style="width: 580px; background: white;">
<% if (showDesc) { %>
<%= loopSetting.getDescription(locale) %>
<br/>
<% } %>
<% if (loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_STRING || loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_TEXT_AREA) { %>
<table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0" width="500">
    <tr style="border-width:0">
        <td style="border-width:0"><input type="text" disabled="disabled" value="[Loading...]"
                                          style="width: 600px"/></td>
    </tr>
</table>
<script type="text/javascript">
    initLocaleTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>', '<%=loopSetting.getSyntax()%>');
</script>
<% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.STRING_ARRAY) { %>
<table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
</table>
<script type="text/javascript">
    initMultiTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>');
</script>
<% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_STRING_ARRAY) { %>
<table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
    <tr>
        <td><input type="text" disabled="disabled" value="[Loading...]" style="width: 600px"/></td>
    </tr>
</table>
<script type="text/javascript">
    initMultiLocaleTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>');
</script>
<% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.BOOLEAN) { %>
<br/>Current Value:
<input type="hidden" id="value_<%=loopSetting.getKey()%>" value="false"/>
<button id="button_<%=loopSetting.getKey()%>" type="button">
    [Loading...]
</button>
<script type="text/javascript">
    require(["dijit","dijit/form/Button","dijit/registry"],function(dijit){
        new dijit.form.Button({
            disabled: true,
            onClick: function() {
                toggleBooleanSetting('<%=loopSetting.getKey()%>');
                writeSetting('<%=loopSetting.getKey()%>', getObject('value_' + '<%=loopSetting.getKey()%>').value);
            }
        }, "button_<%=loopSetting.getKey()%>");
        readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
            var valueElement = getObject('value_' + '<%=loopSetting.getKey()%>');
            var buttonElement = getObject('button_' + '<%=loopSetting.getKey()%>');
            if (dataValue == 'true') {
                valueElement.value = 'true';
                buttonElement.innerHTML = '\u00A0\u00A0\u00A0True\u00A0\u00A0\u00A0';
            } else {
                valueElement.value = 'false';
                buttonElement.innerHTML = '\u00A0\u00A0\u00A0False\u00A0\u00A0\u00A0';
            }
            buttonElement.disabled = false;
            dijit.byId('button_<%=loopSetting.getKey()%>').setDisabled(false);
        });
    });
</script>
<% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.SELECT) { %>
<select id="select_<%=loopSetting.getKey()%>" disabled="true">
    <% for (final String loopValue : loopSetting.getOptions().keySet()) { %>
    <option value="<%=loopValue%>"><%=loopSetting.getOptions().get(loopValue)%></option>
    <% } %>
</select>
<script type="text/javascript">
    require(["dijit","dijit/form/FilteringSelect","dijit/registry"],function(dijit){
        new dijit.form.FilteringSelect({
            disabled: true,
            onChange: function() {
                writeSetting('<%=loopSetting.getKey()%>',this.value);
            }
        }, "select_<%=loopSetting.getKey()%>");
        readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
            var selectElement = getObject('select_' + '<%=loopSetting.getKey()%>');
            selectElement.disabled = false;
            dijit.byId('select_<%=loopSetting.getKey()%>').setDisabled(false);
            dijit.byId('select_<%=loopSetting.getKey()%>').set('value',dataValue);
        });
    });
</script>
<% } else { %>
<% if (loopSetting.getSyntax() == PwmSetting.Syntax.TEXT_AREA) { %>
<textarea id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>">&nbsp;</textarea>
<script type="text/javascript">
    require(["dijit/form/Textarea"],function(){
        new dijit.form.Textarea({
            regExp: "<%=loopSetting.getRegExPattern().pattern()%>",
            required: <%=loopSetting.isRequired()%>,
            invalidMessage: "The value does not have the correct format.",
            style: "width: 450px",
            onChange: function() {
                writeSetting('<%=loopSetting.getKey()%>', this.value);
            },
            value: "[Loading..]",
            disabled: true
        }, "value_<%=loopSetting.getKey()%>");
        readInitialTextBasedValue('<%=loopSetting.getKey()%>');
    });
</script>
<% } if (loopSetting.getSyntax() == PwmSetting.Syntax.STRING) { %>
<input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"/>
<script type="text/javascript">
    require(["dijit/form/ValidationTextBox"],function(){
        new dijit.form.ValidationTextBox({
            regExp: "<%=loopSetting.getRegExPattern().pattern()%>",
            required: <%=loopSetting.isRequired()%>,
            invalidMessage: "The value does not have the correct format.",
            style: "width: 450px",
            onChange: function() {
                writeSetting('<%=loopSetting.getKey()%>', this.value);
            },
            value: "[Loading..]",
            disabled: true
        }, "value_<%=loopSetting.getKey()%>");
        readInitialTextBasedValue('<%=loopSetting.getKey()%>');
    });
</script>
<% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.NUMERIC) { %>
<input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"/>
<script type="text/javascript">
    require(["dijit/form/NumberSpinner"],function(){
        new dijit.form.NumberSpinner({
            regExp: "<%=loopSetting.getRegExPattern().pattern()%>",
            required: <%=loopSetting.isRequired()%>,
            invalidMessage: "The value does not have the correct format.",
            style: "width: 100px",
            onChange: function() {
                writeSetting('<%=loopSetting.getKey()%>', this.value);
            },
            value: "[Loading..]",
            disabled: true
        }, "value_<%=loopSetting.getKey()%>");
        readInitialTextBasedValue('<%=loopSetting.getKey()%>');
    });
</script>
<% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.PASSWORD) { %>
<div id="password_wrapper_<%=loopSetting.getKey()%>">
    <div style="float: left">
        <table style="width: 455px">
            <tr>
                <td style="text-align:right; white-space:nowrap;">
                    <label for="value_<%=loopSetting.getKey()%>">Password</label>
                </td>
                <td>
                    <input type="password" id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>" style="width: 400px"/>
                </td>
            </tr>
            <tr>
                <td style="text-align:right; white-space:nowrap;">
                    <label for="value_verify_<%=loopSetting.getKey()%>">Verify Password</label>
                </td>
                <td>
                    <input type="password" id="value_verify_<%=loopSetting.getKey()%>" name="setting_verify_<%=loopSetting.getKey()%>" style="width: 400px"/>
                </td>
            </tr>
        </table>
    </div>
</div>
<script type="text/javascript">
    require(["dojo","dijit","dijit/form/ValidationTextBox","dijit/registry"],function(dojo,dijit){
        new dijit.form.ValidationTextBox({
            required: <%=loopSetting.isRequired()%>,
            invalidMessage: "The password is not valid.",
            style: "width: 400px",
            value: "<%=ConfigManagerServlet.DEFAULT_PW%>",
            type: 'password',
            onKeyDown: function() {
                var currentValue = dojo.byId('value_<%=loopSetting.getKey()%>').value;
                if (currentValue == '<%=ConfigManagerServlet.DEFAULT_PW%>') {
                    dojo.byId('value_<%=loopSetting.getKey()%>').value = '';
                }
                dojo.byId('value_verify_<%=loopSetting.getKey()%>').value = '';
                dijit.byId('value_verify_<%=loopSetting.getKey()%>').value = '';
            }
        }, "value_<%=loopSetting.getKey()%>");
        new dijit.form.ValidationTextBox({
            required: true,
            invalidMessage: "The password does not match.",
            style: "width: 400px",
            value: "<%=ConfigManagerServlet.DEFAULT_PW%>",
            type: 'password',
            onChange: function() {
                var v = dijit.byId('value_verify_<%=loopSetting.getKey()%>');
                if (v.isValid()) {
                    writeSetting('<%=loopSetting.getKey()%>', this.value);
                }
            },
            validator: function() {
                var password = dojo.byId('value_<%=loopSetting.getKey()%>').value;
                var verifyPassword = dojo.byId('value_verify_<%=loopSetting.getKey()%>').value;
                return password == verifyPassword;
            }
        }, "value_verify_<%=loopSetting.getKey()%>");
        readInitialTextBasedValue('<%=loopSetting.getKey()%>');
    });
</script>
<br class="clear"/>
<% } %>
<% } %>
</div>
</div>
<br/>
<% } %>
<% } %>
