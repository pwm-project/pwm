<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.util.XmlUtil" %>
<%@ page import="org.jdom2.Document" %>
<%@ page import="org.jdom2.Element" %>
<%@ page import="java.util.List" %>
<%@ page import="java.io.InputStream" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
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
<div id="wrapper">
    <jsp:include page="../../WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Software License Reference"/>
    </jsp:include>

    <div id="centerbody" class="attribution-centerbody">
        <%@ include file="reference-nav.jsp"%>

        <p class="page-title">Software License Reference</p>

        <%
        InputStream attributionInputStream = getClass().getResourceAsStream("/attribution.xml");

        if (attributionInputStream != null) {
            Document document = XmlUtil.parseXml(attributionInputStream);
            Element dependencies = document.getRootElement().getChild("dependencies");

            for (Element dependency : dependencies.getChildren("dependency")) {
                String projectUrl = dependency.getChildText("projectUrl");
            %>
                <div class="licenseBlock">
                    <div class="dependency-name"><%=dependency.getChildText("name") %></div>

                    <% if (projectUrl != null) { %>
                        <div class="dependency-url"><a href="<%=projectUrl%>" target="_blank"><%=projectUrl%></a></div>
                    <% } else { %>
                        <div class="dependency-url-not-provided">URL not provided</div>
                    <% } %>

                    <div class="dependency-file"><%=dependency.getChildText("artifactId") %>-<%=dependency.getChildText("version") %>.<%=dependency.getChildText("type") %></div>
                    <%
                    Element licenses = dependency.getChild("licenses");
                    List<Element> licenseList = licenses.getChildren("license");
                    for (Element license : licenseList) { %>
                    <div class="dependency-license"><a href="<%=license.getChildText("url")%>" target="_blank" class="license-link"><%=license.getChildText("name")%></a></div>
                    <% } %>
                </div>
            <% } %>
        <% } else { %>
            <div class="licenseBlock">
                Error: attribution file not found: attribution.xml
            </div>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/TitlePane"],function(dojoParser){
            dojoParser.parse();
        });
    });
</script>
</pwm:script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
