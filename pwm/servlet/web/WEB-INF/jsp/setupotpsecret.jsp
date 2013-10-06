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
<!--

TODO: show/hide setup data

-->
<%@page import="java.util.List"%>
<%@page import="java.net.URLEncoder"%>
<%@page import="password.pwm.util.otp.OTPUserConfiguration"%>
<%@ page import="password.pwm.bean.servlet.SetupOtpBean" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = PwmSession.getPwmSession(session).getSetupOtpBean();%>
<html dir="<pwm:LocaleOrientation/>">
    <%@ include file="fragment/header.jsp" %>
    <body class="nihilo" onload="pwmPageLoadHandler()">
        <script type="text/javascript" defer="defer" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/responses.js'/>"></script>
        <div id="wrapper">
            <jsp:include page="fragment/header-body.jsp">
                <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
            </jsp:include>
            <div id="centerbody">
                <p><pwm:Display key="Display_SetupOtpSecret"/></p>
                <form action="<pwm:url url='SetupOtpSecret'/>" method="post" name="setupOtpSecret"
                      enctype="application/x-www-form-urlencoded" onchange="" id="setupOtpSecret"
                      onsubmit="handleFormSubmit('setotpsecret_button', this);
            return false;">
                    <%@ include file="fragment/message.jsp" %>
                    <script type="text/javascript">PWM_GLOBAL['responseMode'] = "user";</script>
                    <%

                        OTPUserConfiguration otp = otpBean.getOtp();
                        String ident = otp.getIdentifier();
                        String secret = otp.getSecret();
                        String otptype = otp.getType().toString();
                        String otpInfo = String.format("otpauth://%s/%s?secret=%s", otptype.toLowerCase(), ident, secret);
                        String imageUrl = String.format("%s/public/qrcode.jsp?width=250&height=250&content=%s", request.getContextPath(), URLEncoder.encode(otpInfo));
                        List<String> recovery = otp.getRecoveryCodes();

                    %>

                    <div id="qrcodeblock" style="visibility: visible" class="qrcodeblock">
                        <img class="qrcodeimage" src="<%=imageUrl%>" alt="QR code" width="250" height="250"/>
                        <table border="0">
                            <tr valign="top">
                                <td><b><pwm:Display key="Field_OTP_Identifier"/></b></td>
                                <td><%=(ident == null) ? "PWM" : ident%></td>
                            </tr>
                            <tr valign="top">
                                <td><b><pwm:Display key="Field_OTP_Secret"/></b></td>
                                <td><%=secret%></td>
                            </tr>
                            <tr valign="top">
                                <td><b><pwm:Display key="Field_OTP_Type"/></b></td>
                                <td><%=otptype%></td>
                            </tr>
                            <% if (recovery != null && recovery.size() > 0) {%>
                            <tr valign="top">
                                <td><b><pwm:Display key="Field_OTP_RecoveryCodes"/></b></td>
                                <td><% for (String code : recovery) {%>
                                    <div><%=code%></div>
                                    <% }%>
                                </td>
                            </tr>
                            <% }%>
                        </table>
                    </div>
                    <div id="buttonbar">
                        <input type="hidden" name="processAction" value="testOtpSecret"/>
                        <input type="submit" name="continue" class="btn" id="continuebutton" value="<pwm:Display key="Button_Continue"/>"/>
                        <button name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false;">
                            <pwm:Display key="Button_Cancel"/>
                        </button>
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
