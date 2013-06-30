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

<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="password.pwm.util.stats.StatisticsBundle" %>
<%@ page import="password.pwm.util.stats.StatisticsManager" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(session); %>
<% final StatisticsManager statsManager = ContextManager.getPwmApplication(session).getStatisticsManager(); %>
<% final String statsPeriodSelect = password.pwm.Validator.readStringFromRequest(request, "statsPeriodSelect"); %>
<% final String statsChartSelect = password.pwm.Validator.readStringFromRequest(request, "statsChartSelect").length() > 0 ? password.pwm.Validator.readStringFromRequest(request, "statsChartSelect") : Statistic.PASSWORD_CHANGES.toString(); %>
<% final StatisticsBundle stats = statsManager.getStatBundleForKey(statsPeriodSelect); %>
<% final Locale locale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, locale); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler()">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Statistics"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <br/>
        <div id="statsChartOptionsDiv" style="width:600px; text-align: center; margin:0 auto;">
            <label for="statsChartSelect">Statistic</label>
            <select name="statsChartSelect" id="statsChartSelect" data-dojo-type="dijit.form.Select" style="width: 300px;" data-dojo-props="maxHeight: -1"
                    onchange="refreshChart()">
                <% for (final Statistic loopStat : Statistic.sortedValues(locale)) { %>
                <option value="<%=loopStat %>"><%=loopStat.getLabel(locale)%></option>
                <% } %>
            </select>
            <label for="statsChartDays" style="padding-left: 10px">Days</label>
            <input id="statsChartDays" value="30" data-dojo-type="dijit.form.NumberSpinner" style="width: 60px"
                   data-dojo-props="constraints:{min:7,max:120}" onclick="refreshChart()"/>
        </div>
        <div id="statsChart" style="height: 200px; width: 600px">
        </div>
        <table class="tablemain" id="form">
            <tr>
                <td class="title" colspan="10">
                    Statistics
                </td>
            </tr>
            <tr>
                <td colspan="10" style="text-align: center">
                    <form action="<pwm:url url='statistics.jsp'/>" method="GET" enctype="application/x-www-form-urlencoded"
                          name="statsUpdateForm"
                          id="statsUpdateForm"
                          onsubmit="getObject('submit_button').value = ' Please Wait ';getObject('submit_button').disabled = true">
                        <select name="statsPeriodSelect" onchange="getObject('statsUpdateForm').submit();"
                                data-dojo-type="dijit.form.Select" style="width: 500px;" data-dojo-props="maxHeight: -1">
                            <option value="<%=StatisticsManager.KEY_CUMULATIVE%>" <%= StatisticsManager.KEY_CUMULATIVE.equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>
                                since installation - <%= dateFormat.format(pwmApplication.getInstallTime()) %>
                            </option>
                            <option value="<%=StatisticsManager.KEY_CURRENT%>" <%= StatisticsManager.KEY_CURRENT.equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>
                                since startup - <%= dateFormat.format(pwmApplication.getStartupTime()) %>
                            </option>
                            <% final Map<StatisticsManager.DailyKey, String> availableKeys = statsManager.getAvailableKeys(locale); %>
                            <% for (final StatisticsManager.DailyKey key : availableKeys.keySet()) { %>
                            <option value="<%=key%>" <%= key.toString().equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>><%= availableKeys.get(key) %>
                                GMT
                            </option>
                            <% } %>
                        </select>
                    </form>
                </td>
            </tr>
            <% for (Iterator<Statistic> iter = Statistic.sortedValues(locale).iterator(); iter.hasNext();) { %>
            <% Statistic leftStat = iter.next(); %>
            <tr>
                <td class="key">
                    <%= leftStat.getLabel(locale) %>
                </td>
                <td>
                    <%= stats.getStatistic(leftStat) %><%= leftStat.getType() == Statistic.Type.AVERAGE && leftStat != Statistic.AVG_PASSWORD_STRENGTH ? " ms" : "" %>
                </td>
                <% if (iter.hasNext()) { %>
                <% Statistic rightStat = iter.next(); %>
                <td class="key">
                    <%= rightStat.getLabel(locale) %>
                </td>
                <td>
                    <%= stats.getStatistic(rightStat) %><%= rightStat.getType() == Statistic.Type.AVERAGE && rightStat != Statistic.AVG_PASSWORD_STRENGTH ? " ms" : "" %>
                </td>
                <% } else { %>
                <td colspan="2">
                    &nbsp;
                </td>
                <% } %>
            </tr>
            <% } %>
        </table>
        <div id="buttonbar">
            <button type="button" onclick="downloadCsv()"name="button" class="btn">
                Download Statistics CSV File
            </button>
        </div>
    </div>
    <div class="push"></div>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dijit/registry","dijit/form/Select","dijit/form/NumberSpinner","dojo/domReady!"],function(dojoParser,registry){
                dojoParser.parse();
                registry.byId('statsChartSelect').set('value','<%=Statistic.PASSWORD_CHANGES%>');
                setTimeout(function(){
                    refreshChart();
                },60 * 1000)
            });
        });
        function refreshChart() {
            require(["dijit/registry"],function(registry){
                var keyName = registry.byId('statsChartSelect').get('value');
                var days = getObject('statsChartDays').value;
                showStatChart(keyName,days,'statsChart');
            });
        }
        function downloadCsv() {
            window.location.href='<%=request.getContextPath()%><pwm:url url="/public/rest/statistics/file"/>?pwmFormID=<pwm:FormID/>';
        }
    </script>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
