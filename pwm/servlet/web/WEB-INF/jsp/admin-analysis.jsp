<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="password.pwm.util.stats.StatisticsBundle" %>
<%@ page import="password.pwm.util.stats.StatisticsManager" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Map" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final Locale locale = JspUtility.locale(request);
    final DateFormat dateFormat = PwmConstants.DEFAULT_DATETIME_FORMAT;

    StatisticsManager statsManager = null;
    String statsPeriodSelect = "";
    String statsChartSelect = "";
    StatisticsBundle stats = null;
    PwmRequest analysis_pwmRequest = null;
    try {
        analysis_pwmRequest = PwmRequest.forRequest(request, response);
        statsManager = analysis_pwmRequest.getPwmApplication().getStatisticsManager();
        statsPeriodSelect = analysis_pwmRequest.readParameterAsString("statsPeriodSelect");
        statsChartSelect = analysis_pwmRequest.readParameterAsString("statsChartSelect",Statistic.PASSWORD_CHANGES.toString());
        stats = statsManager.getStatBundleForKey(statsPeriodSelect);
    } catch (PwmException e) {
        JspUtility.logError(pageContext, "error during page setup: " + e.getMessage());
    }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Data Analysis"/>
    </jsp:include>
    <div id="centerbody" class="wide">
        <%@ include file="fragment/admin-nav.jsp" %>
        <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;"  data-dojo-props="doLayout: false, persist: true" id="analysis-topLevelTab">
            <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true" title="<pwm:display key="Title_DirectoryReporting" bundle="Admin"/>">
                <% if (analysis_pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.REPORTING_ENABLE)) { %>
                <div data-dojo-type="dijit.layout.ContentPane" title="Summary" class="tabContent">
                    <div style="margin-left: auto; margin-right: auto" id="summaryTableWrapper">
                        <table id="summaryTable" style="max-width:600px">
                            <tr><td><pwm:display key="Display_PleaseWait"/></td></tr>
                        </table>
                        <div class="noticebar">
                            <pwm:display key="Notice_DynamicRefresh" bundle="Admin"/>
                        </div>
                    </div>
                    <div style="text-align: center">
                        <form action="<pwm:url url="Administration"/>" method="post">
                            <button type="submit" class="btn" id="button-downloadUserSummaryCsv">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-download">&nbsp;</span></pwm:if>
                                <pwm:display key="Button_DownloadCSV" bundle="Admin"/>
                            </button>
                            <input type="hidden" name="processAction" value="downloadUserSummaryCsv"/>
                            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                        </form>
                    </div>
                </div>
                <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_DataViewer" bundle="Admin"/>" class="tabContent">
                    <div id="grid">
                    </div>
                    <div style="text-align: center">
                        <input name="maxResults" id="maxReportDataResults" value="1000" data-dojo-type="dijit.form.NumberSpinner" style="width: 70px"
                               data-dojo-props="constraints:{min:10,max:50000,pattern:'#'},smallDelta:100"/>
                        Rows
                        <button class="btn" type="button" id="button-refreshReportDataGrid">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-refresh">&nbsp;</span></pwm:if>
                            <pwm:display key="Button_Refresh" bundle="Admin"/>
                        </button>
                        <form action="<pwm:url url="Administration"/>" method="post">
                            <button type="submit" class="btn" id="button-downloadUserReportCsv">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-download">&nbsp;</span></pwm:if>
                                <pwm:display key="Button_DownloadCSV" bundle="Admin"/>
                            </button>
                            <pwm:script>
                                <script type="application/javascript">
                                    PWM_GLOBAL['startupFunctions'].push(function(){
                                        PWM_MAIN.showTooltip({
                                            id: 'button-downloadUserReportCsv',
                                            text: '<pwm:display key="Tooltip_DownloadReportRecords" bundle="Admin"/>',
                                            width: 350
                                        });
                                    });
                                </script>
                            </pwm:script>
                            <input type="hidden" name="processAction" value="downloadUserReportCsv"/>
                            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                        </form>
                    </div>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_ADMIN.initReportDataGrid();
                            });
                        </script>
                    </pwm:script>
                </div>
                <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_ReportEngineStatus" bundle="Admin"/>" class="tabContent">
                    <table style="width:450px" id="statusTable">
                        <tr><td><pwm:display key="Display_PleaseWait"/></td></tr>
                    </table>
                    <table style="width:450px;">
                        <tr><td style="text-align: center; cursor: pointer">
                            <button id="reportStartButton" class="btn">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-play">&nbsp;</span></pwm:if>
                                <pwm:display key="Button_Report_Start" bundle="Admin"/>
                            </button>
                            &nbsp;&nbsp;
                            <button id="reportStopButton" class="btn">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-stop">&nbsp;</span></pwm:if>
                                <pwm:display key="Button_Report_Stop" bundle="Admin"/>
                            </button>
                            &nbsp;&nbsp;
                            <button id="reportClearButton" class="btn">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-trash-o">&nbsp;</span></pwm:if>
                                <pwm:display key="Button_Report_Clear" bundle="Admin"/>
                            </button>
                        </td></tr>
                    </table>
                </div>
                <% } else { %>
                <div>
                    <%= PwmError.ERROR_SERVICE_NOT_AVAILABLE.getLocalizedMessage(analysis_pwmRequest.getLocale(),
                            analysis_pwmRequest.getConfig()) %>
                </div>
                <% } %>
            </div>
            <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true" title="<pwm:display key="Title_EventStatistics" bundle="Admin"/>">
                <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_RawStatistics" bundle="Admin"/>" class="tabContent">
                    <div style="max-height: 350px; overflow-y: auto">
                        <table>
                            <tr>
                                <td colspan="10" style="text-align: center">
                                    <form action="<pwm:url url='Administration'/>" method="GET" enctype="application/x-www-form-urlencoded"
                                          name="statsUpdateForm" id="statsUpdateForm">
                                        <select name="statsPeriodSelect"
                                                style="width: 500px;" data-dojo-props="maxHeight: -1">
                                            <option value="<%=StatisticsManager.KEY_CUMULATIVE%>" <%= StatisticsManager.KEY_CUMULATIVE.equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>
                                                since installation - <%= dateFormat.format(analysis_pwmRequest.getPwmApplication().getInstallTime()) %>
                                            </option>
                                            <option value="<%=StatisticsManager.KEY_CURRENT%>" <%= StatisticsManager.KEY_CURRENT.equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>
                                                since startup - <%= dateFormat.format(analysis_pwmRequest.getPwmApplication().getStartupTime()) %>
                                            </option>
                                            <% final Map<StatisticsManager.DailyKey, String> availableKeys = statsManager.getAvailableKeys(locale); %>
                                            <% for (final StatisticsManager.DailyKey key : availableKeys.keySet()) { %>
                                            <option value="<%=key%>" <%= key.toString().equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>
                                                <%= availableKeys.get(key) %>
                                            </option>
                                            <% } %>
                                        </select>
                                        <button class="btn" type="submit">
                                            <pwm:if test="showIcons"><span class="btn-icon fa fa-refresh">&nbsp;</span></pwm:if>
                                            <pwm:display key="Button_Refresh" bundle="Admin"/>
                                        </button>
                                    </form>
                                </td>
                            </tr>
                            <% for (Statistic loopStat : Statistic.sortedValues(locale)) { %>
                            <tr>
                                <td >
                                    <span id="Statistic_Key_<%=loopStat.getKey()%>"><%= loopStat.getLabel(locale) %><span/>
                                </td>
                                <td>
                                    <%= stats.getStatistic(loopStat) %><%= loopStat.getType() == Statistic.Type.AVERAGE && loopStat != Statistic.AVG_PASSWORD_STRENGTH ? " ms" : "" %>
                                </td>
                            </tr>
                            <% } %>
                        </table>
                    </div>
                    <div style="text-align: center">
                        <form action="Administration" method="post" enctype="application/x-www-form-urlencoded">
                            <button type="submit" class="btn" id="button-downloadStatisticsLogCsv">
                                <pwm:if test="showIcons"><span class="btn-icon fa fa-download"></span></pwm:if>
                                <pwm:display key="Button_DownloadCSV" bundle="Admin"/>
                            </button>
                            <input type="hidden" name="processAction" value="downloadStatisticsLogCsv"/>
                            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                        </form>
                    </div>
                </div>
                <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_StatisticsCharts" bundle="Admin"/>" class="tabContent">
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
                                   data-dojo-props="constraints:{min:7,max:120}"/>
                        </div>
                        <div id="statsChart">
                        </div>
                    </div>

                </div>
            </div>
        </div>
    </div>
