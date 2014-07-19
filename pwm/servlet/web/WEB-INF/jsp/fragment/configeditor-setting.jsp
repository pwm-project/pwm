<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.bean.ConfigEditorCookie" %>
<%@ page import="password.pwm.config.LdapProfile" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.PwmSettingSyntax" %>
<%@ page import="password.pwm.config.StoredConfiguration" %>
<%@ page import="password.pwm.http.ContextManager" %>
<%@ page import="password.pwm.http.PwmSession" %>
<%@ page import="password.pwm.http.bean.ConfigManagerBean" %>
<%@ page import="password.pwm.http.servlet.ConfigEditorServlet" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="java.util.Locale" %>

<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
    final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
    final PwmSetting loopSetting = (PwmSetting)request.getAttribute("setting");
    final Locale locale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale();
    final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(session).getConfigManagerBean();
    final ConfigEditorCookie cookie = ConfigEditorServlet.readConfigEditorCookie(request, response);
    final boolean showDescription = (Boolean)request.getAttribute("showDescription");
    final String profileID = (String)request.getAttribute("profileID");
    final StoredConfiguration.SettingMetaData settingMetaData = configManagerBean.getConfiguration().readSettingMetadata(loopSetting,profileID);
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
    <% if (showDescription) { %>
    <span class="text"><%=title%></span>
    <% } else { %>
    <span class="text" onclick="PWM_CFGEDIT.toggleHelpDisplay('<%=loopSetting.getKey()%>',{})"><%=title%></span>
    <% } %>
    <% if (!showDescription) { %>
    <div class="fa fa-question icon_button" title="Help" id="helpButton-<%=loopSetting.getKey()%>" onclick="PWM_CFGEDIT.toggleHelpDisplay('<%=loopSetting.getKey()%>',{})"></div>
    <% } %>
    <div style="visibility: hidden" class="fa fa-undo icon_button" title="Reset" id="resetButton-<%=loopSetting.getKey()%>" onclick="handleResetClick('<%=loopSetting.getKey()%>')" ></div>
</div>
<div id="helpDiv_<%=loopSetting.getKey()%>" class="helpDiv" style="display: none">
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.getObject('helpDiv_<%=loopSetting.getKey()%>').innerHTML = PWM_SETTINGS['settings']['<%=loopSetting.getKey()%>']['description'];
            PWM_CFGEDIT.toggleHelpDisplay('<%=loopSetting.getKey()%>',{force:'<%=showDescription ? "show":"hide"%>'});
        });
    </script>
    </pwm:script>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.showTooltip({
            id: "resetButton-<%=loopSetting.getKey()%>",
            text: PWM_CONFIG.showString('Tooltip_ResetButton')
        });
        <% if (!showDescription) { %>
        PWM_MAIN.showTooltip({
            id: "helpButton-<%=loopSetting.getKey()%>",
            text: PWM_CONFIG.showString('Tooltip_HelpButton')
        });
        <% } %>
    });
