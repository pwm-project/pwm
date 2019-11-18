<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2019 The PWM Project
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


<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.PwmRequest" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.config.value.data.CustomLinkConfiguration" %>

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
