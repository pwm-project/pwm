<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="java.text.DateFormat" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final UserInfoBean uiBean = JspUtility.getPwmSession(pageContext).getUserInfoBean(); %>
<% final SessionStateBean ssBean = JspUtility.getPwmSession(pageContext).getSessionStateBean(); %>
<% final DateFormat dateFormatter = java.text.DateFormat.getDateInstance(DateFormat.FULL, ssBean.getLocale()); %>
<% final DateFormat timeFormatter = java.text.DateFormat.getTimeInstance(DateFormat.FULL, ssBean.getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_PasswordWarning"/>
    </jsp:include>
    <div id="centerbody">
        <p>
            <% if (uiBean.getPasswordExpirationTime() != null) { %>
            <pwm:display key="Display_PasswordWarn"
                         value1="<%= dateFormatter.format(uiBean.getPasswordExpirationTime()) %>"
                         value2="<%= timeFormatter.format(uiBean.getPasswordExpirationTime()) %>"/>
            <% } else { %>
            <pwm:display key="Display_PasswordNoExpire"/>
            <% } %>
        </p>

        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input type="hidden" name="processAction" value="warnResponse"/>
                <input type="hidden" name="warnResponse" value="change"/>

                <button type="submit" name="changePassword" class="btn" id="changePassword">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-key"></span></pwm:if>
                    <pwm:display key="Button_ChangePassword"/>
                </button>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form action="<pwm:current-url/>" method="post" name="setupOtpSecret-skip"
                  enctype="application/x-www-form-urlencoded" id="setupOtpSecret-skip" class="pwm-form">
                <input type="hidden" name="processAction" value="warnResponse"/>
                <input type="hidden" name="warnResponse" value="skip"/>
                <button type="submit" name="skipbutton" class="btn" id="skipbutton">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-fighter-jet"></span></pwm:if>
                    <pwm:display key="Button_Skip"/>
                </button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
