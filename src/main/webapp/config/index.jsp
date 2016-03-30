<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<jsp:include page="/WEB-INF/jsp/fragment/header.jsp"/>
<body>
<meta http-equiv="refresh" content="0;url=<pwm:context/><pwm:url url="/private/config/ConfigManager"/>"/>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Configuration"/>
    </jsp:include>
    <div id="content">
        <div id="centerbody">
            <div id="page-content-title"><pwm:display key="Title_Configuration" bundle="Config" displayIfMissing="true"/></div>
            <pwm:display key="Display_PleaseWait"/> <a href="<pwm:context/><pwm:url url="/private/config/ConfigManage"/>"><pwm:display bundle="Admin" key="MenuItem_ConfigManager"/></a>
        </div>
    </div>
    <br class="clear"/>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>

