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

<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.bean.DisplayElement" %>

<%@ taglib uri="pwm" prefix="pwm" %>
<% final DisplayElement displayElement = (DisplayElement)request.getAttribute("displayElement"); %>
<tr>
    <td class="key">
        <%= displayElement.getLabel() %>
    </td>
    <% if (displayElement.getType() == DisplayElement.Type.timestamp) { %>
    <td class="timestamp" id="<%=StringUtil.escapeHtml(displayElement.getKey())%>">
        <%= StringUtil.escapeHtml(displayElement.getValue()) %>
    </td>
    <% } else { %>
    <td  id="<%=StringUtil.escapeHtml(displayElement.getKey())%>">
        <%= StringUtil.escapeHtml(displayElement.getValue()) %>
    </td>
    <% } %>
</tr>
