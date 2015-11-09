<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.PwmLocaleBundle" %>
<%@ page import="password.pwm.util.LocaleHelper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.ResourceBundle" %>
<%@ page import="java.util.TreeSet" %>
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
  ~
  --%>

<!DOCTYPE html>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_THEME); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_FOOTER_TEXT); %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final Locale userLocale = JspUtility.locale(request); %>
<% final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Display Strings Reference"/>
    </jsp:include>
    <div id="centerbody wide">
        <%@ include file="reference-nav.jsp"%>
        <ol>
                <% for (PwmLocaleBundle bundle : PwmLocaleBundle.values()) { %>
                <li><a href="#displayStrings_<%=bundle.getTheClass().getSimpleName()%>"><%=bundle.getTheClass().getSimpleName()%></a></li>
                <% } %>
        </ol>
        <br/>
        <% for (PwmLocaleBundle bundle : PwmLocaleBundle.values()) { %>
        <h2>
            <a id="displayStrings_<%=bundle.getTheClass().getSimpleName()%>"><%=bundle.getTheClass().getSimpleName()%></a>
            <% if (bundle.isAdminOnly()) { %> (admin-only) <% } %>
        </h2>
        <table>
            <% final ResourceBundle resourceBundle = ResourceBundle.getBundle(bundle.getTheClass().getName()); %>
            <% for (final String key : new TreeSet<String>(Collections.list(resourceBundle.getKeys()))) { %>
            <% final Map<Locale,String> values = LocaleHelper.getUniqueLocalizations(pwmRequest != null ? pwmRequest.getConfig() : null, bundle.getTheClass(), key, PwmConstants.DEFAULT_LOCALE); %>
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
                    <%= StringUtil.escapeHtml(values.get(locale)) %>
                </td>
            </tr>
            <% } else { %>
            <tr>
                <td>
                    <%= locale.toString()%> - <%=locale.getDisplayName(userLocale)%>
                </td>
                <td>
                    <%= StringUtil.escapeHtml(values.get(locale)) %>
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
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
