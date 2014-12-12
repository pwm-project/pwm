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

<%@page import="password.pwm.http.bean.SetupOtpBean"%>
<%@page import="password.pwm.util.otp.OTPUserRecord"%>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = PwmSession.getPwmSession(session).getSetupOtpBean();%>
<%
    final OTPUserRecord otpUserRecord = otpBean.getOtpUserRecord();
    final String ident = otpUserRecord.getIdentifier();
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<script type="text/javascript" defer="defer" src="<pwm:context/><pwm:url url='/public/resources/js/responses.js'/>"></script>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:display key="Success_Unknown" bundle="Message"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <% if (otpBean.getRecoveryCodes() != null && !otpBean.getRecoveryCodes().isEmpty()) %>
        <table style="text-align: center">
            <tr>
                <td><b> Recovery codes for <%=otpBean.getOtpUserRecord().getIdentifier()%></b></td>
            </tr>
            <tr>
                <td> Each of these recovery codes can be used exactly one time in the event that you can not access your phone.  Be sure to
                <a id="link-print">print this page </a> or otherwise copy these codes and and store the print out in a safe place.  Do not
                copy and paste these codes on to your computer.</td>
            </tr>
            <tr>
                <td>
                <% for (final String code : otpBean.getRecoveryCodes()) { %>
                <%= code %>
                <br/>
                <% } %>
                </td>
            </tr>
        </table>
        <div id="buttonbar">
            <form action="<pwm:url url='SetupOtp'/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form">
                <% try { PwmSession.getPwmSession(session).getSessionStateBean().setSessionSuccess(null,null); } catch (Exception e) {} %>
                <div id="buttonbar">
                    <input type="hidden" name="processAction" value="complete"/>
                    <button type="submit" name="button" class="btn" id="submitBtn">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
                        <pwm:display key="Button_Continue"/>
                    </button>
                    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                </div>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dojo/ready","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog","dojo/domReady!"],function(dojoParser,ready){
        });
        PWM_MAIN.addEventHandler('link-print','click',function(){ window.print(); });
    });
</script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
