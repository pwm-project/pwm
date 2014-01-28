<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="password.pwm.util.stats.StatisticsBundle" %>
<%@ page import="password.pwm.util.stats.StatisticsManager" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.TimeZone" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<%
    if ("start".equalsIgnoreCase(request.getParameter("reportAction"))) {pwmApplicationHeader.getUserReportService().scheduleImmediateUpdate(); }
    if ("stop".equalsIgnoreCase(request.getParameter("reportAction"))) {pwmApplicationHeader.getUserReportService().cancelUpdate();}
    if ("clear".equalsIgnoreCase(request.getParameter("reportAction"))) {pwmApplicationHeader.getUserReportService().clear();}
%>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Data Analysis"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;"  data-dojo-props="doLayout: false">
            <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false" title="Directory Reporting">
                <% if (pwmApplicationHeader.getConfig().readSettingAsBoolean(PwmSetting.REPORTING_ENABLE)) { %>
                <div data-dojo-type="dijit.layout.ContentPane" title="Summary">
                    <div style="height: 100%">
                        <table id="summaryTable">
                            <tr><td><pwm:Display key="Display_PleaseWait"/></td></tr>
                        </table>
                        <div class="noticebar">
                            <pwm:Display key="Notice_DynamicRefresh" bundle="Admin"/>
                            <%--
                            <button class="btn" type="button" onclick="PWM_ADMIN.refreshReportDataSummary()">
                                <span class="fa fa-refresh">&nbsp;Refresh</span>
                            </button>
                            --%>
                        </div>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_ADMIN.refreshReportDataSummary(5 * 1000);
                            });
                        </script>
                    </div>
                </div>
                <div data-dojo-type="dijit.layout.ContentPane" title="Data Viewer">
                    <div id="grid">
                    </div>
                    <div style="text-align: center">
                        <input name="maxResults" id="maxReportDataResults" value="1000" data-dojo-type="dijit.form.NumberSpinner" style="width: 70px"
                               data-dojo-props="constraints:{min:10,max:50000,pattern:'#'},smallDelta:100"/>
                        Rows
                        <button class="btn" type="button" onclick="PWM_ADMIN.refreshReportDataGrid()">
                            <span class="fa fa-refresh">&nbsp;Refresh</span>
                        </button>
                    </div>
                    <div style="text-align: center">
                        <form action="<%=request.getContextPath()%><pwm:url url="/private/CommandServlet"/>" method="GET">
                            <button type="submit" class="btn">
                                <span class="fa fa-download">&nbsp;Download as CSV</span>
                            </button>
                            <input type="hidden" name="processAction" value="outputUserReportCsv"/>
                            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                        </form>
                    </div>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            PWM_ADMIN.initReportDataGrid();
                        });
                    </script>
                </div>
                <div data-dojo-type="dijit.layout.ContentPane" title="Report Engine Status">
                    <table style="width:400px" id="statusTable">
                        <tr><td><pwm:Display key="Display_PleaseWait"/></td></tr>
                    </table>
                    <table style="width:400px;">
                        <tr><td style="text-align: center; text-decoration: no-underline; cursor: pointer">
                            <button id="reportStartButton" class="btn" onclick="PWM_ADMIN.reportAction('start')">
                                <i class="fa fa-play">&nbsp;Start</i>
                            </button>
                            &nbsp;&nbsp;
                            <button id="reportStopButton" class="btn" onclick="PWM_ADMIN.reportAction('stop')">
                                <i class="fa fa-stop">&nbsp;Stop</i>
                            </button>
                            &nbsp;&nbsp;
                            <button id="reportClearButton" class="btn" onclick="PWM_ADMIN.reportAction('clear')">
                                <span class="fa fa-trash-o">&nbsp;Clear</span>
                            </button>
                        </td></tr>
                    </table>
                    <script type="application/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            PWM_ADMIN.refreshReportDataStatus(5 * 1000);
                        });
                    </script>
                </div>
                <% } else { %>
                <div class="message message-error">
                    <%= PwmError.ERROR_SERVICE_NOT_AVAILABLE.getLocalizedMessage(pwmSessionHeader.getSessionStateBean().getLocale(),pwmApplicationHeader.getConfig()) %>
                </div>
                <% } %>
            </div>
            <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false" title="Event Statistics">
                <div data-dojo-type="dijit.layout.ContentPane" title="Raw Statistics">
                    <% final PwmApplication pwmApplication = ContextManager.getPwmApplication(session); %>
                    <% final StatisticsManager statsManager = ContextManager.getPwmApplication(session).getStatisticsManager(); %>
                    <% final String statsPeriodSelect = password.pwm.Validator.readStringFromRequest(request, "statsPeriodSelect"); %>
                    <% final String statsChartSelect = password.pwm.Validator.readStringFromRequest(request, "statsChartSelect").length() > 0 ? password.pwm.Validator.readStringFromRequest(request, "statsChartSelect") : Statistic.PASSWORD_CHANGES.toString(); %>
                    <% final StatisticsBundle stats = statsManager.getStatBundleForKey(statsPeriodSelect); %>
                    <% final Locale locale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
                    <% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, locale); dateFormat.setTimeZone(TimeZone.getTimeZone("Zulu")); %>
                    <div style="max-height: 350px; overflow-y: auto">
                        <table class="tablemain" id="form">
                            <tr>
                                <td colspan="10" style="text-align: center">
                                    <form action="<pwm:url url='userreport.jsp'/>" method="GET" enctype="application/x-www-form-urlencoded"
                                          name="statsUpdateForm"
                                          id="statsUpdateForm"
                                          onsubmit="PWM_MAIN.getObject('submit_button').value = ' Please Wait ';PWM_MAIN.getObject('submit_button').disabled = true">
                                        <select name="statsPeriodSelect" onchange="PWM_MAIN.getObject('statsUpdateForm').submit();"
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
                                <td >
                                    <%= leftStat.getLabel(locale) %>
                                </td>
                                <td>
                                    <%= stats.getStatistic(leftStat) %><%= leftStat.getType() == Statistic.Type.AVERAGE && leftStat != Statistic.AVG_PASSWORD_STRENGTH ? " ms" : "" %>
                                </td>
                            </tr>
                            <% } %>
                        </table>
                    </div>
                    <div style="text-align: center">

                        <button type="button" onclick="downloadCsv()" name="statisticsDownloadButton" class="btn">
                            <span class="fa fa-download" >&nbsp;Download as CSV</span>
                        </button>
                    </div>
                </div>
                <div data-dojo-type="dijit.layout.ContentPane" title="Event Charts">
                    <div style="height:100%; width: 100%">
                        <div id="statsChartOptionsDiv" style="width:580px; text-align: center; margin:0 auto;">
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
                        <div id="statsChart">
                        </div>
                    </div>

                </div>
            </div>
            <div class="push"></div>
        </div>
    </div>
</div>
<script>
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/registry","dojo/ready","dijit/form/Select","dijit/form/NumberSpinner","dijit/layout/TabContainer","dijit/layout/ContentPane"],function(dojoParser,registry,ready){
            ready(function(){
                dojoParser.parse();
                registry.byId('statsChartSelect').set('value','<%=Statistic.PASSWORD_CHANGES%>');
                setTimeout(function(){
                    refreshChart();
                },5*1000);
            });
        });
    });

    function refreshChart() {
        require(["dijit/registry"],function(registry){
            var keyName = registry.byId('statsChartSelect').get('value');
            var days = PWM_MAIN.getObject('statsChartDays').value;
            PWM_MAIN.showStatChart(keyName,days,'statsChart');
        });
    }
    function downloadCsv() {
        window.location.href='<%=request.getContextPath()%><pwm:url url="/public/rest/statistics/file"/>?pwmFormID=<pwm:FormID/>';
    }

</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
