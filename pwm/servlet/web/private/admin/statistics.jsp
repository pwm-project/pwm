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

<%@ page import="password.pwm.PwmApplication" %>
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
<% final String statsPeriodSelect = password.pwm.Validator.readStringFromRequest(request, "statsPeriodSelect", 255); %>
<% final String statsChartSelect = password.pwm.Validator.readStringFromRequest(request, "statsChartSelect", 255).length() > 0 ? password.pwm.Validator.readStringFromRequest(request, "statsChartSelect", 255) : Statistic.PASSWORD_CHANGES.toString(); %>
<% final StatisticsBundle stats = statsManager.getStatBundleForKey(statsPeriodSelect); %>
<% final Locale locale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale(); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, locale); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="PWM Statistics"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <%!
            static String makeGoogleChartImageUrl(Statistic stat, StatisticsManager statsManager) {
                final Map<String, String> chartData = statsManager.getStatHistory(stat, 31);
                int topValue = 0;
                for (final String value : chartData.values())
                    topValue = Integer.parseInt(value) > topValue ? Integer.parseInt(value) : topValue;
                final StringBuilder imgURL = new StringBuilder();
                imgURL.append("http://chart.apis.google.com/chart");
                imgURL.append("?cht=bvs");
                imgURL.append("&chs=590x150");
                imgURL.append("&chds=0,").append(topValue);
                imgURL.append("&chco=d20734");
                imgURL.append("&chxt=x,y");
                imgURL.append("&chxr=1,0,").append(topValue);
                imgURL.append("&chbh=14");
                imgURL.append("&chd=t:");
                for (final String value : chartData.values()) imgURL.append(value).append(",");
                imgURL.delete(imgURL.length() - 1, imgURL.length());
                imgURL.append("&chl=");
                int counter = 0;
                for (final String value : chartData.keySet()) {
                    if (counter % 3 == 0) {
                        imgURL.append(value).append("|");
                    } else {
                        imgURL.append(" |");
                    }
                    counter++;
                }
                imgURL.delete(imgURL.length() - 1, imgURL.length());
                return imgURL.toString();
            }
        %>
        <form action="<pwm:url url='statistics.jsp'/>" method="GET" enctype="application/x-www-form-urlencoded"
              name="statsUpdateForm"
              id="statsUpdateForm"
              onsubmit="getObject('submit_button').value = ' Please Wait ';getObject('submit_button').disabled = true">
            <table class="tablemain">
                <tr>
                    <td class="title" colspan="10">
                        Statistics
                    </td>
                </tr>
                <tr>
                    <td colspan="10" style="text-align: center">
                        <select name="statsPeriodSelect" onchange="getObject('statsUpdateForm').submit();">
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
                        <noscript>
                            <input type="submit" id="submit_button_period" class="btn" value="Update"/>
                        </noscript>
                    </td>
                </tr>
                <% for (Iterator<Statistic> iter = Statistic.sortedValues(locale).iterator(); iter.hasNext();) { %>
                <% Statistic leftStat = iter.next(); %>
                <tr>
                    <td class="key">
                        <%= leftStat.getLabel(locale) %>
                    </td>
                    <td>
                        <%= stats.getStatistic(leftStat) %><%= leftStat.getType() == Statistic.Type.AVERAGE ? " ms" : "" %>
                    </td>
                    <% if (iter.hasNext()) { %>
                    <% Statistic rightStat = iter.next(); %>
                    <td class="key">
                        <%= rightStat.getLabel(locale) %>
                    </td>
                    <td>
                        <%= stats.getStatistic(rightStat) %><%= rightStat.getType() == Statistic.Type.AVERAGE ? " ms" : "" %>
                    </td>
                    <% } else { %>
                    <td colspan="2">
                        &nbsp;
                    </td>
                    <% } %>
                </tr>
                <% } %>
            </table>
            <br class="clear"/>
            <table class="tablemain">
                <tr>
                    <td class="title" colspan="10">
                        Activity
                    </td>
                </tr>
                <tr>
                    <td colspan="10" style="text-align: center">
                        <select name="statsChartSelect" id="statsChartSelect"
                                onchange="getObject('googleChartImage').src=getObject('statsChartSelect').options[getObject('statsChartSelect').selectedIndex].title;">
                            <% for (final Statistic loopStat : Statistic.sortedValues(locale)) { %>
                            <option value="<%=loopStat %>" <%= loopStat.toString().equals(statsChartSelect) ? "selected=\"selected\"" : "" %>
                                    title="<%=makeGoogleChartImageUrl(loopStat,statsManager)%>"><%=loopStat.getLabel(locale)%>
                            </option>
                            <% } %>
                        </select>
                        <br/>
                        <noscript>
                            <input type="submit" id="submit_button_chart" class="btn" value="Update"/>
                        </noscript>
                        <img id="googleChartImage"
                             src="<%=makeGoogleChartImageUrl(Statistic.valueOf(statsChartSelect),statsManager)%>"
                             alt="[ Google Chart Image ]"/>
                    </td>
                </tr>
            </table>
        </form>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
