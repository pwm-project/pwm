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

<%@ page import="password.pwm.util.LocalDBLogger" %>
<%@ page import="password.pwm.util.*" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final LocalDBLogger localDBLogger = ContextManager.getPwmApplication(session).getLocalDBLogger(); %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div style="width: 100%; text-align:center;">
<a href="<%=request.getContextPath()%><pwm:url url='/public/CommandServlet'/>?processAction=viewLog">refresh</a>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
<a href="#" onclick="self.close()">close</a>
</div>
<%
    final PwmLogLevel logLevel = PwmLogLevel.TRACE;
    final LocalDBLogger.EventType logType = LocalDBLogger.EventType.Both;
    final int eventCount = 1000;
    final long maxTime = 10000;
    final LocalDBLogger.SearchResults searchResults = localDBLogger.readStoredEvents(PwmSession.getPwmSession(session), logLevel, eventCount, "", "", maxTime, logType);
%>
<pre><% for (final PwmLogEvent event : searchResults.getEvents()) { %><%= event.toLogString(true) %><%="\n"%><% } %></pre>
</body>
</html>
