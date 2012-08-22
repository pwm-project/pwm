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
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Iterator" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mmm:ss"); %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="PWM Event Log"/>
    </jsp:include>
    <%@ include file="admin-nav.jsp" %>
    <% if ("true".equalsIgnoreCase(request.getParameter("doReport"))) { %>
    <div id="centerbody" style="width:98%">
        <br class="clear"/>
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
                <td style="white-space: nowrap;">
                    <%= userInformation.getPasswordExpirationTime() == null ? "n/a" : dateFormat.format(userInformation.getPasswordExpirationTime()) %>
                </td>
                <td style="white-space: nowrap;">
                    <%= userInformation.getPasswordChangeTime() == null ? "n/a" : dateFormat.format(userInformation.getPasswordChangeTime()) %>
                </td>
                <td style="white-space: nowrap;">
                    <%= userInformation.getResponseSetTime() == null ? "n/a" : dateFormat.format(userInformation.getResponseSetTime()) %>
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
        <div id="buttonbar" style="align: center">
            <form action="userreport.jsp" method="GET">
                <button type="submit" class="btn">Continue</button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <% } else { %>
    <div id="centerbody">
        <br/><br/>
        <p>This report may take a long time to generate depending on the number of users in the search.</p>
        <p>If the user count is large, PWM may need large sizes of Java memory (heap size) to run the report.  It is also possible to run
            this report from the <i>PwmCommand</i> command line utility.
        </p>
        <div id="buttonbar" style="align: center">
            <form action="userreport.jsp" onclick="showWaitDialog()" method="GET">
                <input type="hidden" name="doReport" value="true"/>
                <button type="submit" class="btn">Run Report</button>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form action="<%=request.getContextPath()%><pwm:url url="/private/CommandServlet"/>" method="GET">
                <button type="submit" class="btn">Download Report as CSV</button>
                <input type="hidden" name="processAction" value="outputUserReportCsv"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <% } %>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
