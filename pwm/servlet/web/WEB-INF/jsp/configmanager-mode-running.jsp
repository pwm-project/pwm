<%@ page import="password.pwm.bean.ConfigManagerBean" %>
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
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler()">
<%
    final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(session).getConfigManagerBean();
    final boolean hasBeenModified = configManagerBean.getConfiguration() != null && configManagerBean.getConfiguration().hasBeenModified();
%>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/configmanager.js"/>"></script>
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
        <p><b>The configuration for this server has been locked.  However you can still edit the configuration.</b></p>
        <p>For security reasons, to edit the configuration, you must upload (and then download) the <span style="font-style: italic;">PwmConfiguration.xml</span>
            file.
        </p>
        <a class="menubutton" href="#" onclick="document.forms['uploadXml'].submit();">Upload Configuration Menu</a>
        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" name="uploadFile" size="50"/>
            <input type="submit" class="btn" name="uploadSubmit" value="Upload"
                   onclick="document.forms['uploadXml'].submit();"/>
        </form>
        <br/>
        <br/>
        <a class="menubutton">Alternate Option: Un-Locking the Configuration</a>
        <p>
            The locking of the <span style="font-style: italic;">PwmConfiguration.xml</span> file is controlled by the property "configIsEditable" within the file.  Set this property to "true" to return
            to the online configuration mode.  Be aware that while this property is set to true anyone accessing this site can make modifications to the live configuration without authentication.
        </p>
        <br/>
        <a class="menubutton" href="#" onclick="document.forms['editMode'].submit();">Alternate Option: Edit a new configuration</a>
        <p>
        <p>Edit a newconfiguration in memory by selecting a new configuration template.  After editing the configuration, you can download
            the <span style="font-style: italic;">PwmConfiguration.xml</span> file.  This option will not modify the running configuration.
        </p>
        <% } else { %>
        <p>Your modified configuration is currently in memory, but has not yet been saved.  Please choose an option below to continue.</p>
        <br/>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="generateXml"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="generateXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <a class="menubutton" href="#" onclick="document.forms['generateXml'].submit()">Download Configuration File</a>
        <p>Download the in memory configuration to an XML file. Save the <span style="font-style: italic;">PwmConfiguration.xml</span> to PWM's <span
                style="font-style: italic;">WEB-INF </span> directory to change the configuration.  In most cases, PWM will automatically restart and load the new configuration immediately.</p>

        <br/>
        <a class="menubutton" href="#" onclick="document.forms['editMode'].submit()">Return to Editor</a>
        <p>Continue editing the in memory configuration.</p>

        <form action="<pwm:url url='ConfigManager'/>" method="post" name="cancelEditing"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="cancelEditing"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>

        <br/>
        <a class="menubutton" href="#" onclick="document.forms['cancelEditing'].submit()">Cancel Edits</a>
        <p>Cancel all changes you have made to the in-memory configuration.</p>
        <% } %>
    </div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
