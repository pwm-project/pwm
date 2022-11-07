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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="../../WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="../../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Admin"/>
    </jsp:include>

    <div id="centerbody" class="tile-centerbody">
        <div style="text-align: center; margin-bottom: 10px;">
            <form action="<pwm:url addContext="true" url="/private"/>" method="get" id="admin" name="admin">
                <button type="submit" class="navbutton">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-arrow-up"></span></pwm:if>
                    <pwm:display key="Title_MainPage" bundle="Display"/>
                </button>
            </form>
        </div>
        <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
            <a id="button_reporting" href="<pwm:url url='<%=PwmServletDefinition.DomainAdminReport.servletUrl()%>' addContext="true"/> ">
                <div class="tile">
                    <div class="tile-content">
                        <div class="tile-image admin-image"></div>
                        <div class="tile-title" title="<pwm:display key='Title_DirectoryReporting' bundle="Admin"/>"><pwm:display key="Title_DirectoryReporting" bundle="Admin"/></div>
                        <div class="tile-subtitle" title="<pwm:display key='Title_DirectoryReporting' bundle="Admin"/>"><pwm:display key="Title_DirectoryReporting" bundle="Admin"/></div>
                    </div>
                </div>
            </a>
        </pwm:if>
        <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
            <a id="button_userdebug" href="<pwm:url url='<%=PwmServletDefinition.DomainAdminUserDebug.servletUrl()%>' addContext="true"/> ">
                <div class="tile">
                    <div class="tile-content">
                        <div class="tile-image user-image"></div>
                        <div class="tile-title" title="<pwm:display key='Title_UserDebug' bundle="Admin"/>"><pwm:display key="Title_UserDebug" bundle="Admin"/></div>
                        <div class="tile-subtitle" title="<pwm:display key='Title_UserDebug' bundle="Admin"/>"><pwm:display key="Title_UserDebug" bundle="Admin"/></div>
                    </div>
                </div>
            </a>
        </pwm:if>
        <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
            <a id="button_userdebug" href="<pwm:url url='<%=PwmServletDefinition.DomainAdminStatistics.servletUrl()%>' addContext="true"/> ">
                <div class="tile">
                    <div class="tile-content">
                        <div class="tile-image user-image"></div>
                        <div class="tile-title" title="<pwm:display key='Title_Statistics' bundle="Admin"/>"><pwm:display key="Title_Statistics" bundle="Admin"/></div>
                        <div class="tile-subtitle" title="<pwm:display key='Title_Statistics' bundle="Admin"/>"><pwm:display key="Title_Statistics" bundle="Admin"/></div>
                    </div>
                </div>
            </a>
        </pwm:if>
        <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
            <a id="button_systemadmin" href="<pwm:url url='<%=PwmServletDefinition.SystemAdmin.servletUrl()%>' addContext="true"/> ">
                <div class="tile">
                    <div class="tile-content">
                        <div class="tile-image admin-image"></div>
                        <div class="tile-title" title="System Administration">System Administration</div>
                        <div class="tile-subtitle" title="System Administration">System Administration</div>
                    </div>
                </div>
            </a>
        </pwm:if>
    </div>
    <div class="push"></div>
</div>
<%@ include file="../../WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
