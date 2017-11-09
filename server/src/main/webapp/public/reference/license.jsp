<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.util.java.XmlUtil" %>
<%@ page import="org.jdom2.Document" %>
<%@ page import="org.jdom2.Element" %>
<%@ page import="java.util.List" %>
<%@ page import="java.io.InputStream" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.Permission" %>

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
<body class="nihilo">
<link href="<pwm:url url='/public/resources/referenceStyle.css' addContext="true"/>" rel="stylesheet" type="text/css"/>
<% List<XmlUtil.DependencyInfo> dependencyInfos = XmlUtil.getLicenseInfos(); %>
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

        <% for (final XmlUtil.DependencyInfo dependencyInfo : dependencyInfos) { %>
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
                for (final XmlUtil.LicenseInfo licenseInfo : dependencyInfo.getLicenses()) { %>
            <div class="dependency-license">
                License:
                <a href="<%=licenseInfo.getLicenseUrl()%>" target="_blank" class="license-link">
                    <%=licenseInfo.getLicenseName()%>
                </a>
            </div>
            <% } %>
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
