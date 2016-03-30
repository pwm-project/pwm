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
<%@ page import="password.pwm.http.bean.ForgottenPasswordBean" %>
<%@ page import="password.pwm.http.JspUtility"%>
<%@ page import="password.pwm.util.otp.OTPUserRecord" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%@ include file="fragment/header.jsp" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ForgottenPassword"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_ForgottenPassword" displayIfMissing="true"/></div>
        <% final ForgottenPasswordBean fpb = JspUtility.getSessionBean(pageContext, ForgottenPasswordBean.class); %>
        <%
            OTPUserRecord otp = fpb.getUserInfo().getOtpUserRecord();
            String identifier = otp.getIdentifier();

            if (identifier != null && identifier.length() > 0 ) {
        %>
        <p><pwm:display key="Display_RecoverOTPIdentified" value1="<%=identifier%>"/></p>
        <% } else { %>
        <p><pwm:display key="Display_RecoverOTP" /></p>
        <% } %>
        <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form" autocomplete="off">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <h2><label for="<%=PwmConstants.PARAM_TOKEN%>"><pwm:display key="Field_Code"/></label></h2>
            <input type="text" pattern="[0-9]*" id="<%=PwmConstants.PARAM_TOKEN%>" name="<%=PwmConstants.PARAM_TOKEN%>" class="inputfield" required="required" autofocus/>
            <div class="buttonbar">
                <button type="submit" class="btn" name="search" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <% if ("true".equals(JspUtility.getAttribute(pageContext, PwmRequest.Attribute.ForgottenPasswordOptionalPageView))) { %>
                <button type="button" id="button-goBack" name="button-goBack" class="btn" >
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <% } %>
                <%@ include file="/WEB-INF/jsp/fragment/forgottenpassword-cancel.jsp" %>
                <input type="hidden" id="processAction" name="processAction" value="enterOtp"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script>
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('button-goBack','click',function() {
               PWM_MAIN.submitPostAction('<%=PwmServletDefinition.ForgottenPassword.servletUrlName()%>', '<%=ForgottenPasswordServlet.ForgottenPasswordAction.verificationChoice%>');
            });
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

