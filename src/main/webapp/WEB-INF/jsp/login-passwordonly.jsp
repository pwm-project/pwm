<%@ page import="password.pwm.util.StringUtil" %>
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
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper" class="login-wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Login"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_Login" displayIfMissing="true"/></div>
        <p><pwm:display key="Display_LoginPasswordOnly"/></p>
        <form action="<pwm:current-url/>" method="post" name="login-password" enctype="application/x-www-form-urlencoded" id="login-password" autocomplete="off" class="pwm-form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <h2><label for="password"><pwm:display key="Field_Password"/></label></h2>
            <input type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" name="password" id="password" class="inputfield passwordfield" placeholder="<pwm:display key="Field_Password"/>" <pwm:autofocus/> />
            <div class="buttonbar">
                <button type="submit" class="btn" name="button" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-sign-in"></span></pwm:if>
                    <pwm:display key="Button_Login"/>
                </button>
                <input type="hidden" name="processAction" value="login">
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                <input type="hidden" id="<%=PwmConstants.PARAM_POST_LOGIN_URL%>" name="<%=PwmConstants.PARAM_POST_LOGIN_URL%>"
                       value="<%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).readParameterAsString(PwmConstants.PARAM_POST_LOGIN_URL))%>"/>

            </div>
        </form>
        <br/>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>