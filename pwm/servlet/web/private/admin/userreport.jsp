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

<%@ page import="password.pwm.util.UserReport" %>
<%@ page import="java.util.Iterator" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="PWM Event Log"/>
    </jsp:include>
    <div id="centerbody" style="width:98%">
        <%@ include file="admin-nav.jsp" %>
        <br class="clear"/>
        <% if ("true".equalsIgnoreCase(request.getParameter("doReport"))) { %>
        <% final UserReport userReport = new UserReport(ContextManager.getPwmApplication(session)); %>
        <br class="clear"/>
        <table id="form">
            <tr>
                <td style="text-align: center;">
                    User DN
                </td>
                <td style="text-align: center">
                    User GUID
                </td>
                <td style="text-align: center">
                    Password Expiration Time
                </td>
                <td style="text-align: center">
                    Password Change Time
                </td>
                <td style="text-align: center">
                    Response Save Time
                </td>
                <td style="text-align: center">
                    Has Valid Responses
                </td>
                <td style="text-align: center">
                    Password Expired
                </td>
                <td style="text-align: center">
                    Password Pre-Expired
                </td>
                <td style="text-align: center">
                    Password Violates Policy
                </td>
                <td style="text-align: center">
                    Password In Warn Period
                </td>
            </tr>
            <%
                for (final Iterator<UserReport.UserInformation> resultIterator = userReport.resultIterator(); resultIterator.hasNext(); ) {
                    final UserReport.UserInformation userInformation = resultIterator.next();
            %>
            <tr>
                <td class="key" style="font-family: Courier, sans-serif" width="5">
                    <%= userInformation.getUserDN() %>
                </td>
                <td>
                    <%= userInformation.getGuid() %>
                </td>
                <td>
                    <%= userInformation.getPasswordExpirationTime() == null ? "n/a" : userInformation.getPasswordExpirationTime() %>
                </td>
                <td>
                    <%= userInformation.getPasswordChangeTime() == null ? "n/a" : userInformation.getPasswordChangeTime() %>
                </td>
                <td>
                    <%= userInformation.getResponseSetTime() == null ? "n/a" : userInformation.getResponseSetTime() %>
                </td>
                <td>
                    <%= userInformation.isHasValidResponses() ? "yes" : "no" %>
                </td>
                <td>
                    <%= userInformation.getPasswordStatus().isExpired() ? "yes" : "no" %>
                </td>
                <td>
                    <%= userInformation.getPasswordStatus().isPreExpired() ? "yes" : "no" %>
                </td>
                <td>
                    <%= userInformation.getPasswordStatus().isViolatesPolicy() ? "yes" : "no" %>
                </td>
                <td>
                    <%= userInformation.getPasswordStatus().isWarnPeriod() ? "yes" : "no" %>
                </td>
            </tr>
            <% } %>
        </table>
        <% } else { %>
        <div>
                <p>This report may take a long time to generate depending on the number of users in the search.  Also be sure PWM has been given adequate
                    Java memory (heap) size to run the report.
                </p>
                <div id="buttonbar" style="align: center">
                    <form action="userreport.jsp?doReport=true" method="POST">
                        <button type="submit">Run Report</button>
                    </form>
                </div>
        </div>
        <% } %>
    </div>
    <%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
