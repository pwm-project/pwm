<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2015 The PWM Project
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
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_GuestUpdate"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="fragment/guest-nav.jsp"%>
        <p><pwm:display key="Display_GuestUpdate"/></p>
        <form action="<pwm:url url='GuestRegistration'/>" method="post" name="updateGuest" enctype="application/x-www-form-urlencoded" class="pwm-form">
            <%@ include file="fragment/message.jsp" %>
            <jsp:include page="fragment/form.jsp"/>
            <%
                final PwmRequest guestPwmRequest = PwmRequest.forRequest(request, response);
                final long maxValidDays = guestPwmRequest.getConfig().readSettingAsLong(PwmSetting.GUEST_MAX_VALID_DAYS);
                final GuestRegistrationBean guestRegistrationBean = guestPwmRequest.getPwmSession().getGuestRegistrationBean();
                if (maxValidDays > 0) {
                    long futureMS = maxValidDays * 24 * 60 * 60 * 1000;
                    Date maxValidDate = new Date(new Date().getTime() + (futureMS));
                    String maxValidDateString = new SimpleDateFormat("yyyy-MM-dd").format(maxValidDate);
                    String selectedDate = guestRegistrationBean.getFormValues().get("__expirationDate__");
                    if (selectedDate == null || selectedDate.length() <= 0) {
                        Date currentDate = JspUtility.getPwmSession(pageContext).getGuestRegistrationBean().getUpdateUserExpirationDate();
                        if (currentDate == null) {
                            selectedDate = maxValidDateString;
                        } else {
                            selectedDate = new SimpleDateFormat("yyyy-MM-dd").format(currentDate);
                        }
                    }
            %>
            <p>
                <label>
                    <pwm:display key="Display_ExpirationDate" value1="<%=String.valueOf(maxValidDays)%>"/>
                    <input name="<%=GuestRegistrationServlet.HTTP_PARAM_EXPIRATION_DATE%>" id="<%=GuestRegistrationServlet.HTTP_PARAM_EXPIRATION_DATE%>" type="hidden" required="true" value="<%=selectedDate%>"/>
                    <input name="expiredate-stub" id="expiredate-stub" type="date" required="true" value="<%=selectedDate%>"/>
                </label>
            </p>
            <pwm:script>
                <script type="text/javascript">
                    PWM_GLOBAL['startupFunctions'].push(function(){
                        require(["dijit/form/DateTextBox"],function(DateTextBox){
                            new DateTextBox({
                                constraints: {
                                    min: new Date(),
                                    max: '<%=maxValidDateString%>'
                                },
                                value: '<%=selectedDate%>',
                                onChange: function(){
                                    PWM_MAIN.getObject('<%=GuestRegistrationServlet.HTTP_PARAM_EXPIRATION_DATE%>').value = this.value;
                                }
                            }, "expiredate-stub");
                        });
                    });
                </script>
            </pwm:script>
            <% } %>
            <div class="buttonbar">
                <input type="hidden" name="processAction" value="update"/>
                <input type="submit" name="Update" class="btn" value="<pwm:display key="Button_Update"/>" id="submitBtn"/>
                <%@ include file="/WEB-INF/jsp/fragment/cancel-button.jsp" %>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        document.forms.updateGuest.elements[0].focus();
    });
</script>
</pwm:script>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
