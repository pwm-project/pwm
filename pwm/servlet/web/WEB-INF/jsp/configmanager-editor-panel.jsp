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
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<script type="text/javascript">showError('');</script>
<% final PwmSetting.Category loopCategory = PwmSetting.Category.valueOf(request.getParameter("category")); %>
<p style="border-top:0; padding-top:0; margin-top:0"><%= loopCategory.getDescription(request.getLocale())%>
</p>
<% for (final PwmSetting loopSetting : PwmSetting.valuesByCategory().get(loopCategory)) { %>
<div id="titlePane_<%=loopSetting.getKey()%>">
    <div style="float: right; padding-left:5px; padding-bottom:5px">
        <img src="<%=request.getContextPath()%>/resources/reset.gif" alt="Reset" title="Reset to default"
             id="resetButton-<%=loopSetting.getKey()%>"
             style="visibility:hidden;"
             onclick="if (confirm('Are you sure you want to reset the setting <%=loopSetting.getLabel(request.getLocale())%> to the default value?')) { resetSetting('<%=loopSetting.getKey()%>');dijit.byId('mainContentPane').set('href','ConfigManager?processAction=editorPanel&category=<%=loopCategory.toString()%>');}"/>
    </div>
    <script type="text/javascript">
        new dijit.TitlePane({
            title: "<%=loopSetting.getLabel(request.getLocale())%>"
        }, "titlePane_<%=loopSetting.getKey()%>");
    </script>
    <label for="value_<%=loopSetting.getKey()%>">
        <%= loopSetting.getDescription(request.getLocale()) %>
    </label>
    <br/>

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
    <script type="text/javascript">
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
    <% } else { %>
    <% if (loopSetting.getSyntax() == PwmSetting.Syntax.STRING) { %>
    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"/>
    <script type="text/javascript">
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
    <script type="text/javascript">
        new dijit.form.NumberTextBox({
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
    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.PASSWORD) { %>
    <div id="password_wrapper_<%=loopSetting.getKey()%>">
        <table>
            <tr>
                <td>
                    <label>Password</label>
                </td>
                <td>
                    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>" pwType="new"/>
                </td>

            </tr>
            <tr>
                <td>
                    <label>Verify</label>
                </td>
                <td>
                    <input id="value_verify_<%=loopSetting.getKey()%>" name="setting_verify_<%=loopSetting.getKey()%>"
                           pwType="verify"/>
                </td>
            </tr>
        </table>
    </div>
    <script type="text/javascript">
        new dojox.form.PasswordValidator({
            required: <%=loopSetting.isRequired()%>,
            style: "width: 450px",
            onChange: function() {
                writeSetting('<%=loopSetting.getKey()%>', this.value);
            },
            value: "[Loading..]",
            disabled: true
        }, "password_wrapper_<%=loopSetting.getKey()%>")
    </script>
    <% } %>
    <script type="text/javascript">
        readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
            getObject('value_<%=loopSetting.getKey()%>').value = dataValue;
            getObject('value_<%=loopSetting.getKey()%>').disabled = false;
            dijit.byId('value_<%=loopSetting.getKey()%>').validate(false);
            dijit.byId('value_<%=loopSetting.getKey()%>').setDisabled(false);
        })
    </script>
    <% } %>
</div>
<br/>
<% } %>
