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
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_GuestUpdate"/>
    </jsp:include>
    <div id="centerbody">
        <p id="registration-menu-bar" style="text-align:center;">
            <a class="menubutton" href="GuestRegistration?menuSelect=create&pwmFormID=<pwm:FormID/>"><pwm:Display key="Title_GuestRegistration"/></a>
            &nbsp;&nbsp;&nbsp;
            <a class="menubutton" href="GuestRegistration?menuSelect=search&pwmFormID=<pwm:FormID/>"><pwm:Display key="Title_GuestUpdate"/></a>
        </p>
        <br/>
        <p><pwm:Display key="Display_GuestUpdate"/></p>

        <form action="<pwm:url url='GuestRegistration'/>" method="post" enctype="application/x-www-form-urlencoded"
              name="searchForm"
              onsubmit="handleFormSubmit('submitBtn',this);return false" id="searchForm">
            <%@ include file="fragment/message.jsp" %>


            <% //check to see if any locations are configured.
                if (!ContextManager.getPwmApplication(session).getConfig().getLoginContexts().isEmpty()) {
            %>
            <h2><label for="context"><pwm:Display key="Field_Location"/></label></h2>
            <select name="context" id="context" class="inputfield">
                <pwm:DisplayLocationOptions name="context"/>
            </select>
            <% } %>

            <h2><label for="username"><pwm:Display key="Field_Username"/></label></h2>
            <input type="text" id="username" name="username" class="inputfield"
                   value="<pwm:ParamValue name='username'/>"/>

            <div id="buttonbar">
                <input type="hidden"
                       name="processAction"
                       value="search"/>
                <input type="submit" class="btn"
                       name="search"
                       value="<pwm:Display key="Button_Search"/>"
                       id="submitBtn"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>

                <input type="reset" class="btn"
                       name="reset" onclick="clearForm('searchForm');return false;"
                       value="<pwm:Display key="Button_Reset"/>"/>
                <% } %>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        getObject('username').focus();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