</script>
</pwm:script>
<div id="titlePane_<%=loopSetting.getKey()%>" class="setting_body">
    <% if (loopSetting.getSyntax() == PwmSettingSyntax.LOCALIZED_STRING || loopSetting.getSyntax() == PwmSettingSyntax.LOCALIZED_TEXT_AREA) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0" width="500">
        <tr style="border-width:0">
            <td style="border-width:0"><input type="text" disabled="disabled" value="[<pwm:Display key="Display_PleaseWait"/>]"/></td>
        </tr>
    </table>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            LocaleTableHandler.initLocaleTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>', '<%=loopSetting.getSyntax()%>');
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.STRING_ARRAY || loopSetting.getSyntax() == PwmSettingSyntax.PROFILE) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
    </table>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            MultiTableHandler.initMultiTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>');
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.LOCALIZED_STRING_ARRAY) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
        <tr>
            <td><input type="text" disabled="disabled" value="[<pwm:Display key="Display_PleaseWait"/>]"/></td>
        </tr>
    </table>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            MultiLocaleTableHandler.initMultiLocaleTable('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>', '<%=loopSetting.getRegExPattern()%>');
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.CHALLENGE) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
        <tr>
            <td><input type="text" disabled="disabled" value="[<pwm:Display key="Display_PleaseWait"/>]"/></td>
        </tr>
    </table>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            ChallengeTableHandler.init('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>');
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.USER_PERMISSION) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border:0 none">
    </table>
    <div style="width: 100%; text-align: center">
    <button id="<%=loopSetting.getKey()%>_ViewMatchesButton" data-dojo-type="dijit.form.Button">View Matches</button>
    </div>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            UserPermissionHandler.init('table_setting_<%=loopSetting.getKey()%>', '<%=loopSetting.getKey()%>');
            require(["dojo/parser","dijit/form/Button"],function(parser,Button){
                new Button({
                    onClick:function(){executeSettingFunction('<%=loopSetting.getKey()%>',preferences['profile'],'password.pwm.config.function.UserMatchViewerFunction')}
                },'<%=loopSetting.getKey()%>_ViewMatchesButton');
            });
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.FORM) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border:0 none">
    </table>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            FormTableHandler.init('<%=loopSetting.getKey()%>',<%=Helper.getGson().toJson(loopSetting.getOptions())%>);
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.OPTIONLIST) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border:0 none">
    </table>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            OptionListHandler.init('<%=loopSetting.getKey()%>',<%=Helper.getGson().toJson(loopSetting.getOptions())%>);
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.ACTION) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border:0 none">
    </table>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            ActionHandler.init('<%=loopSetting.getKey()%>');
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.EMAIL) { %>
    <table id="table_setting_<%=loopSetting.getKey()%>" style="border:0 none">
    </table>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            EmailTableHandler.init('<%=loopSetting.getKey()%>');
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.BOOLEAN) { %>
    <input type="hidden" id="value_<%=loopSetting.getKey()%>" value="false"/>
    <div id="button_<%=loopSetting.getKey()%>" type="button"></div>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            BooleanHandler.init('<%=loopSetting.getKey()%>');
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.SELECT) { %>
    <select id="setting_<%=loopSetting.getKey()%>" disabled="true">
        <% for (final String loopValue : loopSetting.getOptions().keySet()) { %>
        <option value="<%=loopValue%>"><%=loopSetting.getOptions().get(loopValue)%></option>
        <% } %>
    </select>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dijit/form/Select"],function(Select){
                var selectWidget = new Select({
                    id: 'setting_<%=loopSetting.getKey()%>',
                    style: 'width: 350px'
                }, 'setting_<%=loopSetting.getKey()%>');
                readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
                    require(["dijit/registry","dojo/on"],function(registry,on){
                        var dijitElement = registry.byId('setting_<%=loopSetting.getKey()%>');
                        dijitElement.set('value',dataValue);
                        dijitElement.setDisabled(false);
                        setTimeout(function(){
                            on(selectWidget,"change",function(){
                                writeSetting('<%=loopSetting.getKey()%>',selectWidget.value)
                            });
                        },100);
                    });
                });
            });
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.X509CERT) { %>
    <div style="padding-right:15px">
        <% request.setAttribute("certificate",configManagerBean.getConfiguration().readSetting(loopSetting,cookie.getProfile()).toNativeObject()); %>
        <jsp:include page="setting-certificate.jsp"/>
        <br/>
    </div>
    <% } else { %>
    <% if (loopSetting.getSyntax() == PwmSettingSyntax.TEXT_AREA) { %>
    <textarea id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>">&nbsp;</textarea>
    <pwm:script>
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
                    onKeyPress: function() {
                        writeSetting('<%=loopSetting.getKey()%>', this.value);
                    },
                    value: "[<pwm:Display key="Display_PleaseWait"/>]",
                    disabled: true
                }, "value_<%=loopSetting.getKey()%>");
                readInitialTextBasedValue('<%=loopSetting.getKey()%>');
            });
        });
    </script>
    </pwm:script>
    <% } if (loopSetting.getSyntax() == PwmSettingSyntax.STRING) { %>
    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"/>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dijit/form/ValidationTextBox"],function(ValidationTextBox){
                new ValidationTextBox({
                    regExp: "<%=loopSetting.getRegExPattern().pattern()%>",
                    required: <%=loopSetting.isRequired()%>,
                    invalidMessage: PWM_CONFIG.showString('Warning_InvalidFormat'),
                    style: "width: 550px",
                    onChange: function() {
                        writeSetting('<%=loopSetting.getKey()%>', this.value);
                    },
                    placeholder: '<%=StringEscapeUtils.escapeJavaScript(loopSetting.getPlaceholder(locale))%>',
                    value: "[<pwm:Display key="Display_PleaseWait"/>]",
                    disabled: true
                }, "value_<%=loopSetting.getKey()%>");
                readInitialTextBasedValue('<%=loopSetting.getKey()%>');
            });
        });
    </script>
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.NUMERIC) { %>
    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"/>
    <pwm:script>
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
    </pwm:script>
    <% } else if (loopSetting.getSyntax() == PwmSettingSyntax.PASSWORD) { %>
    <div id="<%=loopSetting.getKey()%>_parentDiv">
        <button data-dojo-type="dijit.form.Button" onclick="ChangePasswordHandler.init('<%=loopSetting.getKey()%>','<%=loopSetting.getLabel(locale)%>')">Store Password</button>
        <button id="clearButton_<%=loopSetting.getKey()%>" data-dojo-type="dijit.form.Button" onclick="PWM_MAIN.showConfirmDialog({text:'Clear password for setting <%=loopSetting.getLabel(locale)%>?',okAction:function() {PWM_CFGEDIT.resetSetting('<%=loopSetting.getKey()%>');PWM_MAIN.showInfo('<%=loopSetting.getLabel(locale)%> password cleared')}})">Clear Password</button>
    </div>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dijit/form/Button"],function(parser){
                parser.parse('<%=loopSetting.getKey()%>_parentDiv');
            });
        });
    </script>
    </pwm:script>
    <% } %>
    <% } %>
</div>
</div>
<% if (settingMetaData != null && (settingMetaData.getUserIdentity() != null || settingMetaData.getModifyDate() != null)) { %>
<div style="font-size: 8pt; margin-top: 2px; text-align: center; padding: 0; border: 0;">
    <% if (settingMetaData.getModifyDate() != null ) { %>
    <span style="color: grey">modified </span>
    <span class="timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(settingMetaData.getModifyDate())%></span>
    <% } %>
    <% if (settingMetaData.getUserIdentity() != null ) { %>
    <span style="color: grey">modified by </span>
    <span><%=settingMetaData.getUserIdentity().getUserDN()%></span>
    <%  final LdapProfile storedProfile = settingMetaData.getUserIdentity().getLdapProfile(pwmApplication.getConfig()); %>
    <%  if (storedProfile != null && !storedProfile.isDefault()) { %>
    <span style="color: grey"> (<%=settingMetaData.getUserIdentity().getLdapProfileID()%>)</span>
    <% } %>
    <% } %>
</div>
<% } %>
<br/>
