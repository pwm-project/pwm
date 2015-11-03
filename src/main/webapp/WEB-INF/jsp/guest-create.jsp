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

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final String maxValidDate = (String)JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.GuestMaximumExpirationDate); %>
<% final String selectedDate = (String)JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.GuestCurrentExpirationDate); %>
<% final String maxValidDays = (String)JspUtility.getAttribute(pageContext, PwmConstants.REQUEST_ATTR.GuestMaximumValidDays); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_GuestRegistration"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/guest-nav.jsp" %>
        <p><pwm:display key="Display_GuestRegistration"/></p>

        <form action="<pwm:current-url/>" method="post" name="newGuest" enctype="application/x-www-form-urlencoded" class="pwm-form">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <jsp:include page="fragment/form.jsp"/>
            <p>
                <label>
                    <pwm:display key="Display_ExpirationDate" value1="<%=String.valueOf(maxValidDays)%>"/>
                    <input name="<%=GuestRegistrationServlet.HTTP_PARAM_EXPIRATION_DATE%>" id="<%=GuestRegistrationServlet.HTTP_PARAM_EXPIRATION_DATE%>" type="hidden" required="true" value="<%=selectedDate%>"/>
                    <input name="expiredate-stub" id="expiredate-stub" type="date" required="true" value="<%=selectedDate%>"/>
                </label>
            </p>
            <pwm:script>
            </pwm:script>

            <div class="buttonbar">
                <input type="hidden" name="processAction" value="create"/>
                <button type="submit" name="Create" class="btn" id="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-user-plus"></span></pwm:if>
                    <pwm:display key="Button_Create"/>
                </button>
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
            PWM_GUEST.initDatePicker('<%=maxValidDate%>','<%=selectedDate%>');
        });
    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/guest.js"/>
<%@ include file="/WEB-INF/jsp/fragment/cancel-form.jsp" %>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
