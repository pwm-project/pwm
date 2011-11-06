<%@ page import="password.pwm.bean.ConfigManagerBean" %>
<%@ page import="password.pwm.config.PwmSetting" %>
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="fragment/header.jsp" %>
<body class="tundra">
<%
    final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(session).getConfigManagerBean();
    final boolean hasBeenModified = configManagerBean.getConfiguration() != null && configManagerBean.getConfiguration().hasBeenModified();
%>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/configmanager.js"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            PWM Offline Configuration Editor
        </div>
        <div id="header-title">
            Configuration is locked
        </div>
    </div>
    <form action="<pwm:url url='ConfigManager'/>" method="post" name="editMode" enctype="application/x-www-form-urlencoded">
        <input type="hidden" name="processAction" value="editMode"/>
        <input type="hidden" name="mode" value="SETTINGS"/>
        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
    </form>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <% if (!hasBeenModified) { %>
        <p>
            The configuration for this server has been locked.
            While the configuration is locked, it is not possible to edit the live, running configuration directly.
            However, you can use this interface to upload your existing <span
                style="font-style: italic;">PwmConfiguarion.xml</span> and edit
            the configuration using this offline editor, and then save (download) the updated configuration to replace the current running configuration.
        </p>
        <h2>Upload Configuration File</h2>
        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" name="uploadFile" size="50"/>
            <input type="submit" class="btn" name="uploadSubmit" value="Upload"
                   onclick="document.forms['uploadXml'].submit();"/>
        </form>
        <p>Upload an existing <span style="font-style: italic;">PwmConfiguration.xml</span> to be edited in this offline configuration editor.</p>
        <br/>
        <br/>
        <h3>Option: Un-locking the Configuration</h3>
        <p>
            The locking of the <span style="font-style: italic;">PwmConfiguration.xml</span> file is controlled by the property "configIsEditable" within the file.  Set this property to "true" to return
            to the online configuration mode.  Be aware that while this property is set to true anyone accessing this site can make modifications to the live configuration without authentication.
        </p>
        <h3>Option: Create a new configuration</h3>
        <p>
                <% for (final PwmSetting.Template template : PwmSetting.Template.values()) { %>
            <a href="#" onclick="startNewConfigurationEditor('<%=template.toString()%>')">New Configuration - <%=template.getDescription()%></a><br/>
                <% } %>
        <p>Edit a new, default configuration in memory by selecting a new configuration template.  After editing the configuration, you can download
            the <span style="font-style: italic;">PwmConfiguration.xml</span> file.  This option will not modify the running configuration.
        </p>
        <% } else { %>
        <p>
            Your modified configuration is currently in memory.  Please download the <span style="font-style: italic;">PwmConfiguration.xml</span> file or return
            to the editor.
        </p>

        <form action="<pwm:url url='ConfigManager'/>" method="post" name="generateXml"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="generateXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <h2><a href="#" onclick="document.forms['generateXml'].submit();">Download Configuration File</a></h2>
        <p>Download the in memory configuration to an XML file. Save the <span style="font-style: italic;">PwmConfiguration.xml</span> to PWM's <span
                style="font-style: italic;">WEB-INF </span> directory to change the configuration.  In most cases, PWM will automatically restart and load the new configuration immediately.</p>

        <h2><a href="#" onclick="document.forms['editMode'].submit();">Return to Editor</a></h2>
        <p>Continue editing the in memory configuration.</p>

        <form action="<pwm:url url='ConfigManager'/>" method="post" name="cancelEditing"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="cancelEditing"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <h2><a href="#" onclick="document.forms['cancelEditing'].submit();">Cancel Edits</a></h2>
        <p>Cancel all changes you have made to the in-memory configuration.</p>
        <% } %>
    </div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
