<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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

<%@page import="password.pwm.bean.servlet.SetupOtpBean"%>
<%@page import="password.pwm.util.otp.OTPUserRecord"%>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = PwmSession.getPwmSession(session).getSetupOtpBean();%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<script type="text/javascript" defer="defer" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/responses.js'/>"></script>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_SetupOtpSecret"/></p>
        <form action="<pwm:url url='SetupOtpSecret'/>" method="post" name="setupOtpSecret"
              enctype="application/x-www-form-urlencoded" onchange="" id="setupOtpSecret"
              onsubmit="PWM_MAIN.handleFormSubmit('setotpsecret_button', this); return false;">
            <%@ include file="fragment/message.jsp" %>
            <script type="text/javascript">PWM_GLOBAL['responseMode'] = "user";</script>
            <%
                final OTPUserRecord otpUserRecord = otpBean.getOtpUserRecord();
                final String ident = otpUserRecord.getIdentifier();
            %>
            <div id="qrcodeblock" style="visibility: visible" class="qrcodeblock">
                <img class="qrcodeimage" src="SetupOtpSecret?processAction=showQrImage&width=250&height=250&pwmFormID=<pwm:FormID/>" alt="QR code" width="250" height="250"/>
                <div style="width: 100%;">
                    <table border="0" style="width: 400px; margin-right: auto; margin-left: auto">
                        <tr valign="top">
                            <td><b><pwm:Display key="Field_OTP_Identifier"/></b></td>
                            <td><%=otpUserRecord.getIdentifier()%></td>
                        </tr>
                        <tr valign="top">
                            <td><b><pwm:Display key="Field_OTP_Secret"/></b></td>
                            <td><%=otpUserRecord.getSecret()%></td>
                        </tr>
                        <tr valign="top">
                            <td><b><pwm:Display key="Field_OTP_Type"/></b></td>
                            <td><%=otpUserRecord.getType().toString()%></td>
                        </tr>
                        <% if (otpBean.getRecoveryCodes()!= null && otpBean.getRecoveryCodes().size() > 0) {%>
                        <tr valign="top">
                            <td><b><pwm:Display key="Field_OTP_RecoveryCodes"/></b></td>
                            <td><% for (String code : otpBean.getRecoveryCodes()) {%>
                                <div><%=code%></div>
                                <% }%>
                            </td>
                        </tr>
                        <% }%>
                    </table>
                </div>
            </div>
            <div id="buttonbar">
                <input type="hidden" name="processAction" value="toggleSeen"/>
                <button type="submit" name="continue" class="btn" id="continuebutton">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:Display key="Button_Continue"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    // todo
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
