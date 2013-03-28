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

<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(session); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="About"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="admin-nav.jsp" %>
        <table>
            <tr>
                <td class="key">
                    <%=PwmConstants.PWM_APP_NAME%> Version
                </td>
                <td>
                    <%= PwmConstants.SERVLET_VERSION %>
                </td>
            </tr>
            <% if (pwmApplication.getConfig().readSettingAsBoolean(PwmSetting.VERSION_CHECK_ENABLE)) { %>
            <tr>
                <td class="key">
                    Current Published Version
                </td>
                <td>
                    <%
                        String publishedVersion = "n/a";
                        if (pwmApplication != null && pwmApplication.getVersionChecker() != null) {
                            publishedVersion = pwmApplication.getVersionChecker().currentVersion();
                        }
                    %>
                    <%= publishedVersion %>
                </td>
            </tr>
            <% } %>
            <tr>
                <td class="key">
                    Current Time
                </td>
                <td>
                    <%= dateFormat.format(new java.util.Date()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Start Time
                </td>
                <td>
                    <%= dateFormat.format(pwmApplication.getStartupTime()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Up Time
                </td>
                <td>
                    <%= TimeDuration.fromCurrent(pwmApplication.getStartupTime()).asLongString() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Install Time
                </td>
                <td>
                    <%= dateFormat.format(pwmApplication.getInstallTime()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Server Timezone
                </td>
                <td>
                    <%= dateFormat.getTimeZone().getDisplayName() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Instance ID
                </td>
                <td>
                    <%= pwmApplication.getInstanceID() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Last LDAP Unavailable Time
                </td>
                <td>
                    <%= pwmApplication.getLastLdapFailure() != null ? dateFormat.format(pwmApplication.getLastLdapFailure().getDate()) : "n/a" %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    LDAP Vendor
                </td>
                <td>
                    <%
                        String vendor = "[detection error]";
                        try {
                            vendor = pwmApplication.getProxyChaiProvider().getDirectoryVendor().toString();
                        } catch (Exception e) { /* nothing */ }
                    %>
                    <%= vendor %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Chai API Version
                </td>
                <td>
                    <%= com.novell.ldapchai.ChaiConstant.CHAI_API_VERSION %> (<%= com.novell.ldapchai.ChaiConstant.CHAI_API_BUILD_INFO %>)
                </td>
            </tr>
            <tr>
                <td class="key">
                    Dojo API Version
                </td>
                <td>
                    <span id="dojoVersionSpan"></span>
                    <script type="text/javascript">
                        PWM_GLOBAL['startupFunctions'].push(function(){
                            require(["dojo"],function(dojo){
                                dojo.byId('dojoVersionSpan').innerHTML = dojo.version;
                            });
                        });
                    </script>
                </td>
            </tr>
            <tr>
                <td class="key">
                    License Information
                </td>
                <td>
                    <a href="<%=request.getContextPath()%><pwm:url url="/public/license.jsp"/>">License Information</a>
                </td>
            </tr>
        </table>
    </div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


