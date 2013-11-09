<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.PwmSettingSyntax" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="java.util.Locale" %>
<%@ page import="password.pwm.bean.servlet.ConfigManagerBean" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="password.pwm.servlet.ConfigEditorServlet" %>
<%@ page import="password.pwm.bean.ConfigEditorCookie" %>
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
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmSetting loopSetting = (PwmSetting)request.getAttribute("setting");
    final Locale locale = password.pwm.PwmSession.getPwmSession(session).getSessionStateBean().getLocale();
    final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean();
    final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(request, response);
    final boolean showDesc = cookie.isShowDesc();
%>
<div id="outline_<%=loopSetting.getKey()%>" class="setting_outline">
    <%
    StringBuilder title = new StringBuilder();
    title.append(loopSetting.getLabel(locale));
    if (loopSetting.getLevel() == 1) {
        title.append(" (Advanced)");
    }
%>
<div class="setting_title" id="title_<%=loopSetting.getKey()%>">
    <span class="text" onclick="toggleHelpDisplay('helpDiv_<%=loopSetting.getKey()%>')"><%=title%></span>
    <div class="fa fa-question icon_button" title="Help" id="helpButton-<%=loopSetting.getKey()%>" onclick="toggleHelpDisplay('helpDiv_<%=loopSetting.getKey()%>')"></div>
    <div class="fa fa-undo icon_button" title="Reset" id="resetButton-<%=loopSetting.getKey()%>" onclick="handleResetClick('<%=loopSetting.getKey()%>')" ></div>
</div>
<div id="helpDiv_<%=loopSetting.getKey()%>" class="helpDiv" style="display: <%=showDesc?"block":"none"%>">
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            getObject('helpDiv_<%=loopSetting.getKey()%>').innerHTML = PWM_SETTINGS['settings']['<%=loopSetting.getKey()%>']['description'];
        });
    </script>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dijit/Tooltip"],function(Tooltip){
            new Tooltip({
                connectId: ["resetButton-<%=loopSetting.getKey()%>"],
                label: 'Return this setting to its default value.'
            });
            new Tooltip({
                connectId: ["helpButton-<%=loopSetting.getKey()%>"],
                label: 'Show description for this setting.'
            });
        });
    });
