<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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
<body onload="pwmPageLoadHandler()" class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Login"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_LoginPasswordOnly"/></p>
        <form action="<pwm:url url='Login'/>" method="post" name="login-password" enctype="application/x-www-form-urlencoded" id="login-password"
              onsubmit="return handleFormSubmit('submitBtn',this)">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <h2><label for="password"><pwm:Display key="Field_Password"/></label></h2>
            <input type="password" name="password" id="password" class="inputfield" autofocus/>
            <div id="buttonbar">
                <input type="submit" class="btn"
                       name="button"
                       value="<pwm:Display key="Button_Login"/>"
                       id="submitBtn"/>
                <input type="hidden" name="processAction" value="login">
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" type="button" name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
        <br/>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
