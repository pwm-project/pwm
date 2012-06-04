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
<body class="nihilo">
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/configmanager.js"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            PWM Configuration Editor
        </div>
        <div id="header-title">
            Configuration Mode: <%=ContextManager.getPwmApplication(session).getApplicationMode()%>
        </div>
    </div>
    <div id="centerbody">
        <p>Welcome to PWM.  We hope you enjoy using this software.</p>
        <p>For help, guidance and other resources please visit the <a href="<%=PwmConstants.PWM_URL_HOME%>">PWM Project Page</a></p>
        <p>PWM was not able to detect a pre-existing configuration and is now in new configuration mode.  Please begin configuring PWM by selecting a default
            template below.  If you decide to change your selection later, you can choose a different template at any time.</p>
        <p>After selecting a default template below configure PWM by setting the LDAP configuration options, and then saving the configuration.</p>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <% for (final PwmSetting.Template template : PwmSetting.Template.values()) { %>
        <h3><a href="#" onclick="startNewConfigurationEditor('<%=template.toString()%>')"> <%=template.getDescription()%></a></h3>
        <% } %>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="editMode"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="editMode"/>
            <input type="hidden" name="mode" value="SETTINGS"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>

        <br/>
        <h2><a href="#" onclick="document.forms['uploadXml'].submit();">Upload Configuration File</a></h2>

        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" name="uploadFile" size="50"/>
            <input type="submit" class="btn" name="uploadSubmit" value="Upload"
                   onclick="document.forms['uploadXml'].submit();"/>
        </form>
        <p>Alternatively, you may upload a previously saved configuration file. The uploaded file will be saved as the PWM configuration.</p>
    </div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>