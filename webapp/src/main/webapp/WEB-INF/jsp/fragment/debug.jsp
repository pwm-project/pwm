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


<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<table>
    <tr>
        <td>Forward URL</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getForwardUrl())%></td>
    </tr>
    <tr>
        <td>Logout URL</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getLogoutURL())%></td>
    </tr>
    <tr>
        <td>Locale</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getLocale().toString())%></td>
    </tr>
    <tr>
        <td>Theme</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getPwmSession().getSessionStateBean().getTheme())%></td>
    </tr>
    <tr>
        <td>Instance ID</td><td><%=StringUtil.escapeHtml(JspUtility.getPwmRequest(pageContext).getPwmApplication().getInstanceID())%></td>
    </tr>
</table>
