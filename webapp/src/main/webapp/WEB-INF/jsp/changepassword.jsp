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
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.http.servlet.changepw.ChangePasswordServlet" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ChangePassword"/>
    </jsp:include>
    <div id="centerbody" ng-app="changepassword.module" ng-controller="ChangePasswordController as $ctrl">
        <h1 id="page-content-title"><pwm:display key="Title_ChangePassword" displayIfMissing="true"/></h1>
        <pwm:if test="<%=PwmIfTest.passwordExpired%>">
        <h1><pwm:display key="Display_PasswordExpired"/></h1><br/>
        </pwm:if>
        <p><pwm:display key="Display_ChangePassword"/></p>
        <div id="PasswordRequirements">
            <ul>
                <pwm:DisplayPasswordRequirements separator="</li>" prepend="<li>"/>
            </ul>
        </div>
        <%
            final String passwordPolicyChangeMessage = (String)JspUtility.getAttribute(pageContext, PwmRequestAttribute.ChangePassword_PasswordPolicyChangeMessage);
        %>
        <% if (passwordPolicyChangeMessage != null) { %>
        <p><%= passwordPolicyChangeMessage %></p>
        <% } %>
        <br/>
        <%@ include file="fragment/message.jsp" %>

        <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" id="changePasswordForm" autocomplete="off">
            <table class="noborder">
                <tr>
                    <td class="noborder">
                        <div style="width: 100%">
                            <h2 style="display: inline">
                                <label style="" for="password1"><pwm:display key="Field_NewPassword"/></label>
                            </h2>
                            &nbsp;&nbsp;
                            <div class="pwm-icon pwm-icon-question-circle icon_button" id="password-guide-icon" style="cursor: pointer; visibility: hidden"></div>
                            <pwm:if test="<%=PwmIfTest.showRandomPasswordGenerator%>">
                                &nbsp;&nbsp;
                                <div class="pwm-icon pwm-icon-retweet icon_button" id="autogenerate-icon" ng-click="$ctrl.doRandomGeneration()" style="cursor: pointer; visibility: hidden" ></div>
                            </pwm:if>
                        </div>
                        <input type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" name="password1" id="password1" class="changepasswordfield passwordfield" <pwm:autofocus/>/>
                    </td>
                    <td class="noborder" style="width:15%">
                        <pwm:if test="<%=PwmIfTest.showStrengthMeter%>">
                            <div id="strengthBox" style="visibility:hidden;">
                                <div id="strengthLabel" style="padding-top:40px;">
                                    <pwm:display key="Display_StrengthMeter"/>
                                </div>
                                <div class="progress-container" style="margin-bottom:10px">
                                    <div id="strengthBar" style="width: 0">&nbsp;</div>
                                </div>
                            </div>
                        </pwm:if>
                    </td>
                    <td class="noborder" style="width:10%">&nbsp;</td>
                </tr>
                <tr>
                    <td class="noborder" style="width:75%">
                        <h2 style="display: inline"><label for="password2"><pwm:display key="Field_ConfirmPassword"/></label></h2>
                        <input type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" name="password2" id="password2" class="changepasswordfield passwordfield"/>
                    </td>
                    <td class="noborder" style="width:15%">
                        <%-- confirmation mark [not shown initially, enabled by javascript; see also changepassword.js:markConfirmationMark() --%>
                        <div style="padding-top:45px;">
                            <img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15"
                                 src="<pwm:context/><pwm:url url='/public/resources/greenCheck.png'/>">
                            <img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15"
                                 src="<pwm:context/><pwm:url url='/public/resources/redX.png'/>">
                        </div>
                    </td>
                    <td class="noborder" style="width:10%">&nbsp;</td>
                </tr>
            </table>

            <input type="hidden" name="processAction" value="change"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>

            <div class="buttonbar" style="width:100%">
                <button type="submit" name="password_button" class="btn" id="password_button">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_ChangePassword"/>
                </button>
                <pwm:if test="<%=PwmIfTest.passwordExpired%>" negate="true">
                    <button id="button-reset" type="button" name="button-reset" class="btn" form="form-reset">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                        <pwm:display key="Button_Cancel"/>
                    </button>
                </pwm:if>
            </div>
        </form>
        <form id="form-reset" name="form-reset" action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" >
            <input type="hidden" name="processAction" value="<%=ChangePasswordServlet.ChangePasswordAction.reset%>"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_CHANGEPW.startupChangePasswordPage();
        });
    </script>
</pwm:script>

<pwm:script-ref url="/public/resources/js/changepassword.js"/>
<pwm:script-ref url="/public/resources/webjars/pwm-client/vendor.js" />
<pwm:script-ref url="/public/resources/webjars/pwm-client/changepassword.ng.js" />

<%@ include file="fragment/footer.jsp" %>
</body>
</html>
