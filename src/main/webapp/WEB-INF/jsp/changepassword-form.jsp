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
<%@ page import="password.pwm.bean.PasswordStatus" %>
<%@ page import="password.pwm.http.bean.ChangePasswordBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PasswordStatus passwordStatus = JspUtility.getPwmSession(pageContext).getUserInfoBean().getPasswordState(); %>
<% final ChangePasswordBean changePasswordBean = JspUtility.getSessionBean(pageContext, ChangePasswordBean.class); %>

<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ChangePassword"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_ChangePassword" displayIfMissing="true"/></div>
        <% if (passwordStatus.isExpired() || passwordStatus.isPreExpired() || passwordStatus.isViolatesPolicy()) { %>
        <h1><pwm:display key="Display_PasswordExpired"/></h1><br/>
        <% } %>
        <p><pwm:display key="Display_ChangePasswordForm"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form" name="changePasswordForm" id="changePasswordForm" autocomplete="off">
            <% if (changePasswordBean.isCurrentPasswordRequired()) { %>
            <h1>
                <label for="currentPassword"><pwm:display key="Field_CurrentPassword"/></label>
            </h1>
            <input id="currentPassword" type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" class="inputfield" name="currentPassword" <pwm:autofocus/>  />
            <br/>
            <% } %>
            <jsp:include page="fragment/form.jsp"/>
            <div class="buttonbar" style="width:100%">
                <input type="hidden" name="processAction" value="form"/>
                <button type="submit" name="change" class="btn" id="continue_button">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <% if (!passwordStatus.isExpired() && !passwordStatus.isPreExpired() && !passwordStatus.isViolatesPolicy()) { %>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <% } %>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>


