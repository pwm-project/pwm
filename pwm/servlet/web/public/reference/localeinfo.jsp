<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.LocaleHelper" %>
<%@ page import="password.pwm.i18n.PwmLocaleBundle" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
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
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_THEME); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_FOOTER_TEXT); %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final List<Locale> HIGHLIGHTED_LOCALES = Arrays.asList(new Locale[] {
            LocaleHelper.parseLocaleString("en"),
            LocaleHelper.parseLocaleString("fr"),
            LocaleHelper.parseLocaleString("zh_tw"),
            LocaleHelper.parseLocaleString("de"),
            LocaleHelper.parseLocaleString("ja"),
            LocaleHelper.parseLocaleString("pt_BR"),
            LocaleHelper.parseLocaleString("es"),
            LocaleHelper.parseLocaleString("sv")
    });

    PwmRequest pwmRequest = null;
    LocaleHelper.LocaleStats allStats = null;
    LocaleHelper.LocaleStats userFacingStats = null;
    LocaleHelper.LocaleStats adminFacingStats = null;
    LocaleHelper.ConfigLocaleStats configLocaleStats = null;
    try {
        pwmRequest = PwmRequest.forRequest(request, response);
        allStats = LocaleHelper.getStatsForBundles(PwmLocaleBundle.allValues());
        userFacingStats = LocaleHelper.getStatsForBundles(PwmLocaleBundle.userFacingValues());
        {
            final List<PwmLocaleBundle> adminBundles = new ArrayList<PwmLocaleBundle>(PwmLocaleBundle.allValues());
            adminBundles.removeAll(PwmLocaleBundle.userFacingValues());
            adminFacingStats = LocaleHelper.getStatsForBundles(adminBundles);
        }
        configLocaleStats = LocaleHelper.getConfigLocaleStats();
    } catch (PwmException e) {
        JspUtility.logError(pageContext, "error during page setup: " + e.getMessage());
    }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final NumberFormat numberFormat = NumberFormat.getNumberInstance(); %>
