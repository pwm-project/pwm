<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2011 The PWM Project
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

<%@ page import="password.pwm.PwmApplication" %>
<%@ page import="password.pwm.util.ServletHelper" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmApplication pwmApplication = ContextManager.getPwmApplication(session);
    String configFilePath = "PwmConfiguration.xml";
    try { configFilePath = ContextManager.getContextManager(session).getConfigReader().getConfigFile().toString(); } catch (Exception e) { /* */ }
%>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="fragment/header.jsp" %>
<body class="tundra">
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/configmanager.js"></script>
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

        <p>The current PWM configuration was loaded at
            <%=ContextManager.getContextManager(session).getConfigReader().getConfigurationReadTime()%>.
            (Epoch <%=ContextManager.getContextManager(session).getConfigReader().getConfigurationEpoch()%>)
        </p>
        <table class="tablemain">
            <tr>
                <td class="title" colspan="10">
                    PWM Health
                </td>
            </tr>
            <tr>
                <td colspan="10" style="border:0; margin:0; padding:0">
                    <div id="healthBody" style="border:0; margin:0; padding:0"></div>
                    <script type="text/javascript">
                        dojo.addOnLoad(function() {
                            showPwmHealth('healthBody', false);
                        });
                    </script>
                </td>
            </tr>
        </table>
        <br class="clear"/>

        <h2><a href="#" onclick="document.forms['editMode'].submit();">Configuration Editor</a></h2>

        <form action="<pwm:url url='ConfigManager'/>" method="post" name="editMode"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="editMode"/>
            <input type="hidden" name="mode" value="SETTINGS"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Use the PWM configuration editor to edit the running configuration.</p>

        <h2><a href="#" onclick="var viewLog = window.open('<pwm:url url='ConfigManager'/>?processAction=viewLog','logViewer','status=0,toolbar=0,location=0,menubar=0,scrollbars=1,resizable=1');viewLog.focus;return false">View Log Events</a></h2>
        <p>View recent log events.  Requires pop-up windows to be enabled in your browser.</p>

        <h2><a href="#" onclick="document.forms['generateXml'].submit();">Download Configuration File</a></h2>

        <form action="<pwm:url url='ConfigManager'/>" method="post" name="generateXml"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="generateXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Download the current configuration XML file.</p>

        <h2><a href="#" onclick="document.forms['uploadXml'].submit();">Upload Configuration File</a></h2>

        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" name="uploadFile" size="50"/>
            <input type="submit" class="btn" name="uploadSubmit" value="   Upload   "
                   onclick="document.forms['uploadXml'].submit();"/>
        </form>
        <p>Upload an existing configuration file. The uploaded file will be saved as the PWM configuration and will replace
            the current configuration.</p>

        <h2><a href="#"
               onclick="if (confirm('Are you sure you want to lock the configuration?')) {showWaitDialog('Lock Configuration'); finalizeConfiguration();}">Lock
            Configuration</a></h2>

        <form action="<pwm:url url='ConfigManager'/>" method="post" name="lockConfiguration"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="lockConfiguration"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>

        <p>Lock the configuration. Once the configuration is locked, you can no longer directly edit the running
            configuration using this interface.  If you wish to make changes
            after locking, you will need to have access to the <span style="font-style: italic;"><%=configFilePath%></span>
            file on the PWM server.

        <h2><a href="<%=request.getContextPath()%>">PWM Main Menu</a></h2>

        <p>Return to the main menu to test the configuration.</p>
    </div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
