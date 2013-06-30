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
        <jsp:param name="pwm.PageName" value="Title_UpdateProfileConfirm"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_UpdateProfileConfirm"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:url url='UpdateProfile'/>" method="post" name="updateProfile" enctype="application/x-www-form-urlencoded"
              onsubmit="return false">
            <% request.setAttribute("form",PwmSetting.UPDATE_PROFILE_FORM); %>
            <% request.setAttribute("formData",pwmSessionHeader.getUpdateProfileBean().getFormData()); %>
            <% request.setAttribute("form-readonly","true"); %>
            <jsp:include page="fragment/form.jsp"/>
        </form>
        <div id="buttonbar">
            <form style="display: inline" action="<pwm:url url='UpdateProfile'/>" method="post" name="confirm" enctype="application/x-www-form-urlencoded"
                  onsubmit="handleFormSubmit('confirmBtn',this);return false">
                <input id="confirmBtn" type="submit" class="btn" name="button" value="<pwm:Display key="Button_Confirm"/>"/>
                <input type="hidden" name="processAction" value="confirm"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
             </form>
            <form style="display: inline" action="<pwm:url url='UpdateProfile'/>" method="post" name="confirm" enctype="application/x-www-form-urlencoded"
                  onsubmit="handleFormSubmit('gobackBtn',this);return false">
                <input id="gobackBtn" type="submit" class="btn" name="button" value="<pwm:Display key="Button_GoBack"/>"/>
                <input type="hidden" name="processAction" value="unConfirm"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

