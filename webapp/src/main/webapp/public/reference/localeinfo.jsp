<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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

<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.PwmLocaleBundle" %>
<%@ page import="password.pwm.util.LocaleHelper" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>

<!DOCTYPE html>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final List<Locale> HIGHLIGHTED_LOCALES = Arrays.asList(new Locale[] {
            LocaleHelper.parseLocaleString("en"),
            LocaleHelper.parseLocaleString("ca"),
            LocaleHelper.parseLocaleString("da"),
            LocaleHelper.parseLocaleString("de"),
            LocaleHelper.parseLocaleString("es"),
            LocaleHelper.parseLocaleString("fr"),
            LocaleHelper.parseLocaleString("it"),
            LocaleHelper.parseLocaleString("ja"),
            LocaleHelper.parseLocaleString("nl"),
            LocaleHelper.parseLocaleString("pl"),
            LocaleHelper.parseLocaleString("pt_BR"),
            LocaleHelper.parseLocaleString("ru"),
            LocaleHelper.parseLocaleString("sv"),
            LocaleHelper.parseLocaleString("zh_CN"),
            LocaleHelper.parseLocaleString("zh_TW")
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
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final NumberFormat numberFormat = NumberFormat.getNumberInstance(); %>
<body class="nihilo">
<div id="wrapper">
    <style nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>" >
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
            <% final boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
            <tr<%=highLight ? " class=\"highlight\"" : ""%>>
                <td>
                    <%=loopLocale.getDisplayName()%>
                </td>
                <td>
                    <%=loopLocale.toString()%>
                </td>
                <td>
                    <%=userFacingStats.getPerLocalePresentLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=userFacingStats.getPerLocaleMissingLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=userFacingStats.getPerLocalePercentLocalizations().get(loopLocale)%>
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
            <% final boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
            <tr<%=highLight ? " class=\"highlight\"" : ""%>>
                <td>
                    <%=loopLocale.getDisplayName()%>
                </td>
                <td>
                    <%=loopLocale.toString()%>
                </td>
                <td>
                    <%=adminFacingStats.getPerLocalePresentLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=adminFacingStats.getPerLocaleMissingLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=adminFacingStats.getPerLocalePercentLocalizations().get(loopLocale)%>
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
            <% final boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
            <tr<%=highLight ? " class=\"highlight\"" : ""%>>
                <td>
                    <%=loopLocale.getDisplayName()%>
                </td>
                <td>
                    <%=loopLocale.toString()%>
                </td>
                <td>
                    <%=allStats.getPerLocalePresentLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=allStats.getPerLocaleMissingLocalizations().get(loopLocale)%>
                </td>
                <td>
                    <%=allStats.getPerLocalePercentLocalizations().get(loopLocale)%>
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
            <% final boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
            <tr<%=highLight ? " class=\"highlight\"" : ""%>>
                <td>
                    <%= loopLocale.getDisplayName() %>
                </td>
                <td>
                    <%= loopLocale.toString() %>
                </td>
                <td>
                    <%= configLocaleStats.getDescriptionPresentLocalizations().get(loopLocale) %>
                </td>
                <td>
                    <%= configLocaleStats.getDescriptionMissingLocalizations().get(loopLocale) %>
                </td>
                <td>
                    <%= configLocaleStats.getDescriptionPercentLocalizations().get(loopLocale) %>
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
            <% final boolean highLight =  (HIGHLIGHTED_LOCALES.contains(loopLocale)); %>
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

    <h1><a id="aa">End-User Highlighted Locale Missing Keys</a></h1>
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
        <% if (!pwmLocaleBundle.isAdminOnly()) { %>
        <% for (final Locale locale : allStats.getMissingKeys().get(pwmLocaleBundle).keySet()) { %>
        <% if (HIGHLIGHTED_LOCALES.contains(locale)) { %>
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
        <% } %>
        <% } %>
        </table>
        <br/><br/><br/><br/>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
