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

<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.config.CustomLinkConfiguration" %>

<%@ taglib uri="pwm" prefix="pwm" %>

<% final PwmRequest formPwmRequest = PwmRequest.forRequest(request,response); %>
<% final Locale formLocale = formPwmRequest.getLocale(); %>
<% final List<CustomLinkConfiguration> links = (List<CustomLinkConfiguration>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormCustomLinks); %>
<% if (links != null  && !links.isEmpty()) { %>
<% for (final CustomLinkConfiguration item : links) { %>
<form method="get" action="<%=item.getCustomLinkUrl()%>" title="<%=item.getDescription(formLocale)%>" <%=item.isCustomLinkNewWindow()?"target=\"_blank\"":""%>>
    <input class="button" type="submit" value="<%=item.getLabel(formLocale)%>">
</form>
<% } %>
<br/><br/>
<% } %>
