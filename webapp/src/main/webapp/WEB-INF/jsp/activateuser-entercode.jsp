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


<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ page import="password.pwm.http.bean.ActivateUserBean" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.servlet.activation.ActivateUserServlet" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%@ include file="fragment/header.jsp" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ActivateUser"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ActivateUser" displayIfMissing="true"/></h1>
        <% final ActivateUserBean activateUserBean = JspUtility.getSessionBean(pageContext, ActivateUserBean.class); %>
        <% final String destination = activateUserBean.getTokenDestination().getDisplay(); %>
        <p><pwm:display key="Display_RecoverEnterCode" value1="<%=destination%>"/></p>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <h2><label for="<%=PwmConstants.PARAM_TOKEN%>"><pwm:display key="Field_Code"/></label></h2>
        <div class="buttonbar">
            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form" style="display: inline;">
                <textarea id="<%=PwmConstants.PARAM_TOKEN%>" name="<%=PwmConstants.PARAM_TOKEN%>" class="tokenInput"></textarea>
                <button type="submit" class="btn" name="search" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span>&nbsp</pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <input type="hidden" id="processAction" name="processAction" value="enterCode"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>

            <% if ( JspUtility.getAttribute(pageContext, PwmRequestAttribute.GoBackAction) != null ) { %>
            <button type="submit" id="button-goBack" name="button-goBack" class="btn" form="form-goBack">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                <pwm:display key="Button_GoBack"/>
            </button>
            <% } %>

            <pwm:if test="<%=PwmIfTest.showCancel%>">
                <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded">
                    <input type="hidden" name="processAction" value="reset"/>
                    <button type="submit" name="button" class="btn" id="buttonCancel">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span>&nbsp</pwm:if>
                        <pwm:display key="Button_Cancel"/>
                    </button>
                    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                </form>
            </pwm:if>
        </div>
    </div>
    <div class="push"></div>
</div>
<% if ( JspUtility.getAttribute(pageContext, PwmRequestAttribute.GoBackAction) != null ) { %>
<form id="form-goBack" action="<pwm:current-url/>" method="post">
    <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=ActivateUserServlet.ActivateUserAction.reset%>"/>
    <input type="hidden" name="<%=PwmConstants.PARAM_RESET_TYPE%>" value="<%=JspUtility.getAttribute(pageContext, PwmRequestAttribute.GoBackAction)%>"/>
    <input type="hidden" name="<%=PwmConstants.PARAM_FORM_ID%>" value="<pwm:FormID/>"/>
</form>
<% } %>
<form id="form-cancel" action="<pwm:current-url/>" method="post">
    <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=ActivateUserServlet.ActivateUserAction.reset%>"/>
    <input type="hidden" name="<%=PwmConstants.PARAM_RESET_TYPE%>" value="<%=ActivateUserServlet.ResetType.exitActivation%>"/>
    <input type="hidden" name="<%=PwmConstants.PARAM_FORM_ID%>" value="<pwm:FormID/>"/>
</form>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

