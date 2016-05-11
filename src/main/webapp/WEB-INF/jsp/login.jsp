<%@ page import="password.pwm.http.tag.conditional.PwmIfTag" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper" class="login-wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Login"/>
    </jsp:include>
    <div id="centerbody">
        <div id="page-content-title"><pwm:display key="Title_Login" displayIfMissing="true"/></div>
        <p>
            <span class="panel-login-display-message"><pwm:display key="Display_Login"/></span>
        </p>
        <form action="<pwm:current-url/>" method="post" name="login" enctype="application/x-www-form-urlencoded" id="login" autocomplete="off">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <%@ include file="/WEB-INF/jsp/fragment/ldap-selector.jsp" %>
            <div class="sign-in">
                <div style="margin-top: 15px;"><input type="text" name="username" id="username" placeholder="<pwm:display key="Field_Username"/>" class="inputfield" <pwm:autofocus/> required="required"></div>
                <div style="margin-top: 15px;"><input type="<pwm:value name="<%=PwmValue.passwordFieldType%>"/>" name="password" id="password" placeholder="<pwm:display key="Field_Password"/>" required="required" class="inputfield passwordfield"/></div>
                <input type="hidden" id="<%=PwmConstants.PARAM_POST_LOGIN_URL%>" name="<%=PwmConstants.PARAM_POST_LOGIN_URL%>"
                       value="<%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).readParameterAsString(PwmConstants.PARAM_POST_LOGIN_URL))%>"/>
                <div class="buttonbar">
                    <button type="submit" class="btn" <pwm:autofocus/> name="button" id="submitBtn">
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
        <pwm:if test="<%=PwmIfTest.endUserFunctionalityAvaiable%>">
            <pwm:if test="<%=PwmIfTest.showLoginOptions%>">
                <table class="noborder">
                    <pwm:if test="<%=PwmIfTest.forgottenPasswordEnabled%>">
                        <tr>
                            <td class="menubutton_key">
                                <a class="menubutton" id="Title_ForgottenPassword" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ForgottenPassword.servletUrl()%>'/>">
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
                                <a class="menubutton" id="forgotten-username" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ForgottenUsername.servletUrl()%>'/>">
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
                                <a class="menubutton" id="activate-user" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.ActivateUser.servletUrl()%>'/>">
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
                                <a class="menubutton" id="new-user" href="<pwm:url addContext="true" url='<%=PwmServletDefinition.NewUser.servletUrl()%>'/>">
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
    </div>
    <div class="push"></div>
</div>
<pwm:if test="<%=PwmIfTest.forwardUrlDefined%>">
    <%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
</pwm:if>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('login','submit',function(event){
                PWM_MAIN.handleLoginFormSubmit(PWM_MAIN.getObject('login'),event);
            });
        });
    </script>
</pwm:script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
