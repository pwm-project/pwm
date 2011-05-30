<%--
~ Password Management Servlets (PWM)
~ http://code.google.com/p/pwm/
~
~ Copyright (c) 2006-2009 Novell, Inc.
~ Copyright (c) 2009-2010 The PWM Project
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

<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final password.pwm.config.PwmSetting.Level level = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().getLevel(); %>
<% final boolean showDesc = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().isShowDescr(); %>
<% final password.pwm.config.PwmSetting.Category category = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean().getCategory(); %>
<h1 style="text-align:center;"><%=category.getLabel(request.getLocale())%>
</h1>
<% if (showDesc) { %><span><%= category.getDescription(request.getLocale())%></span><% } %>
<% for (final PwmSetting loopSetting : PwmSetting.values()) { %>
<% if (loopSetting.getCategory() == category && (level == PwmSetting.Level.ADVANCED || loopSetting.getLevel() == PwmSetting.Level.BASIC)) { %>
<script type="text/javascript">showError('');</script>
<div id="titlePane_<%=loopSetting.getKey()%>" style="margin-top:0; padding-top:0; border-top:0">
    <h2><label for="value_<%=loopSetting.getKey()%>"><%=loopSetting.getLabel(request.getLocale())%></label></h2>
    <% if (showDesc) { %>
    <%= loopSetting.getDescription(request.getLocale()) %>
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
    <input type="hidden" id="value_<%=loopSetting.getKey()%>" value="false"/>
    <button id="button_<%=loopSetting.getKey()%>" type="button">
        [Loading...]
    </button>
    <img src="<%=request.getContextPath()%>/resources/reset.gif" alt="Reset" title="Reset to default value"
         id="resetButton-<%=loopSetting.getKey()%>"
         style="visibility:hidden; vertical-align:middle;"
         onclick="handleResetClick('<%=loopSetting.getKey()%>')"/>
    <script type="text/javascript">
        dojo.require("dijit.form.Button");
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
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.SELECT) { %>
    <select id="select_<%=loopSetting.getKey()%>" disabled="true">
        <% for (final String loopValue : loopSetting.getOptions().keySet()) { %>
        <option value="<%=loopValue%>"><%=loopSetting.getOptions().get(loopValue)%></option>
        <% } %>
    </select>
    <img src="<%=request.getContextPath()%>/resources/reset.gif" alt="Reset" title="Reset to default value"
         id="resetButton-<%=loopSetting.getKey()%>"
         style="visibility:hidden; vertical-align:middle;"
         onclick="handleResetClick('<%=loopSetting.getKey()%>')"/>
    <script type="text/javascript">
        dojo.require("dijit.form.FilteringSelect");
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
    </script>
    <% } else { %>
    <% if (loopSetting.getSyntax() == PwmSetting.Syntax.STRING) { %>
    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"/>
    <img src="<%=request.getContextPath()%>/resources/reset.gif" alt="Reset" title="Reset to default value"
         id="resetButton-<%=loopSetting.getKey()%>"
         style="visibility:hidden; vertical-align:bottom;"
         onclick="handleResetClick('<%=loopSetting.getKey()%>')"/>
    <script type="text/javascript">
        dojo.require("dijit.form.ValidationTextBox");
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
        }, "value_<%=loopSetting.getKey()%>")
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.NUMERIC) { %>
    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"/>
    <img src="<%=request.getContextPath()%>/resources/reset.gif" alt="Reset" title="Reset to default value"
         id="resetButton-<%=loopSetting.getKey()%>"
         style="visibility:hidden; vertical-align:bottom;"
         onclick="handleResetClick('<%=loopSetting.getKey()%>')"/>
    <script type="text/javascript">
        dojo.require("dijit.form.NumberSpinner");
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
        }, "value_<%=loopSetting.getKey()%>")
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
                        <input type="password" id="value_verify_<%=loopSetting.getKey()%>" name="setting_verify_<%=loopSetting.getKey()%>" style="width: 400px" >
                    </td>
                </tr>
            </table>
        </div>
        <div style="float: left; padding: 5px">
            <img src="<%=request.getContextPath()%>/resources/reset.gif" alt="Reset" title="Reset to default value"
                 id="resetButton-<%=loopSetting.getKey()%>"
                 style="visibility:hidden; vertical-align:bottom;"
                 onclick="handleResetClick('<%=loopSetting.getKey()%>')"/>
        </div>
    </div>
    <script type="text/javascript">
        dojo.require("dijit.form.ValidationTextBox");
        new dijit.form.ValidationTextBox({
            required: <%=loopSetting.isRequired()%>,
            invalidMessage: "The password is not valid.",
            style: "width: 400px",
            value: "[Loading..]",
            type: 'password',
            onChange: function() {
                writeSetting('<%=loopSetting.getKey()%>', this.value);
                dojo.byId('value_verify_<%=loopSetting.getKey()%>').value = '';
                dijit.byId('value_verify_<%=loopSetting.getKey()%>').value = '';
                dijit.byId('value_verify_<%=loopSetting.getKey()%>').validator();
            },
            disabled: true
        }, "value_<%=loopSetting.getKey()%>");
        new dijit.form.ValidationTextBox({
            required: true,
            invalidMessage: "The password does not match.",
            style: "width: 400px",
            disabled: false,
            type: 'password',
            validator: function() {
                var password = dojo.byId('value_<%=loopSetting.getKey()%>').value;
                var verifyPassword = dojo.byId('value_verify_<%=loopSetting.getKey()%>').value;
                return password == verifyPassword;
            }
        }, "value_verify_<%=loopSetting.getKey()%>");
    </script>
    <br class="clear"/>
    <% } %>
    <script type="text/javascript">
        readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
            getObject('value_<%=loopSetting.getKey()%>').value = dataValue;
            getObject('value_<%=loopSetting.getKey()%>').disabled = false;
            dijit.byId('value_<%=loopSetting.getKey()%>').set('disabled', false);
            dijit.byId('value_<%=loopSetting.getKey()%>').validate(false);
            dijit.byId('value_<%=loopSetting.getKey()%>').startup();
        })
    </script>
    <% } %>
</div>
<br/>
<% } %>
<% } %>
