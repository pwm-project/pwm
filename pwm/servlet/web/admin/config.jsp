<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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

<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.lang.reflect.Method" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Locale" %>
<%@ page import="password.pwm.PwmSession" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(session); %>
<% final password.pwm.config.Configuration pwmConfig = pwmSession.getConfig(); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();">
<div id="wrapper">
    <div id="header">
        <p class="logotext">PWM Configuration Settings<br/>
            <span class="logotext2"><pwm:Display key="APPLICATION-TITLE"/></span>
        </p>
    </div>
    <div id="centerbody">
        <br class="clear"/>
    </div>
    <table>
        <tr>
            <td class="title" colspan="10">
                PWM Configuration Settings
            </td>
        </tr>
        <tr>
            <td class="key">
                Configuration Load Time
            </td>
            <td colspan="3">
                <%= (java.text.DateFormat.getDateTimeInstance()).format(new Date(ContextManager.getContextManager(session).getConfigReader().getLoadTime())) %>
            </td>
        </tr>

        <%
            for (final PwmSetting s : PwmSetting.values()) {
                if (!s.isConfidential()) {
        %>
        <tr><td class="key">
            <%= s.getKey() %>
        </td><td colspan="3">
            <%= pwmConfig.toString(s) %>
        </td></tr>
        <%
                }
            }
        %>
        <tr><td class="key">
            Global Password Policy
        </td><td colspan="3">
            <%= pwmConfig.getGlobalPasswordPolicy() %>
        </td></tr>

    </table>
    <br class="clear"/>
    <p>
        Below are settings for each of the local-specific configuration options in eachlocale
        that has either been used to access PWM. If a specific configured locale does not list
        here, access PWM with a browser configured for that locale.
    </p>
    <% for (final Locale loopLocale : ContextManager.getContextManager(session).getConfigReader().getCurrentlyLoadedLocales()) { %>
    <br class="clear"/>
    <table class="tablemain">
        <tr>
            <td class="title" colspan="10">
                Locale-Specific Configuration Settings for <b><%= loopLocale %>
            </b>
            </td>
        </tr>
        <%
            final password.pwm.config.LocalizedConfiguration localeConfig = ContextManager.getContextManager(session).getConfigReader().getLocalizedConfiguration(loopLocale);
            for (final Method method : localeConfig.getClass().getMethods()) {
                final String name = method.getName();
                if (name.matches("(?i)^get.*|^is.*") && !name.matches("(?i).*password.*|^getClass$")) {
                    out.append("<tr><td class=\"key\">");
                    out.append(name.replaceAll("^is|^get", ""));
                    out.append("    </td><td colspan=\"3\">");
                    try {
                        out.append(method.invoke(localeConfig).toString());
                    } catch (Exception e) { /*blah*/ }
                    out.append("</td></tr>");
                }
            }
        %>
    </table>
    <% } %>
</div>
<br class="clear"/>
<%@ include file="../jsp/footer.jsp" %>
</body>
</html>












