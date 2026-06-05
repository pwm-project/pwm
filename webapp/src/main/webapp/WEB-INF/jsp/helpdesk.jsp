<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2023 The PWM Project
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
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%--
  Issue #729: helpdesk is served by the modern (Lit) pwm-client-modern bundle.
  The legacy AngularJS client was removed once the migration reached parity.
--%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<head>
    <%@ include file="/WEB-INF/jsp/fragment/header-common.jsp" %>
</head>
<body>
<div id="wrapper" class="helpdesk-wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody" class="wide tall">
        <pwm-helpdesk></pwm-helpdesk>

        <noscript>
            <span><pwm:display key="Display_JavascriptRequired"/></span>
            <a href="<pwm:context/>"><pwm:display key="Title_MainPage"/></a>
        </noscript>
    </div>
    <div class="push"></div>
</div>

<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>

<pwm:script-ref url="/public/resources/webjars/pwm-client-modern/helpdesk.js" />

</body>
</html>
