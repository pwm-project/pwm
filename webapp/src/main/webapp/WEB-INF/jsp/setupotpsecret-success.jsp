<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@page import="password.pwm.http.bean.SetupOtpBean"%>
<%@page import="password.pwm.http.tag.conditional.PwmIfTest"%>
<%@ page import="password.pwm.util.operations.otp.OTPUserRecord" %>
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
<body>
<pwm:script-ref url="/public/resources/js/responses.js"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_SetupOtpSecret"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_SetupOtpSecret" displayIfMissing="true"/></h1>
        <p><pwm:display key="Success_OtpSetup" bundle="Message"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <% if (otpBean.getRecoveryCodes() != null && !otpBean.getRecoveryCodes().isEmpty()) { %>
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
        <% } %>
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
