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
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url="/public/resources/installmanager.js"/>"></script>
<div id="wrapper">
    <div id="header">
        <div id="header-company-logo"></div>
        <div id="header-page">
            <pwm:Display key="Title_InstallManager" bundle="Config"/>
        </div>
        <div id="header-title">
            Configuration Mode: <%=ContextManager.getPwmApplication(session).getApplicationMode()%>
        </div>
    </div>
    <div id="centerbody">
        <pwm:Display key="Display_ConfigManagerNew" bundle="Config" value1="<%=PwmConstants.PWM_URL_HOME%>"/>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <br/>
        <% for (final PwmSetting.Template template : PwmSetting.Template.values()) { %>
        <p><a class="menubutton" href="#" onclick="selectTemplate('<%=template.toString()%>')"><%=template.getDescription()%></a></p>
        <% } %>
        <br/>
    </div>
</div>
<script type="text/javascript">
    require(["dojo/parser","dojo/domReady!","dojox/form/Uploader"],function(dojoParser){
        dojoParser.parse();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>