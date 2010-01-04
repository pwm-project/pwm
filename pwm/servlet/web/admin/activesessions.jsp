<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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

<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.error.ErrorInformation" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Set" %>
<%@ page import="password.pwm.PwmSession" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Active Sessions"/></jsp:include>
    <div id="centerbody" style="width:98%">
        <p style="text-align:center;">
            <a href="status.jsp">Status</a> | <a href="eventlog.jsp">Event Log</a> | <a href="intruderstatus.jsp">Intruder Status</a> | <a href="activesessions.jsp">Active Sessions</a> | <a href="config.jsp">Configuration</a> | <a href="threads.jsp">Threads</a> | <a href="UserInformation">User Information</a>
        </p>
        <table>
            <tr>
                <td class="title">
                    Label
                </td>
                <td class="title">
                    Creation Time
                </td>
                <td class="title">
                    Idle
                </td>
                <td class="title">
                    Address
                </td>
                <td class="title">
                    Locale
                </td>
                <td class="title">
                    User DN
                </td>
                <td class="title">
                    Bad Auths
                </td>
                <td class="title">
                    Last Error
                </td>
            </tr>

            <%
                final ContextManager theManager = ContextManager.getContextManager(request.getSession().getServletContext());
                final Set<PwmSession> activeSessions = new LinkedHashSet<PwmSession>(theManager.getPwmSessions());
                for (final PwmSession loopSession : activeSessions) {
                    try {
                        final SessionStateBean loopSsBean = loopSession.getSessionStateBean();
                        final password.pwm.bean.UserInfoBean loopUiBean = loopSession.getUserInfoBean();
            %>

            <tr>
                <td>
                    <%= loopSession.getSessionLabel() %>
                </td>
                <td style="white-space: nowrap;">
                    <%= DateFormat.getDateTimeInstance().format(new Date(loopSession.getCreationTime())) %>
                </td>
                <td>
                    <%= TimeDuration.asCompactString(loopSsBean.getIdleTime()) %>
                </td>
                <td>
                    <%=loopSsBean.getSrcAddress() %>
                </td>
                <td>
                    <%= loopSsBean.getLocale() %>
                </td>
                <td>
                    <%= loopSsBean.isAuthenticated() ? loopUiBean.getUserDN() : "&nbsp;" %>
                </td>
                <td>

                    <%= loopSsBean.getIncorrectLogins() %>
                </td>
                <td>
                    <% final ErrorInformation lastError = loopSsBean.getSessionError(); %>
                    <%= lastError != null ? lastError.toUserStr(loopSession) : "&nbsp;" %>
                </td>
            </tr>
            <%
                    } catch (IllegalStateException e) {
                        //don't care, session is invalidated
                    }
                }
            %>
        </table>
    </div>
</div>
<%@ include file="../jsp/footer.jsp" %>
</body>
</html>
