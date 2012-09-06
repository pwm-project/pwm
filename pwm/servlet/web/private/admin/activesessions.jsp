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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.error.ErrorInformation" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.Set" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="PWM Active Sessions"/>
    </jsp:include>
    <div id="centerbody" style="width:98%">
        <%@ include file="admin-nav.jsp" %>
        <table>
            <tr>
                <td class="key" style="text-align: left">
                    Label
                </td>
                <td class="key" style="text-align: left">
                    Creation Time
                </td>
                <td class="key" style="text-align: left">
                    Idle
                </td>
                <td class="key" style="text-align: left">
                    Address
                </td>
                <td class="key" style="text-align: left">
                    Locale
                </td>
                <td class="key" style="text-align: left">
                    User DN
                </td>
                <td class="key" style="text-align: left">
                    Bad Auths
                </td>
                <td class="key" style="text-align: left">
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
                    <%= StringEscapeUtils.escapeHtml(loopSession.getSessionLabel()) %>
                </td>
                <td style="white-space: nowrap;">
                    <%= DateFormat.getDateTimeInstance().format(new Date(loopSession.getCreationTime())) %>
                </td>
                <td>
                    <%= TimeDuration.fromCurrent(loopSession.getLastAccessedTime()).asCompactString() %>
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
                    <%= lastError != null ? lastError.toUserStr(loopSession, ContextManager.getPwmApplication(session)) : "&nbsp;" %>
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
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