</script>
<div id="titlePane_<%=loopSetting.getKey()%>" class="setting_body">
    <% if (loopSetting.getSyntax() == PwmSettingSyntax.LOCALIZED_STRING || loopSetting.getSyntax() == PwmSettingSyntax.LOCALIZED_TEXT_AREA) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0" width="500">
        <tr style="border-width:0">
            <td style="border-width:0"><input type="text" disabled="disabled" value="[<pwm:Display key="Display_PleaseWait"/>]"/></td>
        </tr>
    </table>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            LocaleTableHandler.initLocaleTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>', '<%=loopSetting.getSyntax()%>');
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.STRING_ARRAY) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
    </table>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            MultiTableHandler.initMultiTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>');
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.LOCALIZED_STRING_ARRAY) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
        <tr>
            <td><input type="text" disabled="disabled" value="[<pwm:Display key="Display_PleaseWait"/>]"/></td>
        </tr>
    </table>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            MultiLocaleTableHandler.initMultiLocaleTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>');
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.FORM) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border:0 none">
    </table>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            FormTableHandler.init('<%=loopSetting.getKey()%>',<%=Helper.getGson().toJson(loopSetting.getOptions())%>);
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.ACTION) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border:0 none">
    </table>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            ActionHandler.init('<%=loopSetting.getKey()%>');
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.EMAIL) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border:0 none">
    </table>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            EmailTableHandler.init('<%=loopSetting.getKey()%>');
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.BOOLEAN) { %>
    <input type="hidden" id="value_<%=loopSetting.getKey()%>" value="false"/>
    <div id="button_<%=loopSetting.getKey()%>" type="button"></div>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            BooleanHandler.init('<%=loopSetting.getKey()%>');
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.SELECT) { %>
    <select id="setting_<%=loopSetting.getKey()%>" disabled="true">
        <% for (final String loopValue : loopSetting.getOptions().keySet()) { %>
        <option value="<%=loopValue%>"><%=loopSetting.getOptions().get(loopValue)%></option>
        <% } %>
    </select>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dijit/form/Select"],function(Select){
                new Select({
                    id: 'setting_<%=loopSetting.getKey()%>',
                    onChange: function(){writeSetting('<%=loopSetting.getKey()%>',this.value)},
                    style: 'width: 350px'
                }, 'setting_<%=loopSetting.getKey()%>');
                readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
                    require(["dijit/registry"],function(registry){
                        var dijitElement = registry.byId('setting_<%=loopSetting.getKey()%>');
                        dijitElement.setDisabled(false);
                        dijitElement.set('value',dataValue);
                    });
                });
            });
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.X509CERT) { %>
    <div style="padding-right:15px">
        <% for (X509Certificate certificate : (X509Certificate[])configManagerBean.getConfiguration().readSetting(loopSetting).toNativeObject()) {%>
        <% request.setAttribute("certificate",certificate); %>
        <jsp:include page="setting-certificate.jsp"/>
        <br/>
        <% } %>
    </div>
    <% } else { %>
    <% if (loopSetting.getSyntax() == PwmSettingSyntax.TEXT_AREA) { %>
    <textarea id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>">&nbsp;</textarea>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dijit/form/Textarea"],function(Textarea){
                new Textarea({
                    regExp: "<%=loopSetting.getRegExPattern().pattern()%>",
                    required: <%=loopSetting.isRequired()%>,
                    invalidMessage: "The value does not have the correct format.",
                    style: "width: 500px",
                    onChange: function() {
                        writeSetting('<%=loopSetting.getKey()%>', this.value);
                    },
                    value: "[<pwm:Display key="Display_PleaseWait"/>]",
                    disabled: true
                }, "value_<%=loopSetting.getKey()%>");
                readInitialTextBasedValue('<%=loopSetting.getKey()%>');
            });
        });
    </script>
    <% } if (loopSetting.getSyntax() == PwmSettingSyntax.STRING) { %>
    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"/>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                new ValidationTextBox({
                    regExp: "<%=loopSetting.getRegExPattern().pattern()%>",
                    required: <%=loopSetting.isRequired()%>,
                    invalidMessage: "The value does not have the correct format.",
                    style: "width: 550px",
                    onChange: function() {
                        writeSetting('<%=loopSetting.getKey()%>', this.value);
                    },
                    value: "[<pwm:Display key="Display_PleaseWait"/>]",
                    disabled: true
                }, "value_<%=loopSetting.getKey()%>");
                readInitialTextBasedValue('<%=loopSetting.getKey()%>');
            });
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.NUMERIC) { %>
    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"/>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dijit","dijit/form/NumberSpinner"],function(dijit,NumberSpinner){
                new NumberSpinner({
                    regExp: "<%=loopSetting.getRegExPattern().pattern()%>",
                    required: <%=loopSetting.isRequired()%>,
                    invalidMessage: "The value does not have the correct format.",
                    style: "width: 100px",
                    onChange: function() {
                        writeSetting('<%=loopSetting.getKey()%>', this.value);
                    },
                    value: "[<pwm:Display key="Display_PleaseWait"/>]",
                    disabled: true
                }, "value_<%=loopSetting.getKey()%>");
                dijit.byId("value_<%=loopSetting.getKey()%>")._mouseWheeled = function() {};
                readInitialTextBasedValue('<%=loopSetting.getKey()%>');
            });
        });
    </script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.PASSWORD) { %>
    <div id="<%=loopSetting.getKey()%>_parentDiv">
        <button data-dojo-type="dijit.form.Button" onclick="ChangePasswordHandler.init('<%=loopSetting.getKey()%>','<%=loopSetting.getLabel(locale)%>')">Store Password</button>
        <button id="clearButton_<%=loopSetting.getKey()%>" data-dojo-type="dijit.form.Button" onclick="showConfirmDialog(null,'Clear password for setting <%=loopSetting.getLabel(locale)%>?',function() {resetSetting('<%=loopSetting.getKey()%>');showInfo('<%=loopSetting.getLabel(locale)%> password cleared')})">Clear Password</button>
    </div>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dijit/form/Button"],function(parser){
                parser.parse('<%=loopSetting.getKey()%>_parentDiv');
            });
        });
    </script>
    <% } %>
    <% } %>
</div>
</div>
<br/>
