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

<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.util.*" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body class="tundra">
<div id="wrapper">
    <jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Configuration Manager"/></jsp:include>
    <div id="centerbody">
        <p>PWM uses an xml configuration file to store the settings it needs to run. This file is saved as <span style="font-style: italic;">
        PwmConfiguration.xml</span> in the servlet's <span style="font-style: italic;">WEB-INF</span> folder. </p>
        <p> You can not edit PWM settings directly, however you can use this page to edit and manage the configuration
        file.  You can edit a configuration, save it, upload an existing file and reset a configuration back to the defaults.  When you are finished
        you can save the downloaded xml file and PWM will use that configuration the next time it starts. </p>
        <p> Until you download a configuration file, the changes you make exist only in your browser.  If your
        browser times out or is closed, those changes will be lost.</p>
        <% if (PwmSession.getSessionStateBean(session).getSessionError() != null) { %>
        <span id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
        <% } %>
        <h2><a href="#" onclick="document.forms['switchToEditMode'].submit();">Configuration Editor</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="switchToEditMode" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="switchToEditMode"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Start the PWM configuration editor.  When you have completed any configuration changes, you can return to this page.</p>
        <h2><a href="#" onclick="document.forms['testLdapConnect'].submit();">Test LDAP Connection</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="testLdapConnect" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="testLdapConnect"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Test to be sure an LDAP connection can be made using the in memory configuration</p>
        <h2><a href="#" onclick="if (confirm('Are you sure you want to clear the configuration?')) document.forms['resetConfig'].submit();">Clear Configuration</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="resetConfig" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="resetConfig"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Reset the in memory configuration to the defaults.  This will reset the in memory configuration you are currently editing.</p>
        <h2><a href="#" onclick="document.forms['generateXml'].submit();">Download Configuration File</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="generateXml" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="generateXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Download the in memory configuration to an XML file.  Save the file to PWM's <span
                style="font-style: italic;">WEB-INF </span> directory and restart PWM for
            the configuration file to take effect.</p>
        <h2><a href="#" onclick="document.forms['uploadXml'].submit();">Upload Configuration File</a></h2>
        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" class="btn" name="uploadFile" size="50"/>
        </form>
        <p>Upload an existing configuration.</p>
    </div>
</div>
<%@ include file="footer.jsp" %>
</body>
</html>
