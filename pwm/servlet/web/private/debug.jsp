<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="java.text.DateFormat" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final UserInfoBean uiBean = PwmSession.getPwmSession(session).getUserInfoBean(); %>
<% final SessionStateBean ssBean = PwmSession.getPwmSession(session).getSessionStateBean(); %>
<% final DateFormat dateFormatter = java.text.DateFormat.getDateInstance(DateFormat.FULL, ssBean.getLocale()); %>
<% final DateFormat timeFormatter = java.text.DateFormat.getTimeInstance(DateFormat.FULL, ssBean.getLocale()); %>
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
                <td><%=pwmSessionHeader.getUserInfoBean().getUserIdentity().getUserDN()%></td>
            </tr>
            <tr>
                <td class="key">Ldap Profile</td>
                <td><%="".equals(pwmSessionHeader.getUserInfoBean().getUserIdentity().getLdapProfileID()) ? "default" : pwmSessionHeader.getUserInfoBean().getUserIdentity().getLdapProfileID()%></td>
            </tr>
            <tr>
                <td class="key">AuthType</td>
                <td><%=pwmSessionHeader.getUserInfoBean().getAuthenticationType()%></td>
            </tr>
            <tr>
                <td class="key">Session Creation Time</td>
                <td><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(pwmSessionHeader.getSessionStateBean().getSessionCreationTime())%></td>
            </tr>
            <tr>
                <td class="key">Session ForwardURL</td>
                <td><%=pwmSessionHeader.getSessionStateBean().getForwardURL()%></td>
            </tr>
            <tr>
                <td class="key">Session LogoutURL</td>
                <td><%=pwmSessionHeader.getSessionStateBean().getLogoutURL()%></td>
            </tr>
        </table>
        <table>
            <% for (final Permission permission : Permission.values()) { %>
            <tr>
                <td class="key"><%=permission.toString()%></td>
                <td><%=Permission.checkPermission(permission,pwmSessionHeader,pwmApplicationHeader)%></td>
            </tr>
            <% } %>
        </table>
        <div id="buttonbar">
            <form action="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input tabindex="2" type="submit" name="continue_btn" class="btn"
                       value="    <pwm:Display key="Button_Continue"/>    "/>
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
