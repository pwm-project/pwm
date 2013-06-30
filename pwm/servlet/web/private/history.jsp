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

<%@ page import="password.pwm.event.AuditRecord" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<%
    List<AuditRecord> auditRecords = Collections.emptyList();
    try {
        auditRecords = pwmApplicationHeader.getAuditManager().readUserAuditRecords(pwmSessionHeader);
    } catch (Exception e) {
    }
    final Locale userLocale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale();
%>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UserEventHistory"/>
    </jsp:include>
    <div id="centerbody">
        <% final String timeZone = (java.text.DateFormat.getDateTimeInstance()).getTimeZone().getDisplayName(); %>
        <p><pwm:Display key="Display_UserEventHistory" value1="<%= timeZone %>"/></p>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>

        <table style="border-collapse:collapse;  border: 2px solid #D4D4D4; width:100%">
            <% for (final AuditRecord record : auditRecords) { %>
            <tr>
                <td class="key" style="width: 200px">
                    <%= (DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, userLocale)).format(record.getTimestamp()) %>
                </td>
                <td>
                    <%= record.getEventCode().getLocalizedString(ContextManager.getPwmApplication(session).getConfig(),userLocale) %>
                </td>
            </tr>
            <% } %>
        </table>
        <br class="clear"/>

        <div id="buttonbar">
            <form action="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input type="hidden"
                       name="processAction"
                       value="continue"/>
                <input type="submit" name="button" class="btn"
                       value="    <pwm:Display key="Button_Continue"/>    "
                       id="button_logout"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
