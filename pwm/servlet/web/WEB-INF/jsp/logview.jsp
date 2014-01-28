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

<%@ page import="password.pwm.util.LocalDBLogger" %>
<%@ page import="password.pwm.util.PwmLogEvent" %>
<%@ page import="password.pwm.util.PwmLogLevel" %>
<%@ page import="java.util.Date" %>
<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final LocalDBLogger localDBLogger = ContextManager.getPwmApplication(session).getLocalDBLogger(); %>
<% final String selectedLevel = password.pwm.Validator.readStringFromRequest(request, "level", 255, "");%>
<body class="nihilo">
<% if ("".equals(selectedLevel)) { %>
<div style="text-align: center;"><pwm:Display key="Display_PleaseWait"/></div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.showWaitDialog(null,null,function(){
            PWM_CONFIG.openLogViewer('INFO');
        });
    });
</script>
<% } else { %>
<div style="width: 100%; text-align:center; background-color: #eeeeee" id="headerDiv">
    <%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date())%>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <a style="cursor: pointer" onclick="PWM_MAIN.showWaitDialog(null,null,function(){PWM_CONFIG.openLogViewer('<%=selectedLevel%>')});">refresh</a>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <a style="cursor: pointer" onclick="self.close()">close</a>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <select id="level" name="level" style="width: auto;" onchange="var level=this.options[this.selectedIndex].value;PWM_MAIN.showWaitDialog(null,null,function(){PWM_CONFIG.openLogViewer(level)});">
        <option value="FATAL" <%= "FATAL".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>FATAL
        </option>
        <option value="ERROR" <%= "ERROR".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>ERROR
        </option>
        <option value="WARN" <%= "WARN".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>WARN
        </option>
        <option value="INFO" <%= "INFO".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>INFO
        </option>
        <option value="DEBUG" <%= "DEBUG".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>DEBUG
        </option>
        <option value="TRACE" <%= "TRACE".equals(selectedLevel) ? "selected=\"selected\"" : "" %>>TRACE
        </option>
    </select>
</div>
<%
    PwmLogLevel logLevel; try { logLevel=PwmLogLevel.valueOf(selectedLevel); } catch (Exception e) { logLevel=PwmLogLevel.INFO; }
    final LocalDBLogger.EventType logType = LocalDBLogger.EventType.Both;
    final int eventCount = 1000;
    final long maxTime = 10000;
    final LocalDBLogger.SearchResults searchResults = localDBLogger.readStoredEvents(PwmSession.getPwmSession(session), logLevel, eventCount, "", "", maxTime, logType);
%>
<pre><% for (final PwmLogEvent event : searchResults.getEvents()) { %><%= event.toLogString(true) %><%="\n"%><% } %></pre>
<% } %>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_HIDE_FOOTER_TEXT,"true"); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
<script type="text/javascript" defer="defer" src="<%=request.getContextPath()%><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_GLOBAL['idle_suspendTimeout'] = true;
    });
</script>
</body>
</html>
