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
<%@ page import="java.util.Locale" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final Locale locale = password.pwm.PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean(); %>
<% final password.pwm.config.PwmSetting.Level level = configManagerBean.getLevel(); %>
<% final boolean showDesc = configManagerBean.isShowDescr(); %>
<% final password.pwm.config.PwmSetting.Category category = configManagerBean.getCategory(); %>
<% final boolean hasNotes = configManagerBean.getConfiguration().readProperty(StoredConfiguration.PROPERTY_KEY_NOTES) != null && configManagerBean.getConfiguration().readProperty(StoredConfiguration.PROPERTY_KEY_NOTES).length() > 0; %>
<h1 style="color:#435174; text-align: center;">
    <%=category.getGroup() == 0 ? "Settings - " : "Modules - "%>
    <%=category.getLabel(locale)%>
</h1>
<% if (showDesc) { %><p><%= category.getDescription(locale)%></p><% } %>
<% if (category.settingsForCategory(PwmSetting.Level.ADVANCED).size() > 0 && !level.equals(PwmSetting.Level.ADVANCED)) { %>
<%--
<div style="font-size: smaller">
    <img src="<%=request.getContextPath()%>/resources/warning.gif" alt="warning"/>
    <span style="font-weight: bold;">Some settings are not displayed.</span>&nbsp;&nbsp;Select "Advanced Options" from the View menu to show all settings.
</div>
--%>
<% } %>
<br/>
<% for (final PwmSetting loopSetting : PwmSetting.values()) { %>
<% final boolean showSetting = loopSetting.getCategory() == category && ((level == PwmSetting.Level.ADVANCED || loopSetting.getLevel() == PwmSetting.Level.BASIC) || !configManagerBean.getConfiguration().isDefaultValue(loopSetting)); %>
<% if (showSetting) { %>
<div id="outline_<%=loopSetting.getKey()%>" style="background-color: #F5F5F5; border-radius: 5px; box-shadow: 2px 2px 1px 1px #bfbfbf;}">
<%
    StringBuilder title = new StringBuilder();
    title.append(loopSetting.getLabel(locale));
    if (loopSetting.getLevel() == PwmSetting.Level.ADVANCED) {
        title.append(" (Advanced)");
    }
%>
<img src="<%=request.getContextPath()%><pwm:url url="/resources/reset.png"/>" alt="Reset" title="Reset to default value"
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
<div data-dojo-type="dijit.TitlePane" title="<%=title%>" data-dojo-props="open:<%=showDesc%>" style="width:580px" id="title_<%=loopSetting.getKey()%>">
    <div><%=loopSetting.getDescription(locale)%></div>
</div>
<div id="titlePane_<%=loopSetting.getKey()%>" style="padding-left: 5px; padding-top: 5px">

    <% if (loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_STRING || loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_TEXT_AREA) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0" width="500">
        <tr style="border-width:0">
            <td style="border-width:0"><input type="text" disabled="disabled" value="[Loading...]"/></td>
        </tr>
    </table>
    <script type="text/javascript">
        LocaleTableHandler.initLocaleTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>', '<%=loopSetting.getSyntax()%>');
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.STRING_ARRAY) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
    </table>
    <script type="text/javascript">
        MultiTableHandler.initMultiTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>');
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_STRING_ARRAY) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
        <tr>
            <td><input type="text" disabled="disabled" value="[Loading...]"/></td>
        </tr>
    </table>
    <script type="text/javascript">
        MultiLocaleTableHandler.initMultiLocaleTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>');
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.FORM) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border:0 none">
    </table>
    <script type="text/javascript">
        FormTableHandler.init('<%=loopSetting.getKey()%>');
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.BOOLEAN) { %>
    <input type="hidden" id="value_<%=loopSetting.getKey()%>" value="false"/>
    <button id="button_<%=loopSetting.getKey()%>" type="button">
        [Loading...]
    </button>
    <script type="text/javascript">
        require(["dijit","dijit/registry","dijit/form/Button"],function(dijit,registry){
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
                registry.byId('button_<%=loopSetting.getKey()%>').setDisabled(false);
            });
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.SELECT) { %>
    <select id="setting_<%=loopSetting.getKey()%>" disabled="true" data-dojo-type="dijit.form.FilteringSelect"
            onchange="writeSetting('<%=loopSetting.getKey()%>',this.value);" style="min-width: 300px">
        <% for (final String loopValue : loopSetting.getOptions().keySet()) { %>
        <option value="<%=loopValue%>"><%=loopSetting.getOptions().get(loopValue)%></option>
        <% } %>
    </select>
    <script type="text/javascript">
        require(["dijit","dijit/registry","dijit/form/FilteringSelect"],function(dijit,registry){
            readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
                var selectElement = getObject('setting_' + '<%=loopSetting.getKey()%>');
                selectElement.disabled = false;
                registry.byId('setting_<%=loopSetting.getKey()%>').setDisabled(false);
                registry.byId('setting_<%=loopSetting.getKey()%>').set('value',dataValue);
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
    <button data-dojo-type="dijit.form.Button" onclick="ChangePasswordHandler.changePasswordPopup('<%=loopSetting.getLabel(locale)%>','<%=loopSetting.getKey()%>')">Set Password</button>
    <button id="clearButton_<%=loopSetting.getKey()%>" data-dojo-type="dijit.form.Button" onclick="if (confirm('Clear password for setting <%=loopSetting.getLabel(locale)%>?')) {resetSetting('<%=loopSetting.getKey()%>')}">Clear Password</button>
    <% } %>
    <% } %>
</div>
<br/>
</div>
<br/>
<% } %>
<% } %>
<script type="text/javascript">
    require(["dojo/parser",
        "dijit/form/Button",
        "dijit/form/FilteringSelect",
        "dijit/form/Textarea",
        "dijit/layout/BorderContainer",
        "dijit/layout/ContentPane",
        "dijit/TitlePane",
        "dojo/domReady!"],function(dojoParser){
        dojoParser.parse();
        setTimeout(function(){
            closeWaitDialog();

            <% if (hasNotes) { %>
            if (getCookie("seen-notes") == "false") {
                showConfigurationNotes();
                setCookie("seen-notes","true",30 * 60);
            }
            <% } %>
        },500);
    });
</script>
