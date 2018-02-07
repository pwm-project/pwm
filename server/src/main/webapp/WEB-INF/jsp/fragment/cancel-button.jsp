<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>

<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<pwm:if test="<%=PwmIfTest.showCancel%>">
<pwm:if test="<%=PwmIfTest.forcedPageView%>" negate="true">
<button type="submit" name="button-cancel" class="btn" id="button-cancel" form="form-hidden-cancel">
    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
    <pwm:display key="Button_Cancel"/>
</button>
<pwm:script> <%-- ie doesn't support 'from' attribute on buttons, so handle with this script --%>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function() {
            PWM_MAIN.addEventHandler('button-cancel', 'click', function(e){
                console.log('intercepted cancel button');
                PWM_MAIN.cancelEvent(e);
                var cancelForm = PWM_MAIN.getObject('form-hidden-cancel');
                PWM_MAIN.handleFormSubmit(cancelForm);
            });
        });
    </script>
</pwm:script>
</pwm:if>
</pwm:if>
