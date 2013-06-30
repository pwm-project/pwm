<%@ page import="password.pwm.error.ErrorInformation" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% ErrorInformation errorInfo;
    try {
        errorInfo = PwmSession.getPwmSession(session).getSessionStateBean().getSessionError();
    } catch (PwmUnrecoverableException e) {
        errorInfo = e.getErrorInformation();
    } %>
<%@ include file="fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Error"/>
    </jsp:include>
    <div id="centerbody">
        <br/>
        <h2><%=PwmConstants.PWM_APP_NAME%>&nbsp;<%=errorInfo == null ? "" : errorInfo.getError().getErrorCode()%></h2>
        <br/>
        <br/>
        <span id="message" class="message message-error"><pwm:ErrorMessage/></span>
        <% try { PwmSession.getPwmSession(session).getSessionStateBean().setSessionError(null); } catch (Exception e) {} %>
        <br/>
        <br/>
        <% if (pwmApplicationHeader != null && pwmApplicationHeader.getApplicationMode() != PwmApplication.MODE.ERROR) { %>
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
        <% } %>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
