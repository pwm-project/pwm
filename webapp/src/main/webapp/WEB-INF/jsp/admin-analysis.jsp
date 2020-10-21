<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.i18n.Admin" %>
<%@ page import="password.pwm.svc.stats.AvgStatistic" %>
<%@ page import="password.pwm.svc.stats.DailyKey" %>
<%@ page import="password.pwm.svc.stats.Statistic" %>
<%@ page import="password.pwm.svc.stats.StatisticType" %>
<%@ page import="password.pwm.svc.stats.StatisticsBundle" %>
<%@ page import="password.pwm.svc.stats.StatisticsManager" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.time.format.DateTimeFormatter" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final Locale locale = JspUtility.locale(request);

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
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <% final String PageName = JspUtility.localizedString(pageContext,"Title_DataAnalysis",Admin.class);%>
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=PageName%>"/>
    </jsp:include>
    <div id="centerbody" class="wide">
        <h1 id="page-content-title"><pwm:display key="Title_DataAnalysis" bundle="Admin"/></h1>
        <%@ include file="fragment/admin-nav.jsp" %>
        <div class="tab-container" style="width: 100%; height: 100%;" id="analysis-topLevelTab">
            <input name="tabs" type="radio" id="tab-1" checked="checked" class="input"/>
            <label for="tab-1" class="label"><pwm:display key="Title_DirectoryReporting" bundle="Admin"/></label>
            <div class="tab-content-pane" style="width: 100%; height: 100%;" title="<pwm:display key="Title_DirectoryReporting" bundle="Admin"/>">
                <div class="tab-container" style="width: 100%; height: 100%;">
                    <input name="dr_tabs" type="radio" id="tab-1.1" checked="checked" class="input"/>
                    <label for="tab-1.1" class="label"><pwm:display key="Title_ReportEngineStatus" bundle="Admin"/></label>
                    <div class="tab-content-pane" title="<pwm:display key="Title_ReportEngineStatus" bundle="Admin"/>" class="tabContent">
                        <table style="width:450px" id="statusTable">
                            <tr><td><pwm:display key="Display_PleaseWait"/></td></tr>
                        </table>
                        <table style="width:450px;">
                            <tr><td style="text-align: center; cursor: pointer">
                                <button id="reportStartButton" class="btn">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-play">&nbsp;</span></pwm:if>
                                    <pwm:display key="Button_Report_Start" bundle="Admin"/>
                                </button>
                                &nbsp;&nbsp;
                                <button id="reportStopButton" class="btn">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-stop">&nbsp;</span></pwm:if>
                                    <pwm:display key="Button_Report_Stop" bundle="Admin"/>
                                </button>
                                &nbsp;&nbsp;
                                <button id="reportClearButton" class="btn">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-trash-o">&nbsp;</span></pwm:if>
                                    <pwm:display key="Button_Report_Clear" bundle="Admin"/>
                                </button>
                            </td></tr>
                        </table>
                    </div>

                    <input name="dr_tabs" type="radio" id="tab-1.2" class="input"/>
                    <label for="tab-1.2" class="label">Summary</label>
                    <div class="tab-content-pane" title="Summary" class="tabContent">
                        <div style="max-height: 500px; overflow-y: auto" id="summaryTableWrapper">
                            <table id="summaryTable">
                                <tr><td><pwm:display key="Display_PleaseWait"/></td></tr>
                            </table>
                        </div>
                        <div class="noticebar">
                            <pwm:display key="Notice_DynamicRefresh" bundle="Admin"/>
                            <pwm:display key="Notice_ReportSummary" bundle="Admin"/>
                        </div>
                        <div style="text-align: center">
                            <form class="submitToDownloadForm" action="<pwm:current-url/>" method="post">
                                <button type="submit" class="btn" id="button-downloadUserSummaryCsv">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download">&nbsp;</span></pwm:if>
                                    <pwm:display key="Button_DownloadCSV" bundle="Admin"/>
                                </button>
                                <input type="hidden" name="processAction" value="downloadUserSummaryCsv"/>
                                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                            </form>
                        </div>
                    </div>

                    <input name="dr_tabs" type="radio" id="tab-1.3" class="input"/>
                    <label for="tab-1.3" class="label"><pwm:display key="Title_DataViewer" bundle="Admin"/></label>
                    <div class="tab-content-pane" title="<pwm:display key="Title_DataViewer" bundle="Admin"/>" class="tabContent">
                        <div id="grid">
                        </div>
                        <div style="text-align: center">
                            <input name="maxResults" id="maxReportDataResults" value="1000" type="number" style="width: 70px"
                                   min="10" max="50000" step="100"/>
                            Rows
                            <button class="btn" type="button" id="button-refreshReportDataGrid">
                                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh">&nbsp;</span></pwm:if>
                                <pwm:display key="Button_Refresh" bundle="Admin"/>
                            </button>
                            <form class="submitToDownloadForm" id="downloadUserReportCsvForm" action="<pwm:current-url/>" method="post">
                                <button type="submit" class="btn" id="button-downloadUserReportCsv">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download">&nbsp;</span></pwm:if>
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
                                <input type="hidden" name="selectedColumns" value="" />
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

                    <div class="tab-end"></div>
                </div>
            </div>

            <input name="tabs" type="radio" id="tab-2" class="input"/>
            <label for="tab-2" class="label"><pwm:display key="Title_EventStatistics" bundle="Admin"/></label>
            <div class="tab-content-pane" style="width: 100%; height: 100%;" title="<pwm:display key="Title_EventStatistics" bundle="Admin"/>">
                <div class="tab-container" style="width: 100%; height: 100%;">
                    <input name="es_tabs" type="radio" id="tab-2.1" checked="checked" class="input"/>
                    <label for="tab-2.1" class="label"><pwm:display key="Title_RawStatistics" bundle="Admin"/></label>
                    <div class="tab-content-pane" title="<pwm:display key="Title_RawStatistics" bundle="Admin"/>" class="tabContent">
                        <div style="max-height: 500px; overflow-y: auto">
                            <table>
                                <tr>
                                    <td colspan="10" style="text-align: center">
                                        <form action="<pwm:current-url/>" method="GET" enctype="application/x-www-form-urlencoded"
                                              name="statsUpdateForm" id="statsUpdateForm">
                                            <select name="statsPeriodSelect"
                                                    style="width: 350px;">
                                                <option value="<%=StatisticsManager.KEY_CUMULATIVE%>" <%= StatisticsManager.KEY_CUMULATIVE.equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>
                                                    since installation - <span class="timestamp"><%= JavaHelper.toIsoDate(analysis_pwmRequest.getPwmApplication().getInstallTime()) %></span>
                                                </option>
                                                <option value="<%=StatisticsManager.KEY_CURRENT%>" <%= StatisticsManager.KEY_CURRENT.equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>
                                                    since startup - <span class="timestamp"><%= JavaHelper.toIsoDate(analysis_pwmRequest.getPwmApplication().getStartupTime()) %></span>
                                                </option>
                                                <% final Map<DailyKey, String> availableKeys = statsManager.getAvailableKeys(locale); %>
                                                <% for (final Map.Entry<DailyKey, String> entry : availableKeys.entrySet()) { %>
                                                <% final DailyKey key = entry.getKey(); %>
                                                <option value="<%=key%>" <%= key.toString().equals(statsPeriodSelect) ? "selected=\"selected\"" : "" %>>
                                                    <%=key.localDate().format(DateTimeFormatter.ISO_LOCAL_DATE)%>
                                                </option>
                                                <% } %>
                                            </select>
                                            <button class="btn" type="submit">
                                                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh">&nbsp;</span></pwm:if>
                                                <pwm:display key="Button_Refresh" bundle="Admin"/>
                                            </button>
                                        </form>
                                    </td>
                                </tr>
                                <% for (final Statistic loopStat : Statistic.sortedValues(locale)) { %>
                                <tr>
                                    <td >
                                        <span id="Statistic_Key_<%=loopStat.getKey()%>"><%= loopStat.getLabel(locale) %><span/>
                                    </td>
                                    <td>
                                        <%= stats.getStatistic(loopStat) %>
                                    </td>
                                </tr>
                                <% } %>
                                <% for (final AvgStatistic loopStat : AvgStatistic.values()) { %>
                                <tr>
                                    <td >
                                        <span id="Statistic_Key_<%=loopStat.getKey()%>"><%= loopStat.getLabel(locale) %><span/>
                                    </td>
                                    <td>
                                        <%= stats.getAvgStatistic(loopStat) %><%= loopStat.getUnit() %>
                                    </td>
                                </tr>
                                <% } %>
                            </table>
                        </div>
                        <div class="noticebar">
                            <pwm:display key="Notice_EventStatistics" bundle="Admin"/>
                        </div>
                        <div style="text-align: center">
                            <form class="submitToDownloadForm" action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded">
                                <button type="submit" class="btn" id="button-downloadStatisticsLogCsv">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download"></span></pwm:if>
                                    <pwm:display key="Button_DownloadCSV" bundle="Admin"/>
                                </button>
                                <input type="hidden" name="processAction" value="downloadStatisticsLogCsv"/>
                                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                            </form>
                        </div>
                    </div>

                    <input name="es_tabs" type="radio" id="tab-2.2" class="input"/>
                    <label for="tab-2.2" class="label"><pwm:display key="Title_StatisticsCharts" bundle="Admin"/></label>
                    <div class="tab-content-pane" title="<pwm:display key="Title_StatisticsCharts" bundle="Admin"/>" class="tabContent">
                        <div style="height:100%; width: 100%">
                            <div id="statsChartOptionsDiv" style="width:580px; text-align: center; margin:0 auto;">
                                <label for="statsChartSelect">Statistic</label>
                                <select name="statsChartSelect" id="statsChartSelect" style="width: 300px;">
                                    <% for (final Statistic loopStat : Statistic.sortedValues(locale)) { %>
                                    <option value="<%=loopStat %>"><%=loopStat.getLabel(locale)%></option>
                                    <% } %>
                                </select>
                                <label for="statsChartDays" style="padding-left: 10px">Days</label>
                                <input id="statsChartDays" value="30" type="number" style="width: 60px" min="7" max="120"/>
                            </div>
                            <div id="statsChart">
                            </div>
                        </div>
                    </div>

                    <div class="tab-end"></div>
                </div>
            </div>

        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function refreshChart() {
            var statsChartSelect = PWM_MAIN.getObject('statsChartSelect');
            var keyName = statsChartSelect.options[statsChartSelect.selectedIndex].value;
            var days = PWM_MAIN.getObject('statsChartDays').value;
            PWM_ADMIN.showStatChart(keyName,days,'statsChart',{});
        }


        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo","dojo/query"],function(dojo,query){
                PWM_MAIN.JSLibrary.setValueOfSelectElement('statsChartSelect','<%=Statistic.PASSWORD_CHANGES%>');

                setTimeout(function(){
                    refreshChart();
                },5*1000);

                PWM_ADMIN.refreshReportDataSummary();
                PWM_ADMIN.refreshReportDataStatus();
                setInterval(function () { PWM_ADMIN.refreshReportDataSummary() }, 5 * 1000);
                setInterval(function () { PWM_ADMIN.refreshReportDataStatus() }, 5 * 1000);

                <% for (final Statistic loopStat : Statistic.sortedValues(locale)) { %>
                PWM_MAIN.showTooltip({id:'Statistic_Key_<%=loopStat.getKey()%>',width:400,position:'above',text:PWM_ADMIN.showString("Statistic_Description.<%=loopStat.getKey()%>")});
                <% } %>

                PWM_MAIN.addEventHandler('button-refreshReportDataGrid','click',function(){
                    PWM_ADMIN.refreshReportDataGrid();
                });
                PWM_MAIN.addEventHandler('reportStartButton','click',function(){ PWM_ADMIN.reportAction('Start') });
                PWM_MAIN.addEventHandler('reportStopButton','click',function(){ PWM_ADMIN.reportAction('Stop') });
                PWM_MAIN.addEventHandler('reportClearButton','click',function(){ PWM_ADMIN.reportAction('Clear') });
                PWM_MAIN.addEventHandler('statsChartSelect','change',function(){ refreshChart() });

                if (dojo.isIE) {
                    <%--
                    // The tab containers on this page go wacky in Internet Explorer if the form downloads are submitted
                    // to the current page.  This is a workaround to submit the forms to a blank page instead.
                    --%>
                    query("form.submitToDownloadForm").forEach(function(node, index, arr) {
                        node.target = "_blank";
                    });
                }
            });
        });
    </script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
