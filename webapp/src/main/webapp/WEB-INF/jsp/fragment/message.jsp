<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.error.ErrorInformation" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<%--
  ~ This file is imported by most JSPs, it shows the error/success message bar.
  --%>
<%@ taglib uri="pwm" prefix="pwm" %>
<div id="message_wrapper">
<% final ErrorInformation requestError = (ErrorInformation)JspUtility.getAttribute(pageContext, PwmRequestAttribute.PwmErrorInfo); %>
<% if (requestError != null) { %>
    <span id="message" class="message message-error"><pwm:ErrorMessage/></span>
    <span id="errorCode"><%=requestError.getError().getErrorCode()%></span>
    <span id="errorName"><%=requestError.getError().toString()%></span>
<% } else { %>
    <span id="message" class="message nodisplay">&nbsp;</span>
<% } %>
    <div id="capslockwarning" class="display-none"><pwm:display key="Display_CapsLockIsOn"/></div>
</div>
