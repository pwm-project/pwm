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


<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.PwmLocaleBundle" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.*" %>

<!DOCTYPE html>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final Locale userLocale = JspUtility.locale(request); %>
<% final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Display Strings Reference"/>
    </jsp:include>
    <div id="centerbody" style="width:95%">
        <%@ include file="reference-nav.jsp"%>
        <ol>
                <% for (final PwmLocaleBundle bundle : PwmLocaleBundle.values()) { %>
                <li><a href="#displayStrings_<%=bundle.getTheClass().getSimpleName()%>"><%=bundle.getTheClass().getSimpleName()%></a></li>
                <% } %>
        </ol>
        <br/>
        <% for (final PwmLocaleBundle bundle : PwmLocaleBundle.values()) { %>
        <h2>
            <a id="displayStrings_<%=bundle.getTheClass().getSimpleName()%>"><%=bundle.getTheClass().getSimpleName()%></a>
            <% if (bundle.isAdminOnly()) { %> (admin-only) <% } %>
        </h2>
        <table>
            <% final ResourceBundle resourceBundle = ResourceBundle.getBundle(bundle.getTheClass().getName()); %>
            <% for (final String key : new TreeSet<String>(Collections.list(resourceBundle.getKeys()))) { %>
            <% final Map<Locale,String> values = LocaleHelper.getUniqueLocalizations(pwmRequest != null ? pwmRequest.getConfig() : null, bundle.getTheClass(), key, PwmConstants.DEFAULT_LOCALE); %>
            <% for (final Map.Entry<Locale,String> entry : values.entrySet()) { %>
            <% final Locale locale = entry.getKey(); %>
            <% if (locale.equals(PwmConstants.DEFAULT_LOCALE)) { %>
            <tr>
                <td rowspan="<%=values.size()%>">
                    <%=key%>
                </td>
                <td>
                    <%= locale.toString()%> - <%=locale.getDisplayName(userLocale)%>
                </td>
                <td>
                    <%= StringUtil.escapeHtml(entry.getValue()) %>
                </td>
            </tr>
            <% } else { %>
            <tr>
                <td>
                    <%= locale.toString()%> - <%=locale.getDisplayName(userLocale)%>
                </td>
                <td>
                    <%= StringUtil.escapeHtml(entry.getValue()) %>
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
