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


<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>

<%@ taglib uri="pwm" prefix="pwm" %>
<div style="text-align: center">
    <form action="<pwm:context/><%=PwmServletDefinition.ConfigManager.servletUrl()%>" method="get">
        <button type="submit" class="navbutton">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-dashboard"></span></pwm:if>
            Overview
        </button>
    </form>
    <form action="<pwm:context/><%=PwmServletDefinition.ConfigManager_Certificates.servletUrl()%>" method="get">
        <button type="submit" class="navbutton">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-file-text-o"></span></pwm:if>
            Certificates
        </button>
    </form>
    <form action="<pwm:context/><%=PwmServletDefinition.ConfigManager_Wordlists.servletUrl()%>" method="get">
        <button type="submit" class="navbutton">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-book"></span></pwm:if>
            Word Lists
        </button>
    </form>
    <form action="<pwm:context/><%=PwmServletDefinition.ConfigManager_LocalDB.servletUrl()%>" method="get">
        <button type="submit" class="navbutton">
            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-database"></span></pwm:if>
            LocalDB
        </button>
    </form>
</div>
<br/>


