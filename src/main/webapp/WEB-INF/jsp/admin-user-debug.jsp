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

<%@ page import="password.pwm.Permission" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<% final PwmRequest debug_pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<% final PwmSession debug_pwmSession = debug_pwmRequest.getPwmSession(); %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Debug"/>
    </jsp:include>
    <div id="centerbody">
        <table>
            <tr>
                <td class="key">UserDN</td>
                <td><pwm:macro value="@LDAP:dn@"/></td>
            </tr>
            <tr>
                <td class="key">Ldap Profile</td>
                <td><%="".equals(debug_pwmSession.getUserInfoBean().getUserIdentity().getLdapProfileID()) ? "default" : debug_pwmSession.getUserInfoBean().getUserIdentity().getLdapProfileID()%></td>
            </tr>
            <tr>
                <td class="key">AuthType</td>
                <td><%=debug_pwmSession.getLoginInfoBean().getAuthenticationType()%></td>
            </tr>
            <tr>
                <td class="key">Session Creation Time</td>
                <td><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(debug_pwmSession.getSessionStateBean().getSessionCreationTime())%></td>
            </tr>
            <tr>
                <td class="key">Session ForwardURL</td>
                <td><%=debug_pwmSession.getSessionStateBean().getForwardURL()%></td>
            </tr>
            <tr>
                <td class="key">Session LogoutURL</td>
                <td><%=debug_pwmSession.getSessionStateBean().getLogoutURL()%></td>
            </tr>
        </table>
        <table>
            <% for (final Permission permission : Permission.values()) { %>
            <tr>
                <td class="key"><%=permission.toString()%></td>
                <td><%=debug_pwmSession.getSessionManager().checkPermission(debug_pwmRequest.getPwmApplication(), permission)%></td>
            </tr>
            <% } %>
        </table>
        <div class="buttonbar">
            <form action="<pwm:url url='<%=PwmServletDefinition.Command.servletUrl()%>' addContext="true"/>" method="post" enctype="application/x-www-form-urlencoded">
                <input tabindex="2" type="submit" name="continue_btn" class="btn"
                       value="    <pwm:display key="Button_Continue"/>    "/>
                <input type="hidden"
                       name="processAction"
                       value="continue"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
