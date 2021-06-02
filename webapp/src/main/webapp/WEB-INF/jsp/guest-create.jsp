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


<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final String maxValidDate = (String)JspUtility.getAttribute(pageContext, PwmRequestAttribute.GuestMaximumExpirationDate); %>
<% final String selectedDate = (String)JspUtility.getAttribute(pageContext, PwmRequestAttribute.GuestCurrentExpirationDate); %>
<% final String maxValidDays = (String)JspUtility.getAttribute(pageContext, PwmRequestAttribute.GuestMaximumValidDays); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_GuestRegistration"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_GuestRegistration" displayIfMissing="true"/></h1>
        <%@ include file="/WEB-INF/jsp/fragment/guest-nav.jsp" %>
        <p><pwm:display key="Display_GuestRegistration"/></p>

        <form action="<pwm:current-url/>" method="post" name="newGuest" enctype="application/x-www-form-urlencoded" class="pwm-form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <jsp:include page="fragment/form.jsp"/>
            <p>
                <label>
                    <pwm:display key="Display_ExpirationDate" value1="<%=String.valueOf(maxValidDays)%>"/>
                    <input name="<%=GuestRegistrationServlet.HTTP_PARAM_EXPIRATION_DATE%>" id="<%=GuestRegistrationServlet.HTTP_PARAM_EXPIRATION_DATE%>" type="hidden" required="true" value="<%=selectedDate%>"/>
                    <input name="expiredate-stub" id="expiredate-stub" type="date" required="true" value="<%=selectedDate%>"/>
                </label>
            </p>
            <pwm:script>
            </pwm:script>

            <div class="buttonbar">
                <input type="hidden" name="processAction" value="create"/>
                <button type="submit" name="Create" class="btn" id="submitBtn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-user-plus"></span></pwm:if>
                    <pwm:display key="Button_Create"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_GUEST.initDatePicker('<%=maxValidDate%>','<%=selectedDate%>');
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/guest.js"/>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
