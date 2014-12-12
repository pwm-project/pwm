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

<%@page import="password.pwm.config.option.ForceSetupPolicy"%>
<%@page import="password.pwm.http.bean.SetupOtpBean"%>
<%@ page import="password.pwm.util.otp.OTPUserRecord" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = PwmSession.getPwmSession(session).getSetupOtpBean();%>
<%
    final OTPUserRecord otpUserRecord = otpBean.getOtpUserRecord();
    final ForceSetupPolicy forceSetupPolicy = PwmRequest.forRequest(request,response).getConfig().readSettingAsEnum(PwmSetting.OTP_FORCE_SETUP, ForceSetupPolicy.class);
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Display_SetupOtpSecret"/></p>
        <%@ include file="fragment/message.jsp" %>
        <div data-dojo-type="dijit.layout.TabContainer" data-dojo-props="doLayout: false, persist: true">
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Display_SetupOtp_Android_Title"/>">
                <pwm:display key="Display_SetupOtp_Android_Steps"/>
                <img class="qrcodeimage" src="SetupOtp?processAction=showQrImage&width=150&height=150&pwmFormID=<pwm:FormID/>" alt="QR Code" width="150" height="150"/>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Display_SetupOtp_iPhone_Title"/>">
                <pwm:display key="Display_SetupOtp_iPhone_Steps"/>
                <img class="qrcodeimage" src="SetupOtp?processAction=showQrImage&width=150&height=150&pwmFormID=<pwm:FormID/>" alt="QR code" width="150" height="150"/>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Display_SetupOtp_Other_Title"/>">
                <pwm:display key="Display_SetupOtp_Other_Steps"/>
                <img class="qrcodeimage" src="SetupOtp?processAction=showQrImage&width=150&height=150&pwmFormID=<pwm:FormID/>" alt="QR code" width="150" height="150"/>
                <table border="0" style="width: 300px; margin-right: auto; margin-left: auto">
                    <tr valign="top">
                        <td><b><pwm:display key="Field_OTP_Identifier"/></b></td>
                        <td><%=otpUserRecord.getIdentifier()%></td>
                    </tr>
                    <tr valign="top">
                        <td><b><pwm:display key="Field_OTP_Secret"/></b></td>
                        <td><%=otpUserRecord.getSecret()%></td>
                    </tr>
                    <tr valign="top">
                        <td><b><pwm:display key="Field_OTP_Type"/></b></td>
                        <td><%=otpUserRecord.getType().toString()%></td>
                    </tr>
                </table>
            </div>
        </div>
        <div id="buttonbar">
            <form action="<pwm:url url='SetupOtp'/>" method="post" name="setupOtpSecret"
                  enctype="application/x-www-form-urlencoded" id="setupOtpSecret" class="pwm-form">
                <input type="hidden" name="processAction" value="toggleSeen"/>
                <button type="submit" name="continue" class="btn" id="continuebutton">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <% if (forceSetupPolicy == ForceSetupPolicy.FORCE_ALLOW_SKIP) { %>
            <form action="<pwm:url url='SetupOtp'/>" method="post" name="setupOtpSecret-skip"
                  enctype="application/x-www-form-urlencoded" id="setupOtpSecret-skip" class="pwm-form">
                <input type="hidden" name="processAction" value="skip"/>
                <button type="submit" name="continue" class="btn" id="skipbutton">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-fighter-jet"></span></pwm:if>
                    <pwm:display key="Button_Skip"/>
                </button>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <% } %>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dojo/ready","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog","dojo/domReady!"],function(dojoParser,ready){
            ready(function(){
                dojoParser.parse();
            });
        });
    });
</script>
</pwm:script>
<script type="text/javascript" defer="defer" src="<pwm:context/><pwm:url url='/public/resources/js/responses.js'/>"></script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
