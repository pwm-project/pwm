<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.error.PwmException" %>
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
<%@ page isErrorPage="true" %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<html>
<%@ include file="fragment/header.jsp" %>
<% response.setHeader("Content-Encoding",""); //remove gzip encoding header %>
<% final int statusCode = pageContext.getErrorData().getStatusCode(); %>
<body class="nihilo" data-jsp-page="error-http.jsp">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Error"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_Error" displayIfMissing="true"/></div>
        <br/>
        <h2>HTTP <%=statusCode%></h2>
        <br/>
        <br/>
        <span id="message" class="message message-error">
            <% if (404 == statusCode) { %>
            <%=PwmError.ERROR_HTTP_404.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,null)%>
            <% } else { %>
            <%=PwmError.ERROR_UNKNOWN.getLocalizedMessage(PwmConstants.DEFAULT_LOCALE,null)%>
            <% } %>
        </span>
        <br/>
        <br/>
        <pwm:throwableHandler/>
        <br/>
        <br/>
        <div class="buttonbar">
            <form action="<pwm:url url='<%=PwmServletDefinition.Command.servletUrl()%>' addContext="true"/>" method="post" enctype="application/x-www-form-urlencoded">
                <input type="hidden"
                       name="processAction"
                       value="continue"/>
                <input type="submit" name="button" class="btn"
                       value="    <pwm:display key="Button_Continue"/>    "
                       id="button_continue"/>
            </form>
        </div>
        <br/>
        <br/>
        <br/>
        <br/>
        <br/>
        <br/>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
