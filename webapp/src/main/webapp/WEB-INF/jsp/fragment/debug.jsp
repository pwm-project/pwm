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
