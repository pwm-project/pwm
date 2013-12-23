<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2013 The PWM Project
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ page import="password.pwm.bean.servlet.ForgottenPasswordBean" %>
<%@ page import="password.pwm.util.otp.OTPUserConfiguration" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%@ include file="fragment/header.jsp" %>
<html dir="<pwm:LocaleOrientation/>">
    <body onload="pwmPageLoadHandler();" class="nihilo">
        <div id="wrapper">
            <jsp:include page="fragment/header-body.jsp">
                <jsp:param name="pwm.PageName" value="Title_ForgottenPassword"/>
            </jsp:include>
            <div id="centerbody">
                <%
                    final ForgottenPasswordBean fpb = PwmSession.getPwmSession(session).getForgottenPasswordBean();
                    OTPUserConfiguration otp = fpb.getOtpConfig();
                    String identifier = otp.getIdentifier();

                    if (identifier != null && identifier.length() > 0 ) {
                %>
                <p><pwm:Display key="Display_RecoverOTPIdentified" value1="<%=identifier%>"/></p>
                <% } else { %>
                <p><pwm:Display key="Display_RecoverOTP" /></p>
                <% } %>
                <form action="<pwm:url url='../public/ForgottenPassword'/>" method="post"
                      enctype="application/x-www-form-urlencoded" name="search"
                      onsubmit="handleFormSubmit('submitBtn', this);
                              return false">
                    <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
                    <h2><label for="<%=PwmConstants.PARAM_TOKEN%>"><pwm:Display key="Field_Code"/></label></h2>
                    <input type="password" id="<%=PwmConstants.PARAM_TOKEN%>" name="<%=PwmConstants.PARAM_TOKEN%>" class="inputfield" required="required"/>
                    <div id="buttonbar">
                        <input type="submit" class="btn"
                               name="search"
                               value="<pwm:Display key="Button_CheckCode"/>"
                               id="submitBtn"/>
                        <%@ include file="/WEB-INF/jsp/fragment/button-reset.jsp" %>
                        <input type="hidden" id="processAction" name="processAction" value="enterOtp"/>
                        <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
                        <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                    </div>
                </form>
            </div>
            <div class="push"></div>
        </div>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function() {
                getObject('<%=PwmConstants.PARAM_TOKEN%>').focus();
                ShowHidePasswordHandler.initAllForms();
            });
        </script>
        <%@ include file="fragment/footer.jsp" %>
    </body>
</html>

