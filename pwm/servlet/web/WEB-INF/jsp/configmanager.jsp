<%@ page import="password.pwm.bean.servlet.ConfigManagerBean" %>
<%@ page import="password.pwm.util.PwmLogLevel" %>
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
<body class="nihilo">
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
        <div id="healthBody" style="border: #808080 1px solid; margin:0; padding:0; max-height: 300px; overflow-y: auto">
            <div id="WaitDialogBlank"></div>
        </div>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                require(["dojo/domReady!"],function(){
                    PWM_MAIN.showAppHealth('healthBody', {showRefresh: true, showTimestamp: true});
                });
            });
        </script>
        <style>
            .buttoncell {
                border: 0;
                width:50%;
            }
            .buttonrow {
                height: 45px;
                margin-top: 20px;
                margin-bottom: 20px;
            }
            .menubutton {
                cursor: pointer;
                display: inline;
            }

        </style>
        <br/>
        <br/>
        <table style="border:0">
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_CONFIG.startConfigurationEditor()" id="MenuItem_ConfigEditor">
                        <span class="fa fa-edit"></span>&nbsp;
                        <pwm:Display key="MenuItem_ConfigEditor" bundle="Config"/>
                    </a>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_ConfigEditor',PWM_CONFIG.showString('MenuDisplay_ConfigEditor'));
                        });
                    </script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_CONFIG.openLogViewer({})" id="MenuItem_ViewLog">
                        <span class="fa fa-list-alt"></span>&nbsp;
                        <pwm:Display key="MenuItem_ViewLog" bundle="Config"/>
                    </a>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_ViewLog',PWM_CONFIG.showString('MenuDisplay_ViewLog'));
                        });
                    </script>
                </td>
            </tr>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <iframe id="downloadFrame" style="display:none"></iframe>
                    <a class="menubutton" onclick="makeSupportBundle()" id="MenuItem_DownloadBundle">
                        <span class="fa fa-suitcase"></span>&nbsp;
                        <pwm:Display key="MenuItem_DownloadBundle" bundle="Config"/>
                    </a>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_DownloadBundle',PWM_CONFIG.showString('MenuDisplay_DownloadBundle'));
                        });
                    </script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_MAIN.goto('ConfigManager?processAction=generateXml',{closeDelay:5000})" id="MenuItem_DownloadConfig">
                        <span class="fa fa-download"></span>&nbsp;
                        <pwm:Display key="MenuItem_DownloadConfig" bundle="Config"/>
                    </a>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_DownloadConfig',PWM_CONFIG.showString('MenuDisplay_DownloadConfig'));
                        });
                    </script>
                </td>
            </tr>
            <% if (!configManagerBean.isConfigLocked()) { %>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_MAIN.showConfirmDialog(null,PWM_SETTINGS['display']['MenuDisplay_UploadConfig'],function(){PWM_CONFIG.uploadConfigDialog()},null)" id="MenuItem_UploadConfig">
                        <span class="fa fa-upload"></span>&nbsp;
                        <pwm:Display key="MenuItem_UploadConfig" bundle="Config"/>
                    </a>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_UploadConfig',PWM_CONFIG.showString('MenuDisplay_UploadConfig'));
                        });
                    </script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_CONFIG.lockConfiguration()" id="MenuItem_LockConfig">
                        <span class="fa fa-lock"></span>&nbsp;
                        <pwm:Display key="MenuItem_LockConfig" bundle="Config"/>
                    </a>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_LockConfig',PWM_CONFIG.showString('MenuDisplay_LockConfig'));
                        });
                    </script>
                </td>
            </tr>
            <% } %>
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_MAIN.goto('ConfigManager?processAction=exportLocalDB',{closeDelay:3000})" id="MenuItem_ExportLocalDB">
                        <span class="fa fa-download"></span>&nbsp;
                        <pwm:Display key="MenuItem_ExportLocalDB" bundle="Config"/>
                    </a>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_ExportLocalDB','<pwm:Display key="MenuDisplay_ExportLocalDB" bundle="Config" value1="<%=configFilePath%>"/>');
                        });
                    </script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" onclick="PWM_MAIN.goto('/')" id="MenuItem_MainMenu">
                        <span class="fa fa-arrow-circle-left"></span>&nbsp;
                        <pwm:Display key="MenuItem_MainMenu" bundle="Config"/>
                    </a>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            makeTooltip('MenuItem_MainMenu',PWM_CONFIG.showString('MenuDisplay_MainMenu'));
                        });
                    </script>
                </td>
            </tr>
        </table>
    </div>
</div>
</div>
<div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/TitlePane","dojo/domReady!","dojox/form/Uploader"],function(dojoParser){
            dojoParser.parse();
        });
    });

    function makeSupportBundle() {
        <% if (pwmApplication.getConfig().getEventLogLocalDBLevel() != PwmLogLevel.TRACE) { %>
        PWM_MAIN.showDialog(null,"<pwm:Display key="Warning_MakeSupportZipNoTrace" bundle="Config"/>");
        <% } else { %>
        PWM_MAIN.getObject('downloadFrame').src = 'ConfigManager?processAction=generateSupportZip&pwmFormID=' + PWM_GLOBAL['pwmFormID'];
        PWM_MAIN.showDialog("<pwm:Display key="Display_PleaseWait"/>","<pwm:Display key="Warning_SupportZipInProgress" bundle="Config"/>");
        <% } %>
    }

    function makeTooltip(id,text) {
        require(["dijit","dijit/Tooltip"],function(dijit,Tooltip){
            new Tooltip({
                connectId: [id],
                showDelay: 800,
                position: ['below','above'],
                label: '<div style="max-width: 300px">'+text+'</div>'
            });
        });
    }
</script>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_NO_PWM_MAIN_INIT,"true"); %>
<div><%@ include file="fragment/footer.jsp" %></div>
<script type="text/javascript">
    PWM_CONFIG.initConfigPage(function(){PWM_MAIN.pageLoadHandler()});
</script>
</body>
</html>
