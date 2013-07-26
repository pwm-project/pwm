<%@ page import="password.pwm.error.ErrorInformation" %>
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

<%--
  ~ This file is imported by most JSPs, it shows the error/success message bar.
  --%>
<%@ taglib uri="pwm" prefix="pwm" %>
<div id="message_wrapper">
<% if (PwmSession.getPwmSession(session).getSessionStateBean().getSessionError() != null) { %>
    <% final ErrorInformation errorInformation = PwmSession.getPwmSession(session).getSessionStateBean().getSessionError(); %>
    <span id="message" class="message message-error"><pwm:ErrorMessage/></span>
    <span id="errorCode" style="display: none"><%=errorInformation.getError().getErrorCode()%></span>
    <span id="errorName" style="display: none"><%=errorInformation.getError().toString()%></span>
<% PwmSession.getPwmSession(session).getSessionStateBean().setSessionError(null); %>
<% } else if (PwmSession.getPwmSession(session).getSessionStateBean().getSessionSuccess() != null) { %>
    <span id="message" class="message message-success"><pwm:SuccessMessage/></span>
<% PwmSession.getPwmSession(session).getSessionStateBean().setSessionSuccess(null,null); %>
<% } else { %>
    <span style="display:none" id="message" class="message">&nbsp;</span>
<% } %>
    <div id="capslockwarning" style="display:none;"><pwm:Display key="Display_CapsLockIsOn"/></div>
</div>
