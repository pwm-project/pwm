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


<%@ page import="password.pwm.http.bean.NewUserBean" %>
<%@ page import="password.pwm.http.servlet.newuser.NewUserServlet" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.bean.UpdateProfileBean" %>
<%@ page import="password.pwm.config.profile.UpdateProfileProfile" %>
<%@ page import="password.pwm.bean.TokenDestinationItem" %>
<%@ page import="password.pwm.http.servlet.updateprofile.UpdateProfileServlet" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UpdateProfile"/>
    </jsp:include>
    <div id="centerbody">
        <% TokenDestinationItem tokenDestinationItem = (TokenDestinationItem )JspUtility.getAttribute(pageContext, PwmRequestAttribute.TokenDestItems);%>
        <h1 id="page-content-title"><pwm:display key="Title_UpdateProfile" displayIfMissing="true"/></h1>
        <% if (tokenDestinationItem.getType() == TokenDestinationItem.Type.email) { %>
        <p><pwm:display key="Display_UpdateProfileEnterCode" value1="<%=tokenDestinationItem.getDisplay()%>"/></p>
        <% } else if (tokenDestinationItem.getType() == TokenDestinationItem.Type.sms) { %>
        <p><pwm:display key="Display_UpdateProfileEnterCodeSMS" value1="<%=tokenDestinationItem.getDisplay()%>"/></p>
        <% } %>
        <form action="<pwm:current-url/>" method="post" autocomplete="off" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form">
            <%@ include file="fragment/message.jsp" %>
            <%@ include file="/WEB-INF/jsp/fragment/token-form-field.jsp"%>
            <div class="buttonbar">
                <button type="submit" class="btn" name="search" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                    <pwm:display key="Button_CheckCode"/>
                </button>
                <input type="hidden" id="processAction" name="processAction" value="enterCode"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>

                <button id="button-reset" type="submit" class="btn" name="button" form="form-reset">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
            </div>
        </form>
        <form id="form-reset" name="form-reset" method="post" enctype="application/x-www-form-urlencoded"
              class="pwm-form">
            <input type="hidden" name="<%=PwmConstants.PARAM_RESET_TYPE%>" value="<%=UpdateProfileServlet.ResetAction.unConfirm%>"/>
            <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=UpdateProfileServlet.UpdateProfileAction.reset%>"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

