<%@ page import="password.pwm.bean.servlet.ConfigManagerBean" %>
<%@ page import="password.pwm.util.*" %>
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
    final ConfigManagerBean configManagerBean = password.pwm.PwmSession.getPwmSession(session).getConfigManagerBean();
    String configFilePath = PwmConstants.CONFIG_FILE_FILENAME;
    try { configFilePath = ContextManager.getContextManager(session).getConfigReader().getConfigFile().toString(); } catch (Exception e) { /* */ }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="initConfigPage()">
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
        <%--
        <% final String configLoadTime = ContextManager.getContextManager(session).getConfigReader().getConfigurationReadTime().toString(); %>
        <% final String configEpoch = String.valueOf(ContextManager.getContextManager(session).getConfigReader().getConfigurationEpoch()); %>
        <pwm:Display key="Display_ConfigManagerConfiguration" bundle="Config" value1="<%=configLoadTime%>" value2="<%=configEpoch%>"/>
        --%>
            <div id="healthBody" style="border:0; margin:0; padding:0">
                <div id="WaitDialogBlank"></div>
            </div>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    require(["dojo/domReady!"],function(){
                        showAppHealth('healthBody', {showRefresh: true, showTimestamp: true});
                    });
                });
            </script>
        <br/>
        <table style="border:0">
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="#" onclick="startConfigurationEditor()">
                        <i class="fa fa-edit"></i>&nbsp;
                        <pwm:Display key="MenuItem_ConfigEditor" bundle="Config"/>
                    </a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_ConfigEditor" bundle="Config"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" style="cursor: pointer" onclick="openLogViewer()">
                        <i class="fa fa-list-alt"></i>&nbsp;
                        <pwm:Display key="MenuItem_ViewLog" bundle="Config"/>
                    </a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_ViewLog" bundle="Config"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <script type="text/javascript">
                    function makeSupportBundle() {
                        <% if (pwmApplication.getConfig().getEventLogLocalDBLevel() != PwmLogLevel.TRACE) { %>
                        showDialog(null,"<pwm:Display key="Warning_MakeSupportZipNoTrace" bundle="Config"/>");
                        <% } else { %>
                        getObject('downloadFrame').src = 'ConfigManager?processAction=generateSupportZip&pwmFormID=' + PWM_GLOBAL['pwmFormID'];
                        showDialog("<pwm:Display key="Display_PleaseWait"/>","<pwm:Display key="Warning_SupportZipInProgress" bundle="Config"/>");
                        <% } %>
                    }
                </script>
                <iframe id="downloadFrame" style="display:none"></iframe>
                <td class="menubutton_key">
                    <a class="menubutton" onclick="makeSupportBundle()">
                        <i class="fa fa-suitcase"></i>&nbsp;
                        Download Troubleshooting Bundle
                    </a>
                </td>
                <td style="border:0">
                    <p>Generate a support ZIP file that contains information useful for troubleshooting.</p>
                </td>
            </tr>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="#" onclick="window.location='ConfigManager?processAction=generateXml&pwmFormID=' + PWM_GLOBAL['pwmFormID'];">
                        <i class="fa fa-download"></i>&nbsp;
                        <pwm:Display key="MenuItem_DownloadConfig" bundle="Config"/>
                    </a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_DownloadConfig" bundle="Config"/></p>
                </td>
            </tr>
            <% if (!configManagerBean.isConfigLocked()) { %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="#" onclick="showConfirmDialog(null,PWM_SETTINGS['display']['MenuDisplay_UploadConfig'],function(){uploadConfigDialog()},null)">
                        <i class="fa fa-upload"></i>&nbsp;
                        <pwm:Display key="MenuItem_UploadConfig" bundle="Config"/>
                    </a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_UploadConfig" bundle="Config"/></p>
                </td>
            </tr>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="#" onclick="lockConfiguration()">
                        <i class="fa fa-lock"></i>&nbsp;
                        <pwm:Display key="MenuItem_LockConfig" bundle="Config"/>
                    </a>
                </td>
                <td style="border:0">
                    <p><pwm:Display key="MenuDisplay_LockConfig" bundle="Config" value1="<%=configFilePath%>"/></p>
                </td>
            </tr>
            <% } %>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="#" onclick="window.location='ConfigManager?processAction=exportLocalDB&pwmFormID=' + PWM_GLOBAL['pwmFormID'];">
                        <i class="fa fa-upload"></i>&nbsp;
                        Export LocalDB
                    </a>
                </td>
                <td style="border:0">
                    <p>Export the LocalDB</p>
                </td>
            </tr>
            <tr style="border:0">
                <td class="menubutton_key">
                    <a class="menubutton" href="<%=request.getContextPath()%>">
                        <i class="fa fa-arrow-circle-left"></i>&nbsp;
                        <pwm:Display key="MenuItem_MainMenu" bundle="Config"/>
                    </a>
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
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
