<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2021 The PWM Project
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

<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.servlet.admin.SystemAdminServlet" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.JspUtility" %>

<%@ taglib uri="pwm" prefix="pwm" %>
<% final SystemAdminServlet.Page currentPage = SystemAdminServlet.Page.forUrl(JspUtility.getPwmRequest(pageContext).getURL()).orElseThrow(); %>
<link href="<pwm:url url='/public/resources/webjars/dijit/themes/nihilo/nihilo.css' addContext="true"/>" rel="stylesheet" type="text/css"/>
<link href="<pwm:url url='/public/resources/webjars/dgrid/css/dgrid.css' addContext="true"/>" rel="stylesheet" type="text/css"/>
<script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
    import {PWM_ADMIN} from "<pwm:url url="/public/resources/js/admin.js" addContext="true"/>";
    PWM_ADMIN.initAdminNavMenu();
</script>
<div style="text-align: center; margin-bottom: 10px;">
    <% boolean selected = currentPage == SystemAdminServlet.Page.dashboard; %>
    <form action="<%=SystemAdminServlet.Page.dashboard%>" method="get" id="dashboard" name="dashboard">
        <button type="submit" class="navbutton<%=selected?" selected":""%>">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-dashboard"></span></pwm:if>
            <pwm:display key="Title_Dashboard" bundle="Admin"/>
        </button>
    </form>
    <% selected = currentPage == SystemAdminServlet.Page.activity; %>
    <form action="<%=SystemAdminServlet.Page.activity%>" method="get" id="activity" name="activity">
        <button type="submit" class="navbutton<%=selected?" selected":""%>">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-users"></span></pwm:if>
            <pwm:display key="Title_UserActivity" bundle="Admin"/>
        </button>
    </form>
    <div style="display: inline" id="admin-nav-menu-container">
    </div>
</div>
