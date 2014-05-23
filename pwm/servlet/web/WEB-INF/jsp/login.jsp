<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Login"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_Login"/></p>

        <form action="<pwm:url url='Login'/>" method="post" name="login" enctype="application/x-www-form-urlencoded" id="login"
              onsubmit="return PWM_MAIN.handleFormSubmit('submitBtn',this)">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <%@ include file="/WEB-INF/jsp/fragment/ldap-selector.jsp" %>
            <h2><label for="username"><pwm:Display key="Field_Username"/></label></h2>
            <input type="text" name="username" id="username" class="inputfield" required="required"
                   value="<pwm:ParamValue name='username'/>" autofocus/>

            <h2><label for="password"><pwm:Display key="Field_Password"/></label></h2>
            <input type="password" name="password" id="password" required="required" class="inputfield"/>

            <div id="buttonbar">
                <button type="submit" class="btn" name="button" id="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-sign-in"></span></pwm:if>
                    <pwm:Display key="Button_Login"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-reset.jsp" %>
                <input type="hidden" name="processAction" value="login">
                <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
        <br/>
        <pwm:if test="showLoginOptions">
        <table style="border:0">
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) { %>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="Title_ForgottenPassword" onclick="PWM_MAIN.showWaitDialog()" href="<%=request.getContextPath()%><pwm:url url='/public/ForgottenPassword'/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-unlock"></span></pwm:if>
                        <pwm:Display key="Title_ForgottenPassword"/>
                    </a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_ForgottenPassword"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_USERNAME_ENABLE)) { %>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="Title_ForgottenUsername" onclick="PWM_MAIN.showWaitDialog()" href="<%=request.getContextPath()%><pwm:url url='/public/ForgottenUsername'/>"><pwm:Display key="Title_ForgottenUsername"/></a>
                </td>
                <td style="border: 0">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-unlock"></span></pwm:if>
                    <p><pwm:Display key="Long_Title_ForgottenUsername"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE)) { %>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="Title_ActivateUser" onclick="PWM_MAIN.showWaitDialog()" href="<%=request.getContextPath()%><pwm:url url='/public/ActivateUser'/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-graduation-cap"></span></pwm:if>
                        <pwm:Display key="Title_ActivateUser"/>
                    </a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_ActivateUser"/></p>
                </td>
            </tr>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) { %>
            <tr style="border:0">
                <td style="border:0" class="menubutton_key">
                    <a class="menubutton" id="Title_NewUser" onclick="PWM_MAIN.showWaitDialog()" href="<%=request.getContextPath()%><pwm:url url='/public/NewUser'/>">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-file-text-o"></span></pwm:if>
                        <pwm:Display key="Title_NewUser"/>
                    </a>
                </td>
                <td style="border: 0">
                    <p><pwm:Display key="Long_Title_NewUser"/></p>
                </td>
            </tr>
            <% } %>
        </table>
        </pwm:if>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        ShowHidePasswordHandler.initAllForms();
        if (PWM_MAIN.getObject('username').value.length < 1) {
            PWM_MAIN.getObject('username').focus();
        } else {
            PWM_MAIN.getObject('password').focus();
        }
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
