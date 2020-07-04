<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.Config" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<jsp:include page="/WEB-INF/jsp/fragment/header.jsp"/>
<body>
<meta http-equiv="refresh" content="0;url=<pwm:context/><pwm:url url="/private/config/manager"/>"/>
<div id="wrapper">
    <% final String PageName = JspUtility.localizedString(pageContext,"Title_UserActivity",Config.class);%>
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=PageName%>"/>
    </jsp:include>
    <div id="content">
        <div id="centerbody">
            <h1 id="page-content-title"><pwm:display key="Title_Configuration" bundle="Config" displayIfMissing="true"/></h1>
            <pwm:display key="Display_PleaseWait"/> <a href="<pwm:context/><pwm:url url="/private/config/manager"/>"><pwm:display bundle="Admin" key="MenuItem_ConfigManager"/></a>
        </div>
    </div>
    <br class="clear"/>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>

