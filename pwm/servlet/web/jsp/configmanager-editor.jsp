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

<%@ page import="org.json.simple.JSONArray" %>
<%@ page import="password.pwm.bean.ConfigManagerBean" %>
<%@ page import="password.pwm.config.ConfigurationReader" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.util.*" %>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
"http://www.w3.org/TR/html4/loose.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<% final Set<String> DEFAULT_LOCALES = new TreeSet<String>(); for (final Locale l : Locale.getAvailableLocales()) DEFAULT_LOCALES.add(l.toString());%>
<% final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(session).getConfigManagerBean(); %>
<body class="tundra">
<link href="<%=request.getContextPath()%>/resources/dojo/dijit/themes/tundra/tundra.css" rel="stylesheet" type="text/css"/>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dojo/dojo.js" djConfig="parseOnLoad: true"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dijit/dijit.js" djConfig="parseOnLoad: true"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/configmanager.js"></script>
<script type="text/javascript"><% { int i=0; for (final String loopLocale : DEFAULT_LOCALES) { %>availableLocales[<%=i++%>]='<%=loopLocale%>'; <% } } %></script>
<div id="wrapper">
    <jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Configuration Editor"/></jsp:include>
    <div id="centerbody" style="width: 700px">
        <br class="clear"/>
        <%  if (PwmSession.getSessionStateBean(session).getSessionError() != null) { %>
        <span style="width:680px" id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
        <% } else { %>
        <span style="visibility:hidden; width:680px" id="error_msg" class="msg-success"> </span>
        <% } %>
        <br class="clear"/>
        <div id="mainTabContainer" >
            <script type="text/javascript">
                dojo.require("dijit.layout.TabContainer");
                dojo.addOnLoad(function() {
                    var tc = new dijit.layout.TabContainer({
                        doLayout: false,
                        style: "width: 700px"
                    },"mainTabContainer");
                    tc.startup();
                });
            </script>
            <%
                for (final PwmSetting.Category loopCategory : PwmSetting.valuesByCategory().keySet()) {
                    final List<PwmSetting> loopSettings = PwmSetting.valuesByCategory().get(loopCategory);
            %>
            <div id="<%=loopCategory%>" dojoType="dijit.layout.ContentPane" title="<%=loopCategory.getLabel(request.getLocale())%>">
                <p style="border-top:0; padding-top:0; margin-top:0"><%= loopCategory.getDescription(request.getLocale())%></p>
                <%  for (final PwmSetting loopSetting : loopSettings) { %>
                <div dojoType="dijit.TitlePane" title="<%= loopSetting.getLabel(request.getLocale()) %>">
                    <label for="value_<%=loopSetting.getKey()%>">
                        <%= loopSetting.getDescription(request.getLocale()) %>
                    </label>
                    <br class="clear"/>
                    <% if (loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_STRING || loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_TEXT_AREA) { %>
                    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
                        <tr style="border-width:0"><td style="border-width:0"><input type="text" disabled="disabled" value="[Loading...]" style="width: 600px"/></td></tr>
                    </table>
                    <script type="text/javascript">
                        dojo.addOnLoad(function() {initLocaleTable('table_setting_<%=loopSetting.getKey()%>','<%=loopSetting.getKey()%>','<%=loopSetting.getRegExPattern()%>','<%=loopSetting.getSyntax()%>');});
                    </script>
                    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.STRING_ARRAY) { %>
                    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
                    </table>
                    <script type="text/javascript">
                        dojo.addOnLoad(function() {initMultiTable('table_setting_<%=loopSetting.getKey()%>','<%=loopSetting.getKey()%>','<%=loopSetting.getRegExPattern()%>');});
                    </script>
                    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.LOCALIZED_STRING_ARRAY) { %>
                    <table id="table_setting_<%=loopSetting.getKey()%>" style="border-width:0">
                        <tr><td><input type="text" disabled="disabled" value="[Loading...]" style="width: 600px"/></td></tr>
                    </table>
                    <script type="text/javascript">
                        dojo.addOnLoad(function() {initMultiLocaleTable('table_setting_<%=loopSetting.getKey()%>','<%=loopSetting.getKey()%>','<%=loopSetting.getRegExPattern()%>');});
                    </script>
                    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.BOOLEAN) { %>
                    <input type="hidden" id="value_<%=loopSetting.getKey()%>" value="false"/>
                    <button id="button_<%=loopSetting.getKey()%>" dojoType="dijit.form.Button" type="button" disabled="disabled"
                            onclick="toggleBooleanSetting('<%=loopSetting.getKey()%>');writeSetting('<%=loopSetting.getKey()%>', getObject('value_' + '<%=loopSetting.getKey()%>').value);">
                        [Loading...]
                    </button>
                    <script type="text/javascript">
                        dojo.addOnLoad(function() {
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
                    <% } else { %>
                    <% if (loopSetting.getSyntax() == PwmSetting.Syntax.STRING) { %>
                    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>" disabled="disabled"
                           value="[Loading...]" onchange="writeSetting('<%=loopSetting.getKey()%>',this.value);" required="<%=loopSetting.isRequired()%>"
                           style="width: 600px" dojoType="dijit.form.ValidationTextBox" regExp="<%=loopSetting.getRegExPattern().pattern()%>" invalidMessage="The value does not have the correct format."/>
                    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.PASSWORD) { %>
                    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>"
                           value="[Loading...]" required="<%=loopSetting.isRequired()%>"
                           type="password" autocomplete="off" size="60" dojoType="dijit.form.ValidationTextBox"
                           onchange="writeSetting('<%=loopSetting.getKey()%>',this.value);"
                           onkeypress="getObject('value_<%=loopSetting.getKey()%>-validation').value = '';dijit.byId('value_<%=loopSetting.getKey()%>-validation').validate(false);"/>
                    <br/>
                    <input id="value_<%=loopSetting.getKey()%>-validation" name="setting_<%=loopSetting.getKey()%>-validation"
                           type="password" value="" required="true" dojoType="dijit.form.ValidationTextBox" invalidMessage="The value does not match."/> (confirm)
                    <script type="text/javascript">
                        dojo.addOnLoad(function() {
                            readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
                                getObject('value_<%=loopSetting.getKey()%>-validation').value = dataValue;
                            })});
                        dojo.addOnLoad(function() {
                            dijit.byId("value_<%=loopSetting.getKey()%>-validation").validator = function (value, constraints) {
                                var realValue = getObject('value_<%=loopSetting.getKey()%>').value;
                                return realValue == value;
                            }});
                    </script>
                    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.NUMERIC) { %>
                    <input id="value_<%=loopSetting.getKey()%>" name="setting_<%=loopSetting.getKey()%>" disabled="disabled"
                           value="[Loading...]" onchange="writeSetting('<%=loopSetting.getKey()%>',this.value);" required="<%=loopSetting.isRequired()%>"
                           size="30" dojoType="dijit.form.NumberTextBox" invalidMessage="The value must be numeric."/>

                    <% } %>
                    <br/>
                    <script type="text/javascript">
                        dojo.addOnLoad(function() {
                            readSetting('<%=loopSetting.getKey()%>', function(dataValue) {
                                getObject('value_<%=loopSetting.getKey()%>').value = dataValue;
                                getObject('value_<%=loopSetting.getKey()%>').disabled = false;
                                dijit.byId('value_<%=loopSetting.getKey()%>').validate(false);
                                dijit.byId('value_<%=loopSetting.getKey()%>').setDisabled(false);
                            })
                        });
                    </script>
                    <% } %>
                </div>
                <br class="clear"/>
                <% } %>
            </div>
            <% } %>
        </div>
        <div style="text-align: center;">
            <h2>
                <% if (configManagerBean.getInitialMode() == ConfigurationReader.MODE.RUNNING) { %>
                <a href="#" onclick="showWaitDialog('Updating Configuration'); setTimeout(function() {document.forms['completeEditing'].submit();},1000)">Finished Editing</a>
                <% } else { %>
                <a href="#" onclick="showWaitDialog('Saving Configuration'); setTimeout(function() {document.forms['completeEditing'].submit();},1000)">Save Configuration</a>
                <% } %>
                &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
                <a href="#" onclick="showWaitDialog('Canceling...'); setTimeout(function() {document.forms['cancelEditing'].submit();},1000)">Cancel</a>
            </h2>
            <form action="<pwm:url url='ConfigManager'/>" method="post" name="completeEditing" enctype="application/x-www-form-urlencoded">
                <input type="hidden" name="processAction" value="finishEditing"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form action="<pwm:url url='ConfigManager'/>" method="post" name="cancelEditing" enctype="application/x-www-form-urlencoded">
                <input type="hidden" name="processAction" value="cancelEditing"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
        <br class="clear"/>
    </div>
</div>
<%@ include file="footer.jsp" %>
</body>
</html>
