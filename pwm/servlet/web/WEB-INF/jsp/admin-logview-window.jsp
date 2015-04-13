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

<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="password.pwm.util.logging.LocalDBLogger" %>
<%@ page import="password.pwm.util.logging.PwmLogEvent" %>
<%@ page import="password.pwm.util.logging.PwmLogLevel" %>
<%@ page import="java.util.Date" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_IDLE_TIMEOUT); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final PwmRequest pwmRequest = PwmRequest.forRequest(request,response); %>
<% final LocalDBLogger localDBLogger = pwmRequest.getPwmApplication().getLocalDBLogger(); %>
<% final String selectedLevel = pwmRequest.readParameterAsString("level", 255);%>
<% final PwmLogLevel configuredLevel = pwmRequest.getConfig().readSettingAsEnum(PwmSetting.EVENTS_LOCALDB_LOG_LEVEL,PwmLogLevel.class); %>
<body class="nihilo">
<% if ("".equals(selectedLevel)) { %>
<div style="text-align: center;"><pwm:display key="Display_PleaseWait"/></div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        PWM_MAIN.showWaitDialog({loadFunction:function() {
            PWM_CONFIG.openLogViewer('INFO');
        }});
        PWM_MAIN.TimestampHandler.toggleAllElements();
    });
</script>
</pwm:script>
<% } else { %>
<div style="width: 100%; text-align:center; background-color: #eeeeee" id="headerDiv">
    <span class="timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date())%></span>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <select name="level" style="width: auto;" id="select-level">
        <% for (final PwmLogLevel level : PwmLogLevel.values()) { %>
        <% boolean selected = level.toString().equals(selectedLevel); %>
        <% boolean disabled = level.compareTo(configuredLevel) < 0; %>
        <option value="<%=level%>" <%=selected ?" selected": ""%><%=disabled ? " disabled" : ""%>  ><%=level%></option>
        <% } %>
    </select>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <button class="btn" id="button-refresh">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-refresh"></span></pwm:if>
        <pwm:display key="Button_Refresh" bundle="Admin"/>
    </button>
</div>
<%
    PwmLogLevel logLevel; try { logLevel=PwmLogLevel.valueOf(selectedLevel); } catch (Exception e) { logLevel=PwmLogLevel.INFO; }
    final LocalDBLogger.EventType logType = LocalDBLogger.EventType.Both;
    final int eventCount = 1000;
    final long maxTime = 10000;
    final LocalDBLogger.SearchParameters searchParameters = new LocalDBLogger.SearchParameters(logLevel, eventCount, "", "", maxTime, logType);
    final LocalDBLogger.SearchResults searchResults = localDBLogger.readStoredEvents(searchParameters);
%>
<pre><% while (searchResults.hasNext()) { %>
    <% final PwmLogEvent logEvent = searchResults.next(); %>
    <span class="timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(logEvent.getDate())%></span>, <%=StringUtil.escapeHtml(logEvent.toLogString(false)) %><%="\n"%>
    <% } %></pre>
<% } %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        var refreshFunction = function(){
            var levelSelectElement = PWM_MAIN.getObject('select-level');
            var level=levelSelectElement.options[levelSelectElement.selectedIndex].value;
            PWM_MAIN.showWaitDialog({loadFunction:function(){PWM_CONFIG.openLogViewer(level)}});
        };
        PWM_MAIN.addEventHandler('button-refresh','click',function(){
            refreshFunction();
        });
        document.title = "Log Viewer";
    });
</script>
</pwm:script>
</body>
</html>
