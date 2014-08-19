<%@ page import="password.pwm.config.PwmSettingSyntax" %>
<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.event.AuditEvent" %>
<%@ page import="password.pwm.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.util.TreeSet" %>
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
<%
    final Locale userLocale = pwmSessionHeader.getSessionStateBean().getLocale();
%>
<body class="nihilo">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="AutoDoc"/>
</jsp:include>
<div id="centerbody" class="wide">
<%@ include file="admin-nav.jsp" %>
<h1>Configuration Settings</h1>
<% for (final PwmSetting.Category category : PwmSetting.Category.values()) { %>
<h2><%=category.getLabel(userLocale)%></h2>
<p>
    <%=category.getDescription(userLocale)%>
</p>
<% for (int level = 0; level <= 1; level++) { %>
<% for (final PwmSetting setting : PwmSetting.getSettings(category,level)) { %>
<% if (!setting.isHidden()) { %>
<table>
    <tr>
        <td class="key">
            Label
        </td>
        <td>
            <%=setting.getLabel(userLocale)%>
        </td>
    </tr>
    <tr>
        <td class="key">
            Key
        </td>
        <td>
            <%=setting.getKey()%>
        </td>
    </tr>
    <tr>
        <td class="key">
            Syntax
        </td>
        <td>
            <%=setting.getSyntax()%>
        </td>
    </tr>
    <tr>
        <td class="key">
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
        <td class="key">
            Required
        </td>
        <td>
            <%=setting.isRequired()%>
        </td>
    </tr>
    <tr>
        <td class="key">
            Confidential
        </td>
        <td>
            <%=setting.isConfidential()%>
        </td>
    </tr>
    <% if (setting.getSyntax() == PwmSettingSyntax.OPTIONLIST || setting.getSyntax() == PwmSettingSyntax.SELECT) { %>
    <tr>
        <td class="key">
            Options
        </td>
        <td>
            <table>
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
            </table>
        </td>
    </tr>
    <% } %>
    <tr>
        <td colspan="2">
            <%=setting.getDescription(userLocale)%>
        </td>
    </tr>
</table>
<br/>
<% } %>
<% } %>
<% } %>
<% } %>
<h1>Event Statistics</h1>
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
<h1>Audit Events</h1>
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
            <%= auditEvent.isStoreOnUser()%>
        </td>
        <td>
            <%= auditEvent.getMessage().getLocalizedMessage(userLocale, pwmApplicationHeader.getConfig()) %>
        </td>
    </tr>
    <% } %>
</table>
<h1>Errors</h1>
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
            <%=error.getLocalizedMessage(userLocale, pwmApplicationHeader.getConfig())%>
        </td>
    </tr>
    <% } %>
</table>
<h1>Display Strings</h1>
<% for (PwmConstants.EDITABLE_LOCALE_BUNDLES bundle : PwmConstants.EDITABLE_LOCALE_BUNDLES.values()) { %>
<h2>
    <%=bundle.getTheClass().getSimpleName()%>
</h2>
<% if (bundle.isAdminOnly()) { %> (Admin Only, not seen by end user) <% } %>
<table>
    <% final ResourceBundle resourceBundle = ResourceBundle.getBundle(bundle.getTheClass().getName()); %>
    <% for (final String key : new TreeSet<String>(Collections.list(resourceBundle.getKeys()))) { %>
    <% final Map<Locale,String> values = LocaleHelper.getUniqueLocalizations(pwmApplicationHeader, bundle.getTheClass(), key, PwmConstants.DEFAULT_LOCALE); %>
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
        });
    </script>
</pwm:script>
<% request.setAttribute(PwmConstants.REQUEST_ATTR_SHOW_LOCALE,"false"); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
