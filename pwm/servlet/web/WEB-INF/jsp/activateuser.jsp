<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.config.FormConfiguration" %>
<%@ page import="java.util.List" %>
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
<body onload="pwmPageLoadHandler();document.forms.activateUser.elements[0].focus();" class="tundra">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ActivateUser"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_ActivateUser"/></p>

        <form action="<pwm:url url='ActivateUser'/>" method="post" name="activateUser"
              enctype="application/x-www-form-urlencoded" onsubmit="handleFormSubmit('submitBtn',this);return false"
              onreset="handleFormClear();return false" onkeypress="checkForCapsLock(event)">
            <%@ include file="fragment/message.jsp" %>
            <br/>
            <% //check to see if any locations are configured.
                if (!ContextManager.getPwmApplication(session).getConfig().getLoginContexts().isEmpty()) {
            %>
            <h2><label for="context"><pwm:Display key="Field_Location"/></label></h2>
            <select name="context" id="context">
                <pwm:DisplayLocationOptions name="context"/>
            </select>
            <% } %>
            <form action="<pwm:url url='NewUser'/>" method="post" name="newUser" enctype="application/x-www-form-urlencoded"
                  id="newUserForm"
                  onsubmit="handleFormSubmit('submitBtn',this);return false" onreset="handleFormClear();return false"
                  onkeyup="validateNewUserForm();" onkeypress="checkForCapsLock(event);"
                  >
                <% request.setAttribute("form",PwmSetting.ACTIVATE_USER_FORM); %>
                <jsp:include page="fragment/form.jsp"/>
                <div id="buttonbar">
                    <input type="submit" name="button" class="btn"
                           value="     <pwm:Display key="Button_Activate"/>     "
                           id="submitBtn"/>
                    <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                    <input type="reset" name="reset" class="btn"
                           value="     <pwm:Display key="Button_Reset"/>     "/>
                    <% } %>
                    <input type="hidden"
                           name="processAction"
                           value="activate"/>
                    <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                    <button style="visibility:hidden;" name="button" class="btn" id="button_cancel"
                            onclick="window.location='<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>?processAction=continue';return false">
                        &nbsp;&nbsp;&nbsp;<pwm:Display key="Button_Cancel"/>&nbsp;&nbsp;&nbsp;
                    </button>
                    <script type="text/javascript">getObject('button_cancel').style.visibility = 'visible';</script>
                    <% } %>
                    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                </div>
            </form>
        </form>
    </div>
    <br class="clear"/>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
