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


<%@ page import="password.pwm.http.tag.conditional.PwmIfTag" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper" class="login-wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Login"/>
    </jsp:include>
    <div id="centerbody">
        <noscript>
            <div class="pwm-status-message">
                <div class="pwm-status-message-title">
                    <i class="pwm-icon pwm-icon-status_warn_thick pwm-warn"></i>
                    <span><pwm:display key="Display_WarnJavaScriptNotEnabledTitle" displayIfMissing="true"/></span>
                </div>
                <p><pwm:display key="Display_WarnJavaScriptNotEnabledMessage" displayIfMissing="true"/></p>
            </div>
        </noscript>

        <h1 id="page-content-title"><pwm:display key="Title_Login" displayIfMissing="true"/></h1>
        <p>
            <span class="panel-login-display-message"><pwm:display key="Display_Login"/></span>
        </p>
        <form action="<pwm:current-url/>" method="post" name="login" enctype="application/x-www-form-urlencoded" id="login" autocomplete="off" class="pwm-form-captcha">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <div class="sign-in">
                <%@ include file="/WEB-INF/jsp/fragment/ldap-selector.jsp" %>
                <h2 class="loginFieldLabel"><label for="username"><pwm:display key="Field_Username"/></label></h2>
                <div class="formFieldWrapper">
                    <input type="text" name="username" id="username" title="<pwm:display key="Field_Username"/>" placeholder="<pwm:display key="Field_Username"/>" class="inputfield" <pwm:autofocus/> required="required">
                </div>
                <h2 class="loginFieldLabel"><label for="password"><pwm:display key="Field_Password"/></label></h2>
                <div class="formFieldWrapper">
                    <input type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" name="password" id="password" title="<pwm:display key="Field_Password"/>" placeholder="<pwm:display key="Field_Password"/>" required="required" class="inputfield passwordfield"/>
                </div>
                <%@ include file="/WEB-INF/jsp/fragment/captcha-embed.jsp"%>
                <div class="buttonbar">
                    <button type="submit" class="btn pwm-btn-submit" <pwm:autofocus/> name="button" id="submitBtn">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-sign-in"></span></pwm:if>
                        <pwm:display key="Button_Login"/>
                    </button>
                    <input type="hidden" name="processAction" value="login">
                    <pwm:if test="<%=PwmIfTest.forwardUrlDefined%>">
                        <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                    </pwm:if>
                    <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                </div>
            </div>
        </form>
        <pwm:if test="<%=PwmIfTest.endUserFunctionalityAvailable%>">
            <pwm:if test="<%=PwmIfTest.showLoginOptions%>">
                <table class="noborder">
                    <pwm:if test="<%=PwmIfTest.forgottenPasswordEnabled%>">
                        <tr>
                            <td class="menubutton_key">
                                <a class="menubutton" tabindex="0" id="Title_ForgottenPassword" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ForgottenPassword.servletUrl()%>'/>">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-unlock"></span></pwm:if>
                                    <pwm:display key="Title_ForgottenPassword"/>
                                </a>
                            </td>
                            <td class="menubutton-description">
                                <p><pwm:display key="Long_Title_ForgottenPassword"/></p>
                            </td>
                        </tr>
                    </pwm:if>
                    <pwm:if test="<%=PwmIfTest.forgottenUsernameEnabled%>">
                        <tr>
                            <td class="menubutton_key">
                                <a class="menubutton" tabindex="0" id="forgotten-username" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ForgottenUsername.servletUrl()%>'/>">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-unlock"></span></pwm:if>
                                    <pwm:display key="Title_ForgottenUsername"/>
                                </a>
                            </td>
                            <td class="menubutton-description">
                                <p><pwm:display key="Long_Title_ForgottenUsername"/></p>
                            </td>
                        </tr>
                    </pwm:if>
                    <pwm:if test="<%=PwmIfTest.activateUserEnabled%>">
                        <tr>
                            <td class="menubutton_key">
                                <a class="menubutton" tabindex="0" id="activate-user" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ActivateUser.servletUrl()%>'/>">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-graduation-cap"></span></pwm:if>
                                    <pwm:display key="Title_ActivateUser"/>
                                </a>
                            </td>
                            <td class="menubutton-description">
                                <p><pwm:display key="Long_Title_ActivateUser"/></p>
                            </td>
                        </tr>
                    </pwm:if>
                    <pwm:if test="<%=PwmIfTest.newUserRegistrationEnabled%>">
                        <tr>
                            <td class="menubutton_key">
                                <a class="menubutton" tabindex="0" id="new-user" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.NewUser.servletUrl()%>'/>">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-file-text-o"></span></pwm:if>
                                    <pwm:display key="Title_NewUser"/>
                                </a>
                            </td>
                            <td class="menubutton-description">
                                <p><pwm:display key="Long_Title_NewUser"/></p>
                            </td>
                        </tr>
                    </pwm:if>
                </table>
            </pwm:if>
        </pwm:if>
        <pwm:if test="<%=PwmIfTest.configurationOpen%>">
            <table class="noborder">
                <tr>
                    <td colspan="2"><pwm:display key="Header_ConfigModeActive" bundle="Admin" value1="<%=PwmConstants.PWM_APP_NAME%>"/> </td>
                </tr>
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" tabindex="0" id="button-configmanager" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ConfigManager.servletUrl()%>'/>">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-file-text-o"></span></pwm:if>
                            Configuration Manager
                        </a>
                    </td>
                    <td class="menubutton-description">
                        .
                    </td>
                </tr>
                <tr>
                    <td class="menubutton_key">
                        <a class="menubutton" tabindex="0" id="button-configeditor" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ConfigEditor.servletUrl()%>'/>">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-file-text-o"></span></pwm:if>
                            Configuration Editor
                        </a>
                    </td>
                    <td class="menubutton-description">
                        .
                    </td>
                </tr>
            </table>
        </pwm:if>
    </div>
    <div class="push"></div>
</div>
<pwm:if test="<%=PwmIfTest.forwardUrlDefined%>">
    <%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
</pwm:if>
<% if (CaptchaUtility.captchaEnabledForRequest(JspUtility.getPwmRequest(pageContext))) { %>
<% if (CaptchaUtility.readCaptchaMode( JspUtility.getPwmRequest( pageContext ) ) == CaptchaUtility.CaptchaMode.V3 ) { %>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('login','submit',function(event){
                PWM_MAIN.handleFormSubmit(PWM_MAIN.getObject('login'),event);
            });
        });
    </script>
</pwm:script>
<% } %>
<% } else { %>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('login','submit',function(event){
                PWM_MAIN.handleLoginFormSubmit(PWM_MAIN.getObject('login'),event);
            });
        });
    </script>
</pwm:script>
<% } %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
