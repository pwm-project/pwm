<%@ page import="password.pwm.config.PwmSettingCategory" %>
<%@ page import="password.pwm.config.PwmSettingSyntax" %>
<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.event.AuditEvent" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="java.util.*" %>
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
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    PwmRequest pwmRequest = null;
    final Map<String,PwmSettingCategory> sortedCategories = new TreeMap<String,PwmSettingCategory>();
    try {
        pwmRequest = PwmRequest.forRequest(request, response);
        for (final PwmSettingCategory category : PwmSettingCategory.values()) {
            sortedCategories.put(category.getLabel(pwmRequest.getLocale()),category);
        }
    } catch (PwmException e) {
        JspUtility.logError(pageContext, "error during page setup: " + e.getMessage());
    }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<% final Locale userLocale = JspUtility.locale(request); %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Reference"/>
    </jsp:include>
    <div id="centerbody" style="width: 800px">
        <ol>
            <li><a href="#urls">URLs</a></li>
            <li><a href="#settings">Settings</a></li>
            <ol>
                <% for (final PwmSettingCategory category : sortedCategories.values()) { %>
                <% if (!category.isHidden() && !category.getSettings().isEmpty()) { %>
                <li><a href="#settings_category_<%=category.toString()%>"><%=category.getLabel(userLocale)%></a></li>
                <% } %>
                <% } %>
            </ol>
            <li><a href="#eventStatistics">Event Statistics</a></li>
            <li><a href="#auditEvents">Audit Events</a></li>
            <li><a href="#errors">Errors</a></li>
            <li><a href="#localizations">Localizations</a></li>
            <li><a href="#displayStrings">Display Strings</a></li>
            <ol>
                <% for (PwmConstants.EDITABLE_LOCALE_BUNDLES bundle : PwmConstants.EDITABLE_LOCALE_BUNDLES.values()) { %>
                <li><a href="#displayStrings_<%=bundle.getTheClass().getSimpleName()%>"><%=bundle.getTheClass().getSimpleName()%></a></li>
                <% } %>
            </ol>
        </ol>
        <br/>
        <h1><a id="urls">URLs</a></h1>
        <table>
            <tr>
                <td>Application</td>
                <td><a href="<%=request.getContextPath()%>"><%=request.getContextPath()%></a></td>
            </tr>
            <tr>
                <td>Public Menu</td>
                <td><a href="<%=request.getContextPath()%>/public"><%=request.getContextPath()%>/private/public</a></td>
            </tr>
            <tr>
                <td>Forgotten Password</td>
                <td><a href="<%=request.getContextPath()%>/public/ForgottenPassword"><%=request.getContextPath()%>/public/ForgottenPassword</a></td>
            </tr>
            <tr>
                <td>Activate User</td>
                <td><a href="<%=request.getContextPath()%>/public/ActivateUser"><%=request.getContextPath()%>/public/ActivateUser</a></td>
            </tr>
            <tr>
                <td>New User Registration</td>
                <td><a href="<%=request.getContextPath()%>/public/NewUser"><%=request.getContextPath()%>/public/NewUser</a></td>
            </tr>
            <tr>
                <td>Logged-In Menu</td>
                <td><a href="<%=request.getContextPath()%>/private"><%=request.getContextPath()%>/private</a></td>
            </tr>
            <tr>
                <td>Change Password</td>
                <td><a href="<%=request.getContextPath()%>/private/ChangePassword"><%=request.getContextPath()%>/private/ChangePassword</a></td>
            </tr>
            <tr>
                <td>Setup Responses</td>
                <td><a href="<%=request.getContextPath()%>/private/SetupResponses"><%=request.getContextPath()%>/private/SetupResponses</a></td>
            </tr>
            <tr>
                <td>Helpdesk</td>
                <td><a href="<%=request.getContextPath()%>/private/Helpdesk"><%=request.getContextPath()%>/private/Helpdesk</a></td>
            </tr>
            <tr>
                <td>People Search</td>
                <td><a href="<%=request.getContextPath()%>/private/PeopleSearch"><%=request.getContextPath()%>/private/PeopleSearch</a></td>
            </tr>
        </table>
        <h1><a id="settings">Configuration Settings</a></h1>
        <% for (final PwmSettingCategory category : sortedCategories.values()) { %>
        <% if (!category.isHidden() && !category.getSettings().isEmpty()) { %>
        <h2><a id="settings_category_<%=category.toString()%>"><%=category.getLabel(userLocale)%></a></h2>
        <p>
            <%=category.getDescription(userLocale)%>
        </p>
        <% for (final PwmSetting setting : category.getSettings()) { %>
        <% if (!setting.isHidden()) { %>
        <a id="setting_key_<%=setting.getKey()%>"  style="font-weight: inherit">
            <table>
                <tr>
                    <td class="key" style="width: 100px">

                        <div style="text-align: left">
                            <a href="#setting_key_<%=setting.getKey()%>" style="text-decoration: inherit">
                                <span class="btn-icon fa fa-link"></span>
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
                <tr>
                    <td class="key" style="width: 100px">
                        Required
                    </td>
                    <td>
                        <%=setting.isRequired()%>
                    </td>
                </tr>
                <tr>
                    <td class="key" style="width: 100px">
                        Confidential
                    </td>
                    <td>
                        <%=setting.isConfidential()%>
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
                            <% for (final String key : setting.getOptions().keySet()) { %>
                            <tr>
                                <td>
                                    <%=key%>
                                </td>
                                <td>
                                    <%=setting.getOptions().get(key)%>
                                </td>
                            </tr>
                            <% } %>
                            </tbody>
                        </table>
                    </td>
                </tr>
                <% } %>
                <% final Map<PwmSetting.Template,String> defaultValues = setting.getDefaultValueDebugStrings(true,userLocale); %>
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
                            <% for (final PwmSetting.Template template : defaultValues.keySet()) { %>
                            <tr><td><%=template.toString()%></td><td><pre><%=StringUtil.escapeHtml(defaultValues.get(template))%></pre></td></tr>
                            <% } %>
                            </tbody>
                        </table>
                        <% } else if (defaultValues.size() == 1) { %>
                        <pre><%=StringUtil.escapeHtml(defaultValues.values().iterator().next())%></pre>
                        <% } %>
                    </td>
                </tr>
                <% } %>
                <tr>
                    <td colspan="2">
                        <%=setting.getDescription(userLocale)%>
                    </td>
                </tr>
            </table>
        </a>
        <br/>
        <% } %>
        <% } %>
        <% } %>
        <h1><a id="eventStatistics">Event Statistics</a></h1>
        <table>
            <tr>
                <td>
                    <h3>Label</h3>
                </td>
                <td>
                    <h3>Key</h3>
                </td>
                <td>
                    <h3>Description</h3>
                </td>
            </tr>
            <% for (final Statistic statistic : Statistic.sortedValues(userLocale)) { %>
            <tr>
                <td>
                    <%=statistic.getLabel(userLocale)%>
                </td>
                <td>
                    <%=statistic.getKey()%>
                </td>
                <td>
                    <%=statistic.getDescription(userLocale)%>
                </td>
            </tr>
            <% } %>
        </table>
        <h1><a id="auditEvents">Audit Events</a></h1>
        <table>
            <tr>
                <td>
                    <h3>Key</h3>
                </td>
                <td>
                    <h3>Type</h3>
                </td>
                <td>
                    <h3>Stored In User History</h3>
                </td>
                <td>
                    <h3>Resource Key</h3>
                </td>
                <td>
                    <h3>Label</h3>
                </td>
            </tr>
            <% for (AuditEvent auditEvent : AuditEvent.values()) { %>
            <tr>
                <td>
                    <%= auditEvent.toString() %>
                </td>
                <td>
                    <%= auditEvent.getType() %>
                </td>
                <td>
                    <%= auditEvent.isStoreOnUser() %>
                </td>
                <td>
                    <%= auditEvent.getMessage().getResourceKey() %>
                </td>
                <td>
                    <%= auditEvent.getMessage().getLocalizedMessage(userLocale, null) %>
                </td>
            </tr>
            <% } %>
        </table>
        <h1><a id="errors">Errors</a></h1>
        <table class="tablemain">
            <tr>
                <td>
                    <h3>Error Number</h3>
                </td>
                <td>
                    <h3>Key</h3>
                </td>
                <td>
                    <h3>Resource Key</h3>
                </td>
                <td>
                    <h3>Message</h3>
                </td>
            </tr>
            <% for (PwmError error : PwmError.values()) { %>
            <tr>
                <td>
                    <%=error.getErrorCode()%>
                </td>
                <td >
                    <%=error.toString()%>
                </td>
                <td>
                    <%=error.getResourceKey()%>
                </td>
                <td>
                    <%=error.getLocalizedMessage(userLocale, null)%>
                </td>
            </tr>
            <% } %>
        </table>
        <h1><a id="displayStrings">Display Strings</a></h1>
        <% for (PwmConstants.EDITABLE_LOCALE_BUNDLES bundle : PwmConstants.EDITABLE_LOCALE_BUNDLES.values()) { %>
        <h2>
            <a id="displayStrings_<%=bundle.getTheClass().getSimpleName()%>"><%=bundle.getTheClass().getSimpleName()%></a>
            <% if (bundle.isAdminOnly()) { %> (admin-only) <% } %>
        </h2>
        <table>
            <% final ResourceBundle resourceBundle = ResourceBundle.getBundle(bundle.getTheClass().getName()); %>
            <% for (final String key : new TreeSet<String>(Collections.list(resourceBundle.getKeys()))) { %>
            <% final Map<Locale,String> values = LocaleHelper.getUniqueLocalizations(pwmRequest.getPwmApplication(), bundle.getTheClass(), key, PwmConstants.DEFAULT_LOCALE); %>
            <% for (final Locale locale : values.keySet()) { %>
            <% if (locale.equals(PwmConstants.DEFAULT_LOCALE)) { %>
            <tr>
                <td rowspan="<%=values.size()%>">
                    <%=key%>
                </td>
                <td>
                    <%= locale.toString()%> - <%=locale.getDisplayName(userLocale)%>
                </td>
                <td>
                    <%= values.get(locale) %>
                </td>
            </tr>
            <% } else { %>
            <tr>
                <td>
                    <%= locale.toString()%> - <%=locale.getDisplayName(userLocale)%>
                </td>
                <td>
                    <%= values.get(locale) %>
                </td>
            </tr>
            <% } %>
            <% } %>
            <% } %>
        </table>
        <br/>
        <% } %>
        <div class="push"></div>
    </div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_GLOBAL['idle_suspendTimeout'] = true;
        });
    </script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_FOOTER_TEXT); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
