<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2021 The PWM Project
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
<%@ page import="password.pwm.svc.stats.StatisticsBundleKey" %>
<%@ page import="password.pwm.svc.stats.Statistic" %>
<%@ page import="password.pwm.svc.stats.StatisticType" %>
<%@ page import="password.pwm.svc.stats.StatisticsBundle" %>
<%@ page import="password.pwm.svc.stats.StatisticsService" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="password.pwm.util.java.PwmUtil" %>
<%@ page import="password.pwm.http.PwmRequestFlag" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.http.JspUtility" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <% final String PageName = JspUtility.localizedString(pageContext,"Title_Statistics",Admin.class);%>
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=PageName%>"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="fragment/admin-modular-nav.jsp" %>
        <div>
            <br/><br/>
            <h1 class="center">
                <pwm:display key="Title_Statistics" bundle="Admin"/>
                <form id="statsPeriodForm" name="statsPeriodForm">
                    <select name="statsPeriodSelect" id="statsPeriodSelect">
                    </select>
                </form>
            </h1>
            <br/>
            <table>
                <thead>
                <tr><td colspan="2" class="title">Counters</td></tr>
                </thead>

                <tbody id="statisticsTable">
                <tr><td><pwm:display key="Display_PleaseWait"/></td></tr>
                </tbody>
            </table>
            <br/>
            <table>
                <thead>
                <tr><td colspan="2" class="title">Averages</td></tr>
                </thead>

                <tbody id="averageStatisticsTable">
                <tr><td><pwm:display key="Display_PleaseWait"/></td></tr>
                </tbody>
            </table>
        </div>
        <br/>

        <div class="noticebar">
            <pwm:display key="Notice_EventStatistics" bundle="Admin"/>
        </div>
        <br/>

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
</div>
<div class="push"></div>
</div>
<script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
    import {PWM_ADMIN_STATISTICS} from "<pwm:url url="/public/resources/js/admin-statistics.js" addContext="true"/>";
    PWM_ADMIN_STATISTICS.initStatisticsPage();
</script>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
