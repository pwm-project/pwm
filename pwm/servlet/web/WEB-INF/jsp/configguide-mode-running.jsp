<%@ page import="password.pwm.bean.servlet.ConfigManagerBean" %>
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
<body class="nihilo" onload="pwmPageLoadHandler();">
<%
    final ConfigManagerBean configManagerBean = PwmSession.getPwmSession(session).getConfigManagerBean();
    final boolean hasBeenModified = configManagerBean.getConfiguration() != null && configManagerBean.getConfiguration().hasBeenModified();
%>
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            <pwm:Display key="Title_ConfigManager" bundle="Config"/>
        </div>
        <div id="header-title">
            Configuration is locked
        </div>
    </div>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <% if (!hasBeenModified) { %>
        <pwm:Display key="Display_ConfigManagerRunning" bundle="Config" value1="<%=PwmConstants.CONFIG_FILE_FILENAME%>"/>
        <a class="menubutton" href="#" onclick="document.forms['uploadXml'].submit();"><pwm:Display key="MenuItem_UploadConfig" bundle="Config"/></a>
        <form action="<pwm:url url='ConfigUpload'/>" method="post" name="uploadXml" enctype="multipart/form-data">
            <input type="hidden" name="processAction" value="uploadXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="file" name="uploadFile" size="50" data-dojo-type="dojox/form/Uploader"/>
            <input type="submit" class="btn" name="uploadSubmit" value="Upload"
                   onclick="document.forms['uploadXml'].submit();"/>
        </form>
        <br/>
        <br/>
        <a class="menubutton"><pwm:Display key="MenuItem_AlternateUnlockConfig" bundle="Config"/></a>
        <p>
            <pwm:Display key="MenuDisplay_AlternateUnlockConfig" bundle="Config" value1="<%=PwmConstants.CONFIG_FILE_FILENAME%>"/>
        </p>
        <br/>
        <a class="menubutton" href="#" onclick="startConfigurationEditor();"><pwm:Display key="MenuItem_AlternateNewConfig" bundle="Config"/></a>
        <p>
        <p>
        <pwm:Display key="MenuDisplay_AlternateNewConfig" bundle="Config" value1="<%=PwmConstants.CONFIG_FILE_FILENAME%>"/>
        </p>
        <% } else { %>
        <p>
            <pwm:Display key="Display_ConfigManagerRunningEditor" bundle="Config"/>
        </p>
        <br/>
        <form action="<pwm:url url='ConfigManager'/>" method="post" name="generateXml"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="generateXml"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <a class="menubutton" href="#" onclick="document.forms['generateXml'].submit();"><pwm:Display key="MenuItem_DownloadConfig" bundle="Config"/></a>
        <p>
            <pwm:Display key="MenuDisplay_DownloadConfigRunning" bundle="Config" value1="<%=PwmConstants.CONFIG_FILE_FILENAME%>"/>
        </p>

        <br/>
        <a class="menubutton" href="#" onclick="startConfigurationEditor();"><pwm:Display key="MenuItem_ReturnToEditor" bundle="Config"/></a>
        <p>
            <pwm:Display key="MenuDisplay_ReturnToEditor" bundle="Config"/>
        </p>

        <form action="<pwm:url url='ConfigManager'/>" method="post" name="cancelEditing"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden" name="processAction" value="cancelEditing"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>

        <br/>
        <a class="menubutton" href="#" onclick="document.forms['cancelEditing'].submit();"><pwm:Display key="MenuItem_CancelEdits" bundle="Config"/></a>
        <p>
            <pwm:Display key="MenuDisplay_CancelEdits" bundle="Config"/>
        </p>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    require(["dojo/parser","dojo/domReady!","dojox/form/Uploader"],function(dojoParser){
        dojoParser.parse();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