<body class="nihilo">
<div id="wrapper">
    <style nonce="<pwm:value name="cspNonce"/>" >
        .highlight {
            background-color: rgba(255, 255, 0, 0.22);
        }
    </style>
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Localization Report"/>
    </jsp:include>
    <div id="centerbody">
        <h1><a id="localeKeys">Localization Bundles</a></h1>
        <table>
            <tr>
                <td class="title">
                    Name
                </td>
                <td class="title">
                    End User Facing
                </td>
                <td class="title">
                    Keys
                </td>
                <td class="title">
                    Strings
                </td>
            </tr>
            <% for (final PwmLocaleBundle pwmLocaleBundle : PwmLocaleBundle.values()) { %>
            <tr>
                <td>
                    <%=pwmLocaleBundle.getTheClass().getSimpleName()%>
                </td>
                <td>
                    <%=LocaleHelper.booleanString(!pwmLocaleBundle.isAdminOnly(), pwmRequest.getLocale(), pwmRequest.getConfig())%>
                </td>
                <td>
                    <%=numberFormat.format(pwmLocaleBundle.getKeys().size())%>
                </td>
                <td>
                    <%=numberFormat.format(pwmLocaleBundle.getKeys().size() * allStats.getMissingKeys().values().iterator().next().size())%>
                </td>
            </tr>
            <% } %>
            <tr>
                <td>
                    <b>Total</b>
                </td>
                <td>
                </td>
                <td>
                    <b><%=numberFormat.format(allStats.getTotalKeys())%></b>
                </td>
                <td>
                    <b><%=numberFormat.format(allStats.getTotalSlots())%></b>
                </td>
            </tr>
        </table>


        <h1><a id="end">Localizations (End User Only)</a></h1>
        <table>
            <tr>
                <td class="title">
                    Name
                </td>
                <td class="title">
                    Key
                </td>
                <td class="title">
                    Present Strings
                </td>
                <td class="title">
                    Missing Strings
                </td>
                <td class="title">
                    Percent Present
                </td>
            </tr>
            <% for (final Locale loopLocale: pwmRequest.getConfig().getKnownLocales()) { %>
            <% boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
            <tr<%=highLight ? " class=\"highlight\"" : ""%>>
                <td>
                    <%=loopLocale.getDisplayName()%>
                </td>
                <td>
                    <%=loopLocale.toString()%>
                </td>
                <td>
                    <%=userFacingStats.getPerLocale_presentLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=userFacingStats.getPerLocale_missingLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=userFacingStats.getPerLocale_percentLocalizations().get(loopLocale)%>
                </td>
            </tr>
            <% } %>
            <tr>
                <td>
                    <b>Total</b>
                </td>
                <td>
                </td>
                <td>
                    <b><%=numberFormat.format(userFacingStats.getPresentSlots())%></b>
                </td>
                <td>
                    <b><%=numberFormat.format(userFacingStats.getMissingSlots())%></b>
                </td>
                <td>
                    <b><%=userFacingStats.getTotalPercentage()%></b>
                </td>
            </tr>
        </table>

        <h1><a id="2">Localizations (Admin Facing Only)</a></h1>
        <table>
            <tr>
                <td class="title">
                    Name
                </td>
                <td class="title">
                    Key
                </td>
                <td class="title">
                    Present Strings
                </td>
                <td class="title">
                    Missing Strings
                </td>
                <td class="title">
                    Percent Present
                </td>
            </tr>
            <% for (final Locale loopLocale: pwmRequest.getConfig().getKnownLocales()) { %>
            <% boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
            <tr<%=highLight ? " class=\"highlight\"" : ""%>>
                <td>
                    <%=loopLocale.getDisplayName()%>
                </td>
                <td>
                    <%=loopLocale.toString()%>
                </td>
                <td>
                    <%=adminFacingStats.getPerLocale_presentLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=adminFacingStats.getPerLocale_missingLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=adminFacingStats.getPerLocale_percentLocalizations().get(loopLocale)%>
                </td>
            </tr>
            <% } %>
            <tr>
                <td>
                    <b>Total</b>
                </td>
                <td>
                </td>
                <td>
                    <b><%=numberFormat.format(adminFacingStats.getPresentSlots())%></b>
                </td>
                <td>
                    <b><%=numberFormat.format(adminFacingStats.getMissingSlots())%></b>
                </td>
                <td>
                    <b><%=adminFacingStats.getTotalPercentage()%></b>
                </td>
            </tr>
        </table>


        <h1><a id="localeInfo">Localizations (All)</a></h1>
        <table>
            <tr>
                <td class="title">
                    Name
                </td>
                <td class="title">
                    Key
                </td>
                <td class="title">
                    Present Strings
                </td>
                <td class="title">
                    Missing Strings
                </td>
                <td class="title">
                    Percent Present
                </td>
            </tr>
            <% for (final Locale loopLocale: pwmRequest.getConfig().getKnownLocales()) { %>
            <% boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
            <tr<%=highLight ? " class=\"highlight\"" : ""%>>
                <td>
                    <%=loopLocale.getDisplayName()%>
                </td>
                <td>
                    <%=loopLocale.toString()%>
                </td>
                <td>
                    <%=allStats.getPerLocale_presentLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=allStats.getPerLocale_missingLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=allStats.getPerLocale_percentLocalizations().get(loopLocale)%>
                </td>
            </tr>
            <% } %>
            <tr>
                <td>
                    <b>Total</b>
                </td>
                <td>
                </td>
                <td>
                    <b><%=numberFormat.format(allStats.getPresentSlots())%></b>
                </td>
                <td>
                    <b><%=numberFormat.format(allStats.getMissingSlots())%></b>
                </td>
                <td>
                    <b><%=allStats.getTotalPercentage()%></b>
                </td>
            </tr>
        </table>

        <h1><a id="dsada">Configuration Setting Descriptions</a></h1>
        <table>
            <tr>
                <td class="title">
                    Locale
                </td>
                <td class="title">
                    Key
                </td>
                <td class="title">
                    Present Strings
                </td>
                <td class="title">
                    Missing Strings
                </td>
                <td class="title">
                    Percent Present
                </td>
            </tr>
            <% for (final Locale loopLocale: pwmRequest.getConfig().getKnownLocales()) { %>
            <% boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
            <tr<%=highLight ? " class=\"highlight\"" : ""%>>
                <td>
                    <%= loopLocale.getDisplayName() %>
                </td>
                <td>
                    <%= loopLocale.toString() %>
                </td>
                <td>
                    <%= configLocaleStats.getDescription_presentLocalizations().get(loopLocale) %>
                </td>
                <td>
                    <%= configLocaleStats.getDescription_missingLocalizations().get(loopLocale) %>
                </td>
                <td>
                    <%= configLocaleStats.getDescription_percentLocalizations().get(loopLocale) %>
                </td>
            </tr>
            <% } %>
        </table>

        <h1><a id="dsa">Default Challenge Questions</a></h1>
        <table>
            <tr>
                <td class="title">
                    Locale
                </td>
                <td class="title">
                    Has Default Random Challenge Questions
                </td>
            </tr>
            <% for (final Locale loopLocale: pwmRequest.getConfig().getKnownLocales()) { %>
            <% boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
            <tr<%=highLight ? " class=\"highlight\"" : ""%>>
                <td>
                    <%= loopLocale.getDisplayName() %>
                </td>
                <td>
                    <%= LocaleHelper.booleanString(configLocaleStats.getDefaultChallenges().contains(loopLocale),  pwmRequest.getLocale(),pwmRequest.getConfig())%>
                </td>
            </tr>
            <% } %>
        </table>

        <%--
    <h1><a id="aa">Missing Keys</a></h1>
    <table>
        <tr>
            <td class="title">
                Bundle
            </td>
            <td class="title">
                Locale
            </td>
            <td class="title">
                Key
            </td>
        </tr>
        <% for (final PwmLocaleBundle pwmLocaleBundle : allStats.getMissingKeys().keySet()) { %>
        <% for (final Locale locale : allStats.getMissingKeys().get(pwmLocaleBundle).keySet()) { %>
        <% for (final String key : allStats.getMissingKeys().get(pwmLocaleBundle).get(locale)) { %>
        <tr>
            <td>
                <%=pwmLocaleBundle.getTheClass().getSimpleName()%>
            </td>
            <td>
                <%=locale%>
            </td>
            <td>
                <%=key%>
            </td>
        </tr>
        <% } %>
        <% } %>
        <% } %>
        </table>
        --%>
        <br/><br/><br/><br/>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
