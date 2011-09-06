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
<body onload="pwmPageLoadHandler();document.forms.updateProfile.elements[0].focus();" class="tundra">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UpdateProfile"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_UpdateProfile"/></p>

        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:url url='UpdateProfile'/>" method="post" name="updateProfile" enctype="application/x-www-form-urlencoded"
              onsubmit="handleFormSubmit('submitBtn',this);return false" onreset="handleFormClear();return false">

            <% request.setAttribute("form",PwmSetting.UPDATE_PROFILE_FORM); %>
            <jsp:include page="fragment/form.jsp"/>

            <div id="buttonbar">
                <input id="submitBtn" type="submit" class="btn" name="button" value="    <pwm:Display key="Button_Update"/>    "/>
                <% if (password.pwm.PwmSession.getPwmSession(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" class="btn" name="reset" value="    <pwm:Display key="Button_Reset"/>    "/>
                <% } %>
                <input type="hidden" name="processAction" value="updateProfile"/>
                <% if (password.pwm.PwmSession.getPwmSession(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                        onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                    &nbsp;&nbsp;&nbsp;<pwm:Display key="Button_Cancel"/>&nbsp;&nbsp;&nbsp;
                </button>
                <script type="text/javascript">getObject('button_cancel').style.visibility = 'visible';</script>
                <% } %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <br class="clear"/>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

