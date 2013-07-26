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

<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmApplication pwmApplication = ContextManager.getPwmApplication(session);
    String configFilePath = PwmConstants.CONFIG_FILE_FILENAME;
    try { configFilePath = ContextManager.getContextManager(session).getConfigReader().getConfigFile().toString(); } catch (Exception e) { /* */ }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler()">
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            <pwm:Display key="Title_ConfigManager" bundle="Config"/>
        </div>
        <div id="header-title">
        </div>
    </div>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <% final String configLoadTime = ContextManager.getContextManager(session).getConfigReader().getConfigurationReadTime().toString(); %>
        <% final String configEpoch = String.valueOf(ContextManager.getContextManager(session).getConfigReader().getConfigurationEpoch()); %>
        <pwm:Display key="Display_ConfigManagerConfiguration" bundle="Config" value1="<%=configLoadTime%>" value2="<%=configEpoch%>"/>
        <div data-dojo-type="dijit.TitlePane" title="Health" style="border:0; margin:0; padding:0">
            <div id="healthBody" style="border:0; margin:0; padding:0">
                <div id="WaitDialogBlank"></div>
            </div>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    require(["dojo/domReady!"],function(){
                        showPwmHealth('healthBody', {showRefresh: true});
                    });
                });
            </script>
        </div>
        <br class="clear"/>

        <table style="border:0">
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="#" onclick="startConfigurationEditor()"><pwm:Display key="MenuItem_ConfigEditor" bundle="Config"/></a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_ConfigEditor" bundle="Config"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="#" onclick="var viewLog = window.open('<%=request.getContextPath()%><pwm:url url='/public/CommandServlet'/>?processAction=viewLog','logViewer','status=0,toolbar=0,location=0,menubar=0,scrollbars=1,resizable=1');viewLog.focus;return false"><pwm:Display key="MenuItem_ViewLog" bundle="Config"/></a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_ViewLog" bundle="Config"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="#" onclick="showConfirmDialog(null,'<pwm:Display key="Confirm_LockConfig" bundle="Config"/>',function(){finalizeConfiguration()})"><pwm:Display key="MenuItem_LockConfig" bundle="Config"/></a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_LockConfig" bundle="Config" value1="<%=configFilePath%>"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="<%=request.getContextPath()%>"><pwm:Display key="MenuItem_MainMenu" bundle="Config"/></a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_MainMenu" bundle="Config"/></p>
                </td>
            </tr>
        </table>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/TitlePane","dojo/domReady!","dojox/form/Uploader"],function(dojoParser){
            dojoParser.parse();
        });
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
