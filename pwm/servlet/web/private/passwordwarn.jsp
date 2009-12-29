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

<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="java.text.DateFormat" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final UserInfoBean uiBean = PwmSession.getUserInfoBean(request.getSession()); %>
<% final SessionStateBean ssBean = PwmSession.getSessionStateBean(request.getSession()); %>
<% final DateFormat dateFormatter = java.text.DateFormat.getDateInstance(DateFormat.FULL, ssBean.getLocale()); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="Title_PasswordWarning"/></jsp:include>
    <div id="centerbody">
        <p>
            <% if (uiBean.getPasswordExpirationTime() != null) { %>
            <pwm:Display key="Display_PasswordWarn" value1="<%= dateFormatter.format(uiBean.getPasswordExpirationTime()) %>"/>
            <% } else { %>
            <pwm:Display key="Display_PasswordNoExpire"/>
            <% } %>
        </p>
        <div id="buttonbar">
            <form action="<%=request.getContextPath()%>/private/<pwm:url url='ChangePassword'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input tabindex="1" type="submit" name="change_btn" class="btn"
                       value="    <pwm:Display key="Button_ChangePassword"/>    "/>
            </form>
            <br class="clear"/>
            <form action="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input tabindex="2" type="submit" name="continue_btn" class="btn"
                       value="    <pwm:Display key="Button_Continue"/>    "/>
                <input type="hidden"
                       name="processAction"
                       value="continue"/>
            </form>
        </div>
    </div>
    <br class="clear"/>
</div>
<jsp:include page="../jsp/footer.jsp"/>
</body>
</html>