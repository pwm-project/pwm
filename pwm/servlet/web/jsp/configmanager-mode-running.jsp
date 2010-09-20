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
<div id="wrapper">
    <jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Configuration Manager"/></jsp:include>
    <div id="centerbody">
        <p>The configuration for this server is now finalized and locked.  You can see the running configuration <a href="<%=request.getContextPath()%><pwm:url url="/admin/config.jsp"/>">here</a>.
            If you wish to make configuration changes to this PWM installation, you can take any one
            of the following steps:</p>
        <ol>
            <li>Edit the <span style="font-style: italic;">PwmConfiguration.xml</span> file property setting the property "configIsEditable" to "true".  After making this edit, you will be able
            to edit the configuration from this interface.</li>
            <li>Delete the <span style="font-style: italic;">PwmConfiguration.xml</span> file in the pwm servlet's <span
                    style="font-style: italic;">WEB-INF</span> directory.  After deleting the file, you will be able
            to edit the configuration from this interface.  All settings will be reset to their default.</li>
            <li>Edit the <span style="font-style: italic;">PwmConfiguration.xml</span> settings directly.  It is not required to use the PwmConfiguration interface to modify the configuration file.</li>
        </ol>
        <hr/>
        <p>For convenience, the configuration editor can now be used to edit an in-memory configuration that can then be downloaded.  The in-memory configuration is initialized
        to default values, and any changes made are <span style="font-weight: bold; font-style:italic;">not</span> saved to the running configuration.</p>

        <% if (PwmSession.getSessionStateBean(session).getSessionError() != null) { %>
        <span id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
        <% } %>
        <h2><a href="#" onclick="document.forms['switchToEditMode'].submit();">Configuration Editor</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="switchToEditMode" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="switchToEditMode"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Edit the in-memory PWM configuration file.</p>
        <h2><a href="#" onclick="document.forms['uploadXml'].submit();">Upload Configuration File</a></h2>
        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" class="btn" name="uploadFile" size="50"/>
            <input type="submit" class="btn" name="uploadSubmit" value="Upload" onclick="document.forms['uploadXml'].submit();"/>
        </form>
        <p>Upload a previously saved configuration file.  The uploaded file will be available for editing during this browser session only.</p>
        <h2><a href="#" onclick="document.forms['generateXml'].submit();">Download Configuration File</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="generateXml" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="generateXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Download the in memory configuration to an XML file.  Save the file to PWM's <span style="font-style: italic;">WEB-INF </span> directory to change the configuration.</p>         
    </div>
</div>
<%@ include file="footer.jsp" %>
</body>
</html>