</div>
<div class="push">
</div>
<pwm:script>
    <script type="text/javascript">
        function refreshChart() {
            require(["dijit/registry"],function(registry){
                var keyName = registry.byId('statsChartSelect').get('value');
                var days = PWM_MAIN.getObject('statsChartDays').value;
                PWM_ADMIN.showStatChart(keyName,days,'statsChart',{});
            });
        }


        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dijit/registry","dojo/ready","dijit/form/Select","dijit/form/NumberSpinner","dijit/layout/TabContainer","dijit/layout/ContentPane"],function(dojoParser,registry,ready){
                dojoParser.parse('centerbody');
                ready(function(){
                    registry.byId('statsChartSelect').set('value','<%=Statistic.PASSWORD_CHANGES%>');
                    setTimeout(function(){
                        refreshChart();
                    },5*1000);
                    PWM_ADMIN.refreshReportDataSummary(5 * 1000);
                    PWM_ADMIN.refreshReportDataStatus(5 * 1000);
                });

                <% for (Statistic loopStat : Statistic.sortedValues(locale)) { %>
                PWM_MAIN.showTooltip({id:'Statistic_Key_<%=loopStat.getKey()%>',width:400,position:'above',text:PWM_ADMIN.showString("Statistic_Description.<%=loopStat.getKey()%>")});
                <% } %>

                PWM_MAIN.addEventHandler('button-refreshReportDataGrid','click',function(){
                    PWM_ADMIN.refreshReportDataGrid();
                });
                PWM_MAIN.addEventHandler('reportStartButton','click',function(){ PWM_ADMIN.reportAction('start') });
                PWM_MAIN.addEventHandler('reportStopButton','click',function(){ PWM_ADMIN.reportAction('stop') });
                PWM_MAIN.addEventHandler('reportClearButton','click',function(){ PWM_ADMIN.reportAction('clear') });

            });
        });
    </script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
