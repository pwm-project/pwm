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
<script type="text/javascript" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/newuser.js'/>"></script>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_NewUser"/>
    </jsp:include>
    <div id="centerbody">
        <p><pwm:Display key="Display_NewUser"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <form action="<pwm:url url='NewUser'/>" method="post" name="newUser" enctype="application/x-www-form-urlencoded"
              id="newUserForm" onkeyup="validateNewUserForm()"
              onsubmit="handleFormSubmit('submitBtn',this);return false">
            <% request.setAttribute("form",PwmSetting.NEWUSER_FORM); %>
            <% request.setAttribute("form_showPasswordFields","true"); %>
            <jsp:include page="fragment/form.jsp"/>
            <div id="buttonbar">
                <input type="hidden" name="processAction" value="create"/>
                <input type="submit" name="Create" class="btn"
                       value="<pwm:Display key="Button_Create"/>"
                       id="submitBtn"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" name="reset" class="btn"
                       value="<pwm:Display key="Button_Reset"/>"/>
                <% } %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<script>
    PWM_GLOBAL['startupFunctions'].push(function(){
        document.forms.newUser.elements[0].focus();
        ShowHidePasswordHandler.initAllForms();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
