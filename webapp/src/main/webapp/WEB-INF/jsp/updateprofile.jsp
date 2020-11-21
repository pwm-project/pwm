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


<%@ page import="password.pwm.http.servlet.updateprofile.UpdateProfileServlet" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UpdateProfile"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_UpdateProfile" displayIfMissing="true"/></h1>
        <jsp:include page="fragment/customlink.jsp"/>
        <br/>
        <p><pwm:display key="Display_UpdateProfile"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:current-url/>" method="post" name="updateProfileForm" enctype="application/x-www-form-urlencoded" autocomplete="off"
              class="pwm-form" id="updateProfileForm">

            <jsp:include page="fragment/form.jsp"/>
            <input type="hidden" name="processAction" value="updateProfile"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <div class="buttonbar">
                <button id="submitBtn" type="submit" class="btn" name="button">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_Update"/>
                </button>
                <pwm:if test="<%=PwmIfTest.showCancel%>">
                    <button type="submit" name="button-cancel" class="btn" id="button-cancel" form="form-hidden-cancel">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                        <pwm:display key="Button_Cancel"/>
                    </button>
                </pwm:if>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" id="form-hidden-cancel" name="form-hidden-cancel" class="pwm-form" autocomplete="off">
    <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=UpdateProfileServlet.UpdateProfileAction.reset%>"/>
    <input type="hidden" name="<%=PwmConstants.PARAM_RESET_TYPE%>" value="<%=UpdateProfileServlet.ResetAction.exitProfileUpdate%>"/>
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('updateProfileForm','input',function(){PWM_UPDATE.validateForm()});
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/updateprofile.js"/>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

