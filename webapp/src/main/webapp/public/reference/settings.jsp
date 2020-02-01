<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2019 The PWM Project
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


<%@ page import="password.pwm.config.PwmSettingCategory" %>
<%@ page import="password.pwm.config.PwmSettingFlag" %>
<%@ page import="password.pwm.config.PwmSettingSyntax" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.*" %>
<%@ page import="password.pwm.util.macro.MacroMachine" %>
<%@ page import="com.novell.ldapchai.util.StringHelper" %>
<%@ page import="password.pwm.AppProperty" %>

<!DOCTYPE html>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS); %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final Locale userLocale = JspUtility.locale(request); %>
<%
    final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext);
    final boolean advancedMode = false;
    final List<PwmSettingCategory> sortedCategories = PwmSettingCategory.valuesForReferenceDoc(userLocale);
    final MacroMachine macroMachine = MacroMachine.forNonUserSpecific(pwmRequest.getPwmApplication(), pwmRequest.getLabel());
%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Setting Reference"/>
    </jsp:include>
    <div id="centerbody" style="width: 800px">
        <%@ include file="reference-nav.jsp"%>
        <ol>
            <% for (final PwmSettingCategory category : sortedCategories) { %>
            <li><a href="#settings_category_<%=category.toString()%>"><%=category.toMenuLocationDebug(null,userLocale)%></a></li>
            <% } %>
        </ol>
        <br/>
        <% for (final PwmSettingCategory category : sortedCategories) { %>
        <h2><a id="settings_category_<%=category.toString()%>"><%=category.getLabel(userLocale)%></a></h2>
        <p>
            <%=category.getDescription(userLocale)%>
        </p>
        <% for (final PwmSetting setting : category.getSettings()) { %>
        <% if (!setting.isHidden()) { %>
        <a id="setting_key_<%=setting.getKey()%>"  style="font-weight: inherit; text-decoration: none">
            <table>
                <tr>
                    <td class="key" style="width: 100px">

                        <div style="text-align: left">
                            <a href="#setting_key_<%=setting.getKey()%>" style="text-decoration: inherit">
                                <span class="btn-icon pwm-icon pwm-icon-link"></span>
                            </a>
                        </div>
                        Label
                    </td>
                    <td>
                        <%=setting.getLabel(userLocale)%>
                    </td>
                </tr>
                <tr>
                    <td class="key" style="width: 100px">
                        Key
                    </td>
                    <td>
                        <%=setting.getKey()%>
                    </td>
                </tr>
                <tr>
                    <td class="key" style="width: 100px">
                        Navigation
                    </td>
                    <td>
                        <%=setting.toMenuLocationDebug(null,userLocale)%>
                    </td>
                </tr>
                <tr>
                    <td class="key" style="width: 100px">
                        Syntax
                    </td>
                    <td>
                        <%=setting.getSyntax()%>
                    </td>
                </tr>
                <tr>
                    <td class="key" style="width: 100px">
                        Level
                    </td>
                    <td>
                        <%=setting.getLevel()%>
                        <% if (setting.getLevel() == 0) { %>
                        (Normal)
                        <% } else if (setting.getLevel() == 1) { %>
                        (Advanced)
                        <% } %>
                    </td>
                </tr>
                <% if (setting.getFlags().contains(PwmSettingFlag.MacroSupport)) { %>
                <tr>
                    <td class="key" style="width: 100px">
                        Macro Support
                    </td>
                    <td>
                        <%= LocaleHelper.booleanString(setting.getFlags().contains(PwmSettingFlag.MacroSupport),pwmRequest) %>
                    </td>
                </tr>
                <% } %>
                <tr>
                    <td class="key" style="width: 100px">
                        Required
                    </td>
                    <td>
                        <%= LocaleHelper.booleanString(setting.isRequired(),pwmRequest) %>
                    </td>
                </tr>
                <tr>
                    <td class="key" style="width: 100px">
                        Confidential
                    </td>
                    <td>
                        <%= LocaleHelper.booleanString(setting.isConfidential(),pwmRequest) %>
                    </td>
                </tr>
                <% if (setting.getSyntax() == PwmSettingSyntax.OPTIONLIST || setting.getSyntax() == PwmSettingSyntax.SELECT) { %>
                <tr>
                    <td class="key" style="width: 100px">
                        Options
                    </td>
                    <td>
                        <table>
                            <thead>
                            <tr><td><b>Stored Value</b></td><td><b>Display</b></td></tr>
                            </thead>
                            <tbody>
                            <% for (final Map.Entry<String,String> entry : setting.getOptions().entrySet()) { %>
                            <tr>
                                <td>
                                    <%=entry.getKey()%>
                                </td>
                                <td>
                                    <%=entry.getValue()%>
                                </td>
                            </tr>
                            <% } %>
                            </tbody>
                        </table>
                    </td>
                </tr>
                <% } %>
                <% final Map<String,String> defaultValues = setting.getDefaultValueDebugStrings(userLocale); %>
                <tr>
                    <td class="key" style="width: 100px">
                        Default
                    </td>
                    <td style="max-width: 690px; overflow-x: auto">
                        <% if (defaultValues.size() > 1) { %>
                        <table>
                            <thead>
                            <tr><td><b>Template</b></td><td><b>Value</b></td></tr>
                            </thead>
                            <tbody>
                            <%
                                for (final Map.Entry<String,String> entry : defaultValues.entrySet()) {
                                    final String template = entry.getKey();
                                    final String defaultValue = StringUtil.escapeHtml(entry.getValue());
                            %>
                            <tr>
                                <td>
                                    <%=(template == null || template.isEmpty()) ? "default" : template %>
                                </td>
                                <td>
                                    <%=defaultValue == null ? "&nbsp;" : defaultValue%>
                                </td>
                            </tr>
                            <% } %>
                            </tbody>
                        </table>
                        <%
                        } else if (defaultValues.size() == 1) {
                            final String defaultValue = defaultValues.values().iterator().next();
                        %>
                        <pre><%=defaultValue == null ? "&nbsp;" : defaultValue%></pre>
                        <% } %>
                    </td>
                </tr>
                <tr>
                    <td colspan="2">
                        <%= macroMachine.expandMacros(setting.getDescription(userLocale)) %>
                    </td>
                </tr>
                <% } %>
            </table>
        </a>
        <br/>
        <% } %>
        <% } %>
        <% if (advancedMode) { %>
        <h2><a id="settingStatistics">Setting Statistics</a></h2>
        <% final Map<PwmSetting.SettingStat,Object> settingStats = PwmSetting.getStats(); %>
        <table>
            <tr>
                <td>
                    Total Settings
                </td>
                <td>
                    <%= settingStats.get(PwmSetting.SettingStat.Total) %>
                </td>
            </tr>
            <tr>
                <td>
                    Settings that are part of a Profile
                </td>
                <td>
                    <%= settingStats.get(PwmSetting.SettingStat.hasProfile) %>
                </td>
            </tr>
            <% final Map<PwmSettingSyntax,Integer> syntaxCounts = (Map<PwmSettingSyntax,Integer>)settingStats.get(PwmSetting.SettingStat.syntaxCounts); %>
            <% for (final Map.Entry<PwmSettingSyntax,Integer> entry : syntaxCounts.entrySet()) { %>
            <tr>
                <td>
                    Settings with syntax type <%= entry.getKey().toString() %>
                </td>
                <td>
                    <%= entry.getValue() %>
                </td>
            </tr>
            <% } %>
        </table>

        <h2><a id="appProperties">Application Properties</a></h2>
        <table>
            <tr>
                <td class="key" style="text-align: left">
                    Key
                </td>
                <td class="key" style="text-align: left">
                    Default Value
                </td>
            </tr>
            <% for (final AppProperty appProperty : AppProperty.values()) { %>
            <tr>
                <td style="width: auto; white-space: nowrap; text-align: left">
                    <%=appProperty.getKey()%>
                </td>
                <td>
                    <%=appProperty.getDefaultValue()%>
                </td>
            </tr>
            <% } %>
        </table>
        <% } %>
        <div class="push"></div>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
