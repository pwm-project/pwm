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


<%@ page import="password.pwm.Permission" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.List" %>
<%@ page import="password.pwm.util.java.LicenseInfoReader" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body>
<link href="<pwm:url url='/public/resources/referenceStyle.css' addContext="true"/>" rel="stylesheet" type="text/css"/>
<% List<LicenseInfoReader.DependencyInfo> dependencyInfos = LicenseInfoReader.getLicenseInfos(); %>
<div id="wrapper">
    <jsp:include page="../../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Software License Reference"/>
    </jsp:include>

    <div id="centerbody" class="attribution-centerbody">
        <%@ include file="reference-nav.jsp"%>

        <p class="page-title">Software License Reference</p>

        <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>" negate="true">
            <p>
                Additional data is available for authenticated administrators.
            </p>
        </pwm:if>

        <% if (dependencyInfos != null) { %>
        <% for (final LicenseInfoReader.DependencyInfo dependencyInfo : dependencyInfos) { %>
        <div class="licenseBlock">
            <div class="dependency-name"><%=StringUtil.escapeHtml(dependencyInfo.getName())%></div>

            <% if (dependencyInfo.getProjectUrl() != null) { %>
            <div class="dependency-url">
                <a href="<%=dependencyInfo.getProjectUrl()%>" target="_blank">
                    <%=dependencyInfo.getProjectUrl()%>
                </a>
            </div>
            <% } else { %>
            <div class="dependency-url-not-provided">URL not provided</div>
            <% } %>
            <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
                <div class="dependency-file">
                    <%=dependencyInfo.getArtifactId() %>-<%=dependencyInfo.getVersion()%>.<%=dependencyInfo.getType() %>
                </div>
            </pwm:if>
            <%
                for (final LicenseInfoReader.LicenseInfo licenseInfo : dependencyInfo.getLicenses()) { %>
            <div class="dependency-license">
                License:
                <a href="<%=licenseInfo.getLicenseUrl()%>" target="_blank" class="license-link">
                    <%=licenseInfo.getLicenseName()%>
                </a>
            </div>
            <% } %>
        </div>
        <% } %>
        <% } else { %>
        <div class="licenseBlock">
            Error: attribution file not found: attribution.xml
        </div>
        <% } %>
        <pwm:if test="<%=PwmIfTest.permission%>" permission="<%=Permission.PWMADMIN%>">
            <p>
            <form action="source.zip" method="get">
                <button class="btn" type="submit">Download Source</button>
            </form>
            </p>
        </pwm:if>
        <span class="footnote">nanos gigantum humeris insidentes</span>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
