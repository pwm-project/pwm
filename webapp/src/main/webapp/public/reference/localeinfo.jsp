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


<%@ page import="password.pwm.i18n.PwmLocaleBundle" %>
<%@ page import="password.pwm.util.i18n.ConfigLocaleStats" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.i18n.LocaleStats" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>

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
    final List<Locale> highlightedLocales = LocaleHelper.highLightedLocales();
    final PwmRequest pwmRequest = JspUtility.getPwmRequest( pageContext );
    final Map<PwmLocaleBundle, LocaleStats> localeStats = LocaleStats.getAllLocaleStats();
    final LocaleStats allStats = LocaleStats.getAllStats();
    final LocaleStats userFacingStats = LocaleStats.getUserFacingStats();
    final LocaleStats adminFacingStats = LocaleStats.getAdminFacingStats();
    final ConfigLocaleStats configLocaleStats = ConfigLocaleStats.getConfigLocaleStats();
%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final NumberFormat numberFormat = NumberFormat.getNumberInstance(); %>
<body>
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
            <% LocaleStats bundleStats = localeStats.get( pwmLocaleBundle ); %>
            <tr>
                <td>
                    <%=pwmLocaleBundle.getTheClass().getSimpleName()%>
                </td>
                <td>
                    <%=LocaleHelper.booleanString(!pwmLocaleBundle.isAdminOnly(), pwmRequest.getLocale(), pwmRequest.getConfig())%>
                </td>
                <td>
                    <%=numberFormat.format(bundleStats.getTotalKeys())%>
                </td>
                <td>
                    <%=numberFormat.format(bundleStats.getTotalSlots())%>
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
            <% final boolean highLight =  ( highlightedLocales.contains(loopLocale)); %>
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
            <% final boolean highLight =  ( highlightedLocales.contains(loopLocale)); %>
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
            <% final boolean highLight =  ( highlightedLocales.contains(loopLocale)); %>
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
            <% final boolean highLight =  ( highlightedLocales.contains(loopLocale)); %>
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
            <% final boolean highLight =  ( highlightedLocales.contains(loopLocale)); %>
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
        <% for (final PwmLocaleBundle pwmLocaleBundle : PwmLocaleBundle.allValues()) { %>
        <% if (!pwmLocaleBundle.isAdminOnly()) { %>
        <% for (final Locale locale : pwmRequest.getConfig().getKnownLocales()) { %>
        <% if ( highlightedLocales.contains(locale)) { %>
        <% for (final String key : LocaleStats.missingKeysForBundleAndLocale( pwmLocaleBundle, locale )) { %>
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
