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

<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="java.text.DateFormat" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final UserInfoBean uiBean = PwmSession.getPwmSession(session).getUserInfoBean(); %>
<% final SessionStateBean ssBean = PwmSession.getPwmSession(session).getSessionStateBean(); %>
<% final DateFormat dateFormatter = java.text.DateFormat.getDateInstance(DateFormat.FULL, ssBean.getLocale()); %>
<% final DateFormat timeFormatter = java.text.DateFormat.getTimeInstance(DateFormat.FULL, ssBean.getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PasswordWarning"/>
    </jsp:include>
    <div id="centerbody">
        <p>
            <% if (uiBean.getPasswordExpirationTime() != null) { %>
            <pwm:Display key="Display_PasswordWarn"
                         value1="<%= dateFormatter.format(uiBean.getPasswordExpirationTime()) %>"
                         value2="<%= timeFormatter.format(uiBean.getPasswordExpirationTime()) %>"/>
            <% } else { %>
            <pwm:Display key="Display_PasswordNoExpire"/>
            <% } %>
        </p>

        <div id="buttonbar">
            <form action="<%=request.getContextPath()%>/private/<pwm:url url='ChangePassword'/>" method="post"
                  enctype="application/x-www-form-urlencoded" style="display: inline;">
                <input tabindex="1" type="submit" name="change_btn" class="btn"
                       value="    <pwm:Display key="Button_ChangePassword"/>    "/>
            </form>
            <form action="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>" method="post"
                  enctype="application/x-www-form-urlencoded" style="display: inline;">
                <input tabindex="2" type="submit" name="continue_btn" class="btn"
                       value="    <pwm:Display key="Button_Continue"/>    "/>
                <input type="hidden"
                       name="processAction"
                       value="continue"/>
                <input type="hidden"
                       name="passwordWarn"
                       value="skip"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
