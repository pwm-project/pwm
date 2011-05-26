<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.bean.ConfigManagerBean" %>
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body class="tundra">
<%
    final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(session).getConfigManagerBean();
    final boolean hasBeenModified = configManagerBean.getConfiguration().hasBeenModified();
%>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/configmanager.js"></script>
<div id="wrapper">
    <jsp:include page="header-body.jsp">
        <jsp:param name="pwm.PageName" value="PWM Configuration Manager"/>
    </jsp:include>
    <div id="centerbody">
        <% if (PwmSession.getSessionStateBean(session).getSessionError() != null) { %>
        <span style="width:600px" id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
        <% } else { %>
        <span style="visibility:hidden; width:600px" id="error_msg" class="msg-success"> </span>
        <% } %>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="editMode"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="editMode"/>
            <input type="hidden" name="mode" value="SETTINGS"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <% if (!hasBeenModified) { %>
        <p><span style="font-weight: bold;">The configuration for this server is now finalized and locked.</span></p>
        <p>If you wish to return this server to the previous live-edit configuration mode, edit the <span style="font-style: italic;">PwmConfiguration.xml</span> file by setting the
                property "configIsEditable" to "true".   Otherwise, please choose from the following options:</p>

        <h2>Upload Configuration File</h2>
        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" class="btn" name="uploadFile" size="50"/>
            <input type="submit" class="btn" name="uploadSubmit" value="Upload"
                   onclick="document.forms['uploadXml'].submit();"/>
        </form>
        <p>Upload a previously saved (or current <span style="font-style: italic;">PwmConfiguration.xml</span>) configuration file into memory. The uploaded file will be available for editing during this
            browser session, and can then be downloaded to update/replace the current running configuration.</p>
        <h2>Edit a New Configuration</h2>
        <% for (final PwmSetting.Template template : PwmSetting.Template.values()) { %>
        <h3><a href="#" onclick="startNewConfigurationEditor('<%=template.toString()%>')"><%=template.getDescription()%></a></h3>
        <% } %>
        <p>Edit a new, default configuration in memory by selecting a new configuration template.  After editing the configuration, you can download
        the <span style="font-style: italic;">PwmConfiguration.xml</span> file.</p>


        <% } else { %>

        <h2><a href="#" onclick="document.forms['editMode'].submit();">Return to Editor</a></h2>
        <p>Continue editing the in memory configuration.</p>

        <h2><a href="#" onclick="document.forms['generateXml'].submit();">Download Configuration File</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="generateXml"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="generateXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Download the in memory configuration to an XML file. Save the <span style="font-style: italic;">PwmConfiguration.xml</span> to PWM's <span
                style="font-style: italic;">WEB-INF </span> directory to change the configuration.  In most cases, PWM will automatically restart and load the new configuration immediately.</p>
        <% } %>
    </div>
</div>
<%@ include file="footer.jsp" %>
</body>
</html>
