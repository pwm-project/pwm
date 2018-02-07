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
<%@ page import="password.pwm.PwmConstants" %>

<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<h2><label for="<%=PwmConstants.PARAM_TOKEN%>"><pwm:display key="Field_Code"/></label></h2>
<pwm:if test="<%=PwmIfTest.maskTokenInput%>">
    <input type="password" class="inputfield passwordfield" id="<%=PwmConstants.PARAM_TOKEN%>" name="<%=PwmConstants.PARAM_TOKEN%>" required="required" <pwm:autofocus/> ></textarea>
</pwm:if>
<pwm:if test="<%=PwmIfTest.maskTokenInput%>" negate="true">
    <textarea id="<%=PwmConstants.PARAM_TOKEN%>" name="<%=PwmConstants.PARAM_TOKEN%>" class="tokenInput" required="required" <pwm:autofocus/> ></textarea>
</pwm:if>
