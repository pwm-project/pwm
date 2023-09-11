<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2021 The PWM Project
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


<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ page import="password.pwm.http.bean.ForgottenPasswordBean" %>
<%@ page import="password.pwm.http.servlet.forgottenpw.ForgottenPasswordServlet"%>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%  final boolean resendEnabled = (Boolean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ForgottenPasswordResendTokenEnabled); %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%@ include file="fragment/header.jsp" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ForgottenPassword"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ForgottenPassword" displayIfMissing="true"/></h1>
        <% final ForgottenPasswordBean fpb = JspUtility.getSessionBean(pageContext, ForgottenPasswordBean.class); %>
        <% final String destination = fpb.getProgress().getTokenDestination().getDisplay(); %>
        <p><pwm:display key="Display_RecoverEnterCode" value1="<%=destination%>"/></p>
        <% if (resendEnabled) { %>
        <p><pwm:display key="Display_TokenResend"/></p>
        <p>
            <button type="button" id="button-resend-token" class="btn">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh"></span></pwm:if>
                <pwm:display key="Button_TokenResend"/>
            </button>
        </p>
        <br/>
        <% } %>
        <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form" autocomplete="off">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <%@ include file="/WEB-INF/jsp/fragment/token-form-field.jsp" %>
            <div class="buttonbar">
                <button type="submit" class="btn" name="search" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <% if (JspUtility.getAttribute(pageContext, PwmRequestAttribute.GoBackAction) != null) { %>
                <button type="submit" id="button-goBack" name="button-goBack" class="btn" form="form-goBack">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <% } %>
                <input type="hidden" name="processAction" value="<%=ForgottenPasswordServlet.ForgottenPasswordAction.enterCode%>"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                <pwm:if test="<%=PwmIfTest.showCancel%>">
                    <button type="submit" name="button" class="btn" id="button-sendReset" form="form-cancel">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                        <pwm:display key="Button_Cancel"/>
                    </button>
                </pwm:if>

            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<form id="form-goBack" action="<pwm:current-url/>" method="post">
    <input type="hidden" name="processAction" value="<%=ForgottenPasswordServlet.ForgottenPasswordAction.reset%>"/>
    <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=ForgottenPasswordServlet.ForgottenPasswordAction.reset%>"/>
    <input type="hidden" name="<%=PwmConstants.PARAM_RESET_TYPE%>" value="<%=JspUtility.getAttribute(pageContext, PwmRequestAttribute.GoBackAction)%>"/>
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<form id="form-cancel" action="<pwm:current-url/>" method="post">
    <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=ForgottenPasswordServlet.ForgottenPasswordAction.reset%>"/>
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
    import {PWM_FORGOTTENPW} from "<pwm:url url="/public/resources/js/forgottenpassword.js" addContext="true"/>";
    PWM_FORGOTTENPW.initForgottenPwEnterTokenPage();
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

