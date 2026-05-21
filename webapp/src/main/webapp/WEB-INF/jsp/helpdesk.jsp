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
  Issue #729: opt-in toggle for the modern (Lit) helpdesk client.  Add
  ?modernUi=1 to the URL to load the new pwm-client-modern bundle; default
  remains the legacy AngularJS bundle until parity is reached.  Once every
  helpdesk surface is migrated this conditional collapses to the modern
  branch and the legacy branch is removed.
--%>
<% final boolean useModernHelpdesk = "1".equals(request.getParameter("modernUi")); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<head>
    <%@ include file="/WEB-INF/jsp/fragment/header-common.jsp" %>
    <% if (!useModernHelpdesk) { %>
    <link rel="stylesheet" type="text/css" href="<pwm:url url='/public/resources/webjars/pwm-client/vendor/ux-ias/ias-icons.css' addContext="true"/>"/>
    <% } %>
</head>
<body>
<div id="wrapper" class="helpdesk-wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody" class="wide tall">
        <% if (useModernHelpdesk) { %>
        <pwm-helpdesk></pwm-helpdesk>
        <% } else { %>
        <ui-view id="helpdesk-view" class="ias-styles-root"><div class="WaitDialogBlank"></div></ui-view>
        <% } %>

        <noscript>
            <span><pwm:display key="Display_JavascriptRequired"/></span>
            <a href="<pwm:context/>"><pwm:display key="Title_MainPage"/></a>
        </noscript>
    </div>
    <div class="push"></div>
</div>

<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>

<% if (useModernHelpdesk) { %>
<pwm:script-ref url="/public/resources/webjars/pwm-client-modern/helpdesk.js" />
<% } else { %>
<pwm:script-ref url="/public/resources/webjars/pwm-client/vendor.js" />
<pwm:script-ref url="/public/resources/webjars/pwm-client/helpdesk.ng.js" />
<% } %>

</body>
</html>
