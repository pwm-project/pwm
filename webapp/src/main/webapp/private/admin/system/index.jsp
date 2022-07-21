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


<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTag" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.*" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="../../../WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="../../../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Admin"/>
    </jsp:include>

    <div id="centerbody" class="tile-centerbody">
        <div style="text-align: center; margin-bottom: 10px;">
            <form action="<pwm:url addContext="true" url="/private"/>" method="get" id="nav-main" name="admin">
                <button type="submit" class="navbutton">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-arrow-up"></span></pwm:if>
                    <pwm:display key="Title_MainPage" bundle="Display"/>
                </button>
            </form>
        </div>
        <div style="text-align: center; margin-bottom: 10px;">
            <form action="<pwm:url addContext="true" url="/private/admin"/>" method="get" id="nav-admin" name="admin">
                <button type="submit" class="navbutton">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-arrow-up"></span></pwm:if>
                    <pwm:display key="Title_Admin" bundle="Display"/>
                </button>
            </form>
        </div>
        <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
            <a id="button_reporting" href="<pwm:url url='<%=PwmServletDefinition.SystemAdmin_Certificates.servletUrl()%>' addContext="true"/> ">
                <div class="tile">
                    <div class="tile-content">
                        <div class="tile-image admin-image"></div>
                        <div class="tile-title" title="<pwm:display key='Title_Admin_System_Certificates' bundle="Admin"/>"><pwm:display key="Title_Admin_System_Certificates" bundle="Admin"/></div>
                        <div class="tile-subtitle" title="<pwm:display key='Title_Admin_System_Certificates' bundle="Admin"/>"><pwm:display key="Title_Admin_System_Certificates" bundle="Admin"/></div>
                    </div>
                </div>
            </a>
        </pwm:if>
    </div>
    <div class="push"></div>
</div>
<%@ include file="../../../WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
