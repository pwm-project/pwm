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

<%@page import="password.pwm.http.bean.SetupOtpBean"%>
<%@page import="password.pwm.http.tag.conditional.PwmIfTest"%>
<%@ page import="password.pwm.util.otp.OTPUserRecord" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final SetupOtpBean otpBean = JspUtility.getSessionBean(pageContext,SetupOtpBean.class); %>
<%
    final OTPUserRecord otpUserRecord = otpBean.getOtpUserRecord();
    final String ident = otpUserRecord.getIdentifier();
%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<pwm:script-ref url="/public/resources/js/responses.js"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_SetupOtpSecret" displayIfMissing="true"/></div>
        <p><pwm:display key="Success_OtpSetup" bundle="Message"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <% if (otpBean.getRecoveryCodes() != null && !otpBean.getRecoveryCodes().isEmpty()) %>
        <table style="text-align: center">
            <tr>
                <td><b><%=ident%></b></td>
            </tr>
            <tr>
                <td>
                    <pwm:display key="Display_OtpRecoveryInfo"/>
                </td>
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
        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form">
                <div class="buttonbar">
                    <input type="hidden" name="processAction" value="complete"/>
                    <button type="submit" name="button" class="btn" id="submitBtn">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                        <pwm:display key="Button_Continue"/>
                    </button>
                    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                </div>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
