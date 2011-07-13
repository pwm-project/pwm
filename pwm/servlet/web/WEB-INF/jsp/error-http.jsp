<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="java.io.PrintWriter" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2011 The PWM Project
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%@ page isErrorPage="true" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="header.jsp" %>
<% final int statusCode = pageContext.getErrorData().getStatusCode(); %>
<body onload="pwmPageLoadHandler();" class="tundra">
<div id="wrapper">
    <jsp:include page="header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Error"/>
    </jsp:include>
    <div id="centerbody">
        <br/>
        <h2>HTTP <%=statusCode%></h2>
        <br/>
        <br/>
        <span id="error_msg" class="msg-error">
            <% if (404 == statusCode) { %>
            <%=PwmError.getLocalizedMessage(PwmSession.getPwmSession(session).getSessionStateBean().getLocale(),PwmError.ERROR_HTTP_404,null)%>
            <% } else { %>
            <%=PwmError.getLocalizedMessage(PwmSession.getPwmSession(session).getSessionStateBean().getLocale(),PwmError.ERROR_UNKNOWN,null)%>
            <% } %>
        </span>
        <br/>
        <br/>
        <% if (500 == statusCode) { %>
        <textarea rows="10" style="width: 600px; font-size:small;" readonly="true">
            <%=StringEscapeUtils.escapeHtml(pageContext.getErrorData().getThrowable().toString())%>
            <% for (final StackTraceElement stElement : pageContext.getErrorData().getThrowable().getStackTrace()) { %>
            <%=StringEscapeUtils.escapeHtml(stElement.toString())%>
            <% } %>
        </textarea>
        <% } %>
        <br/>
        <br/>
        <div id="buttonbar">
            <form action="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input type="hidden"
                       name="processAction"
                       value="continue"/>
                <input type="submit" name="button" class="btn"
                       value="    <pwm:Display key="Button_Continue"/>    "
                       id="button_continue"/>
            </form>
        </div>
    </div>
    <br class="clear"/>
</div>
<%@ include file="footer.jsp" %>
</body>
</html>
