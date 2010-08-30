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
<link href="<%=request.getContextPath()%>/resources/dojo/dijit/themes/tundra/tundra.css" rel="stylesheet" type="text/css"/>
<link href="<%=request.getContextPath()%>/resources/dojo/dojox/grid/enhanced/resources/tundraEnhancedGrid.css" rel="stylesheet" type="text/css"/>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dojo/dojo.js" djConfig="parseOnLoad: true"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/dojo/dijit/dijit.js" djConfig="parseOnLoad: true"></script>
<script type="text/javascript" src="<%=request.getContextPath()%>/resources/configmanager.js"></script>
<div id="wrapper">
    <jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Configuration Manager"/></jsp:include>
    <div id="centerbody">
        <p>Welcome to PWM.  PWM is now in new configuration mode, which means you can make changes to the running configuration
            directly through this page.  Changes made in the configuration editor will be saved immediately, and PWM will automatically restart to have changes
            take effect.</p>
        <p>Once you have made all the required configuration changes, the configuration can be finalized to prevent users
            from being able to make changes.</p>
        <% if (PwmSession.getSessionStateBean(session).getSessionError() != null) { %>
        <span id="error_msg" class="msg-error"><pwm:ErrorMessage/></span>
        <% } %>
        <h2><a href="#" onclick="document.forms['switchToEditMode'].submit();">Configuration Editor</a></h2>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="switchToEditMode" enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="switchToEditMode"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <p>Start the PWM configuration editor.  When you select "Finished Editing" in the configuration page, your changes will be saved and
            you will return to this screen.</p>
        <h2><a href="#" onclick="document.forms['uploadXml'].submit();">Upload Configuration File</a></h2>
        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" class="btn" name="uploadFile" size="50"/>
            <input type="submit" class="btn" name="uploadSubmit" value="Upload" onclick="document.forms['uploadXml'].submit();"/>
        </form>
        <p>Upload a previously saved configuration file.  The uploaded file will be saved as the PWM configuration.</p>
    </div>
</div>
<%@ include file="footer.jsp" %>
</body>                                                  n
</html>
