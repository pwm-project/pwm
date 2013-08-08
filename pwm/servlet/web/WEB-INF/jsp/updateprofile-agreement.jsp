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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body onload="pwmPageLoadHandler()" class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UpdateProfile"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="fragment/message.jsp" %>
        <% final String agreementText = ContextManager.getPwmApplication(session).getConfig().readSettingAsLocalizedString(PwmSetting.UPDATE_PROFILE_AGREEMENT_MESSAGE, PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
        <% final String expandedText = MacroMachine.expandMacros(agreementText, pwmApplicationHeader, pwmSessionHeader.getUserInfoBean(), pwmSessionHeader.getSessionManager().getUserDataReader()); %>
        <div class="agreementText"><%= expandedText %></div>
        <div id="buttonbar">
            <form action="<pwm:url url='UpdateProfile'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <%-- remove the next line to remove the "I Agree" checkbox --%>
                <input type="checkbox" id="agreeCheckBox" onclick="updateContinueButton()" data-dojo-type="dijit.form.CheckBox"
                       onchange="updateContinueButton()"/>&nbsp;&nbsp;<label for="agreeCheckBox"><pwm:Display
                    key="Button_Agree"/></label>
                <input type="hidden"
                       name="processAction"
                       value="agree"/>
                <input type="submit" name="button" class="btn"
                       value="<pwm:Display key="Button_Continue"/>"
                       id="button_continue"/>
                <input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form action="<%=request.getContextPath()%>/public/<pwm:url url='Logout'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input type="submit" name="button" class="btn"
                       value="<pwm:Display key="Button_Logout"/>"
                       id="button_logout"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    function updateContinueButton() {
        var checkBox = getObject("agreeCheckBox");
        var continueButton = getObject("button_continue");
        if (checkBox != null && continueButton != null) {
            if (checkBox.checked) {
                continueButton.removeAttribute('disabled');
            } else {
                continueButton.disabled = "disabled";
            }
        }
    }


    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/form/CheckBox"],function(dojoParser){
            dojoParser.parse();
            updateContinueButton();
        });
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
