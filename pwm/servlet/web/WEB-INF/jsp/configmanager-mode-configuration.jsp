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
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/configmanager.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            PWM Configuration Editor
        </div>
        <div id="header-title">
            Configuration is editable
        </div>
    </div>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p>Welcome to the PWM ConfigManager. PWM is in configuration mode, which means you can make changes to the
            running configuration
            directly through this page. Changes made in the configuration editor will be saved immediately, and PWM will
            automatically restart to have changes
            take effect.</p>

        <p>The current configuration was loaded at
            <%=ContextManager.getContextManager(session).getConfigReader().getConfigurationReadTime()%>.
            (Epoch <%=ContextManager.getContextManager(session).getConfigReader().getConfigurationEpoch()%>)
        </p>
        <div data-dojo-type="dijit.TitlePane" title="PWM Health" style="border:0; margin:0; padding:0">
            <div id="healthBody" style="border:0; margin:0; padding:0">
                <div id="WaitDialogBlank"></div>
            </div>
            <script type="text/javascript">
                require(["dojo/domReady!"],function(){
                    showPwmHealth('healthBody', false, true);
                });
            </script>
        </div>
        <br class="clear"/>

        <table style="border:0">
            <tr style="border:0">
                <td style="border:0; text-align: right">
                    <a class="menubutton" href="#" onclick="document.forms['editMode'].submit();">Configuration Editor</a>
                    <form action="<pwm:url url='ConfigManager'/>" method="post" name="editMode"
                          enctype="application/x-www-form-urlencoded">
                        <input type="hidden" name="processAction" value="editMode"/>
                        <input type="hidden" name="mode" value="SETTINGS"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
                <td style="border:0">
                    <p>Use the configuration editor to edit the running configuration.</p>
                </td>
            </tr>
            <tr style="border:0">
                <td style="border:0; text-align: right">
                    <a class="menubutton" href="#" onclick="var viewLog = window.open('<%=request.getContextPath()%><pwm:url url='/public/CommandServlet'/>?processAction=viewLog','logViewer','status=0,toolbar=0,location=0,menubar=0,scrollbars=1,resizable=1');viewLog.focus;return false">View Log Events</a>
                </td>
                <td style="border:0">
                    <p>View recent log events.  Requires pop-up windows to be enabled in your browser.</p>
                </td>
            </tr>
            <tr style="border:0">
                <td style="border:0; text-align: right">
                    <a class="menubutton" href="#" onclick="document.forms['generateXml'].submit();">Download Configuration File</a>
<form action="<pwm:url url='ConfigManager'/>" method="post" name="generateXml"
                          enctype="application/x-www-form-urlencoded">
                        <input type="hidden" name="processAction" value="generateXml"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
                <td style="border:0">
                    <p>Download the current configuration XML file.</p>
                </td>
            </tr>
            <tr style="border:0">
                <td style="border:0; text-align: right">
                    <a class="menubutton" href="#" onclick="if (confirm('Are you sure you wish to overwrite the current running configuration with the selected file?')) {document.forms['uploadXml'].submit()}">Upload Configuration File</a>
                </td>
                <td style="border:0">
                    <p>Upload an existing configuration file. The uploaded file will be saved as the PWM configuration and will replace
                        the current configuration.</p>
                    <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
                        <input type="hidden" name="processAction" value="uploadXml"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                        <input type="file" name="uploadFile" size="50" style="width: 350px" data-dojo-type="dojox/form/Uploader" required="required"/>
                        <%--<input type="submit" class="btn" name="uploadSubmit" value="   Upload   "
                               onclick="document.forms['uploadXml'].submit();"/>--%>
                    </form>
                </td>
            </tr>
            <tr style="border:0">
                <td style="border:0; text-align: right">
                    <a class="menubutton" href="#" onclick="if (confirm('Are you sure you want to lock the configuration?')) {showWaitDialog('Lock Configuration'); finalizeConfiguration();}">Lock Configuration</a>
                    <form action="<pwm:url url='ConfigManager'/>" method="post" name="lockConfiguration"
                          enctype="application/x-www-form-urlencoded">
                        <input type="hidden" name="processAction" value="lockConfiguration"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
                <td style="border:0">
                    <p>Lock the configuration. Once, locked you can still edit the configuration or unlock the
                        configuration as long as you have access to the configuration file at <span style="font-style: italic;"><%=configFilePath%></span>.
                        </p>
                </td>
            </tr>
            <tr style="border:0">
                <td style="border:0; text-align: right">
                    <a class="menubutton" href="<%=request.getContextPath()%>">Main Menu</a>
                </td>
                <td style="border:0">
                    <p>Return to the main menu to test the configuration.</p>
                </td>
            </tr>
        </table>
    </div>
</div>
<script type="text/javascript">
    require(["dojo/parser","dijit/TitlePane","dojo/domReady!","dojox/form/Uploader"],function(dojoParser){
        dojoParser.parse();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
