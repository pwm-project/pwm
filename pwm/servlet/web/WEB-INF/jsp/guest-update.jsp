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
<%@ page import="java.util.Date" %>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
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
        <form action="<pwm:url url='GuestRegistration'/>" method="post" name="updateGuest" enctype="application/x-www-form-urlencoded"
              onsubmit="handleFormSubmit('submitBtn',this);return false">
            <%@ include file="fragment/message.jsp" %>
            <br/>
            <% request.setAttribute("form",PwmSetting.GUEST_UPDATE_FORM); %>
            <jsp:include page="fragment/form.jsp"/>
            <%
                long maxValidDays = ContextManager.getPwmApplication(session).getConfig().readSettingAsLong(PwmSetting.GUEST_MAX_VALID_DAYS);
                if (maxValidDays > 0) {
                    long futureMS = maxValidDays * 24 * 60 * 60 * 1000;
                    Date maxValidDate = new Date(new Date().getTime() + (futureMS));
                    String maxValidDateString = new SimpleDateFormat("yyyy-MM-dd").format(maxValidDate);
                    String selectedDate = PwmSession.getPwmSession(session).getSessionStateBean().getLastParameterValues().get("__expirationDate__");
                    if (selectedDate == null || selectedDate.length() <= 0) {
                        Date currentDate = PwmSession.getPwmSession(session).getGuestRegistrationBean().getUpdateUserExpirationDate();
                        if (currentDate == null) {
                            selectedDate = maxValidDateString;
                        } else {
                            selectedDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
                        }
                    }
            %>
            <p>
                <label for="__expirationDate__"><pwm:Display key="Display_ExpirationDate" value1="<%=String.valueOf(maxValidDays)%>"/> </label>
                <input name="__expirationDate__" id="__expirationDate__"
                       type="date" required="true" value="<%=selectedDate%>"/>
            </p>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    require(["dijit/form/DateTextBox"],function(dojo){
                        new dijit.form.DateTextBox({
                            name: "__expirationDate__",
                            constraints: {
                                min: new Date(),
                                max: '<%=maxValidDateString%>'
                            },
                            value: '<%=selectedDate%>'
                        }, "__expirationDate__");
                    });
                });
            </script>
            <% } %>
            <div id="buttonbar">
                <input type="hidden" name="processAction" value="update"/>
                <input type="submit" name="Update" class="btn"
                       value="<pwm:Display key="Button_Update"/>"
                       id="submitBtn"/>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_RESET_BUTTON)) { %>
                <input type="reset" name="reset" class="btn"
                       value="<pwm:Display key="Button_Reset"/>"/>
                <% } %>
                <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(password.pwm.config.PwmSetting.DISPLAY_CANCEL_BUTTON)) { %>
                <button style="visibility:hidden;" name="button" class="btn" id="button_cancel" onclick="handleFormCancel();return false">
                    <pwm:Display key="Button_Cancel"/>
                </button>
                <% } %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        document.forms.updateGuest.elements[0].focus();
    });
</script>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
