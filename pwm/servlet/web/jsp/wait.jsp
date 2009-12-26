<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<meta http-equiv="refresh" content="0;url='<pwm:url url="<%=PwmSession.getSessionStateBean(session).getPostWaitURL()%>"/>'">
<%@ include file="header.jsp" %>
<body onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="Title_PleaseWait"/></jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_PleaseWaitBody"/></p>
        <%  //check to see if there is an error
            if (PwmSession.getSessionStateBean(session).getSessionError() != null) {
        %>
            <span id="error_msg" class="msg-error">
                <pwm:ErrorMessage/>
            </span>
        <% } %>
        <div id="buttonbar">
            <img src="<%=request.getContextPath()%>/resources/<pwm:url url='wait.gif'/>" alt="<pwm:Display key="Title_PleaseWait"/>"/>            </div>
    </div>
    <br class="clear"/>
</div>
<%@ include file="footer.jsp" %>
</body>
</html>
