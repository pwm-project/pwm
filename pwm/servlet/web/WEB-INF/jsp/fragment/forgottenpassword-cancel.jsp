<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.servlet.forgottenpw.ForgottenPasswordServlet" %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
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

<pwm:if test="showCancel">
    <button type="button" name="button" class="btn" id="button-sendReset">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-times"></span></pwm:if>
        <pwm:display key="Button_Cancel"/>
    </button>
    <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                PWM_MAIN.addEventHandler('button-sendReset', 'click',function() {
                    PWM_MAIN.submitPostAction('<%=PwmServletDefinition.ForgottenPassword.servletUrlName()%>', '<%=ForgottenPasswordServlet.ForgottenPasswordAction.reset%>');
                });
            });
        </script>
    </pwm:script>
</pwm:if>
