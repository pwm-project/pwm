<%@ page import="password.pwm.config.Configuration" %>
<%--
~ Password Management Servlets (PWM)
~ http://code.google.com/p/pwm/
~
~ Copyright (c) 2006-2009 Novell, Inc.
~ Copyright (c) 2009-2011 The PWM Project
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

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();if (getObject('username').value.length < 1) { getObject('username').focus(); } else { getObject('password').focus(); }"
      class="tundra">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Login"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_Login"/></p>

        <form action="<pwm:url url='Login'/>" method="post" name="login" enctype="application/x-www-form-urlencoded"
              onsubmit="handleFormSubmit('submitBtn',this);return false" onreset="handleFormClear();return false"
              onkeypress="checkForCapsLock(event)">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <% //check to see if any locations are configured.
                if (!ContextManager.getPwmApplication(session).getConfig().getLoginContexts().isEmpty()) {
            %>
            <h2><label for="context"><pwm:Display key="Field_Location"/></label></h2>
            <select name="context" id="context">
                <pwm:DisplayLocationOptions name="context"/>
            </select>
            <% } %>
            <h2><label for="username"><pwm:Display key="Field_Username"/></label></h2>
            <input type="text" name="username" id="username" class="inputfield"
                   value="<pwm:ParamValue name='username'/>"/>

            <h2><label for="password"><pwm:Display key="Field_Password"/></label></h2>
            <input type="password" name="password" id="password" class="inputfield"/>

            <div id="buttonbar">
                <input type="submit" class="btn"
                       name="button"
                       value="    <pwm:Display key="Button_Login"/>    "
                       id="submitBtn"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" class="btn"
                       name="reset"
                       value="    <pwm:Display key="Button_Reset"/>    "/>
                <% } %>
                <input type="hidden" name="processAction" value="login">
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                        onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                    &nbsp;&nbsp;&nbsp;<pwm:Display key="Button_Cancel"/>&nbsp;&nbsp;&nbsp;
                </button>
                <script type="text/javascript">getObject('button_cancel').style.visibility = 'visible';</script>
                <% } %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
        <br/>
        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_PASSWORD_ENABLE)) { %>
        <p><a href="<%=request.getContextPath()%><pwm:url url='/public/ForgottenPassword'/>"><pwm:Display key="Title_ForgottenPassword"/></a>
        <pwm:Display key="Long_Title_ForgottenPassword"/></p>
        <% } %>
        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.FORGOTTEN_USERNAME_ENABLE)) { %>
        <p><a href="<%=request.getContextPath()%><pwm:url url='/public/ForgottenUsername'/>"><pwm:Display key="Title_ForgottenUsername"/></a>
        <pwm:Display key="Long_Title_ForgottenUsername"/></p>
        <% } %>
        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.ACTIVATE_USER_ENABLE)) { %>
        <p><a href="<%=request.getContextPath()%><pwm:url url='/public/ActivateUser'/>"><pwm:Display key="Title_ActivateUser"/></a>
        <pwm:Display key="Long_Title_ActivateUser"/></p>
        <% } %>
        <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.NEWUSER_ENABLE)) { %>
        <p><a href="<%=request.getContextPath()%><pwm:url url='/public/NewUser'/>"><pwm:Display key="Title_NewUser"/></a>
        <pwm:Display key="Long_Title_NewUser"/></p>
        <% } %>

    </div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
