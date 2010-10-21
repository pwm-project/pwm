
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

<%@ page import="password.pwm.config.ConfigurationReader" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body class="tundra">
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/configmanager.js"></script>
<div id="wrapper">
    <jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Configuration Manager"/></jsp:include>
    <div id="centerbody">
        <p>Welcome to the PWM ConfigManager.  PWM is in configuration mode, which means you can make changes to the running configuration
        directly through this page.  Changes made in the configuration editor will be saved immediately, and PWM will automatically restart to have changes
        take effect.</p>
        <p>The current PWM configuration was loaded at
            <%=PwmSession.getPwmSession(session).getContextManager().getConfigReader().getConfigurationReadTime()%>.
            (Epoch <%=PwmSession.getPwmSession(session).getContextManager().getConfigReader().getConfigurationEpoch()%>)
        </p>
        <% if (PwmSession.getSessionStateBean(session).getSessionError() != null) { %>
        <span id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
        <% } %>
        <h2><a href="#" onclick="document.forms['editMode'].submit();">Configuration Editor</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="editMode" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="editMode"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Start the PWM configuration editor.  When you select "Finished Editing" in the configuration page, your changes will be saved and
            you will return to this screen.</p>
        <h2><a href="#" onclick="showWaitDialog('Testing LDAP Connection'); document.forms['testLdapConnect'].submit();">Test LDAP Connection</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="testLdapConnect" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="testLdapConnect"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Test to be sure an LDAP connection can be made using the configuration</p>
        <h2><a href="#" onclick="document.forms['generateXml'].submit();">Download Configuration File</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="generateXml" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="generateXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Download the configuration XML file.</p>
        <h2><a href="#" onclick="document.forms['uploadXml'].submit();">Upload Configuration File</a></h2>
        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" class="btn" name="uploadFile" size="50"/>
            <input type="submit" class="btn" name="uploadSubmit" value="   Upload   " onclick="document.forms['uploadXml'].submit();"/>
        </form>
        <p>Upload an existing configuration.  The uploaded file will be saved as the PWM configuration.</p>
        <h2><a href="#" onclick="if (confirm('Are you sure you want to finalize the configuration?')) {showWaitDialog('Finalizing Configuration'); document.forms['lockConfiguration'].submit();}">Finalize Configuration</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="lockConfiguration" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="lockConfiguration"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Finalize the configuration.  Once the configuration is finalized, you can no longer directly edit the running configuration using this interface.  If you wish to make changes
            after finalization, you will need to edit the <span style="font-style: italic;">PwmConfiguration.xml</span> and set the property <span style="font-style: italic;">configIsEditable</span> to true.
        <h2><a href="<%=request.getContextPath()%>">PWM Main Menu</a></h2>
        <p>Return to the main menu to test the configuration.</p>
    </div>
</div>
<%@ include file="footer.jsp" %>
</body>                                                
</html>
