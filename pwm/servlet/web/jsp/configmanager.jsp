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

<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.Constants" %>
<%@ page import="java.util.*" %>
<%@ page import="java.util.Set" %>
<%@ page import="java.util.Arrays" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final Map<String,Map<String,String>> configMap = (Map<String,Map<String,String>>)request.getAttribute(password.pwm.Constants.REQUEST_CONFIG_MAP); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="header-body.jsp"><jsp:param name="pwm.PageName" value="PWM Configuration Settings"/></jsp:include>
    <form action="<pwm:url url='ConfigManager'/>" method="post" name="configManager" enctype="application/x-www-form-urlencoded"
          onsubmit="" onreset="handleFormClear();">
        <div id="centerbody">
            PWM Configurations are controlled by the configuration file <i>pwm-configuration.xml</i>.  This
            page can be used to edit the contents of that file.  You can input an existing <i>pwm-configratuion.xml</i>
            or create a new one from scratch.  Once you have completed the configuration, Generate a configuration file
            and save it in PWM's <i>WEB-INF</i> subdirectory.
            <br class="clear"/>
        </div>
        <br class="clear"/>
        <table style="border: 0;">
            <tr style="border: 0">
                <td style="border: 0">
                    <ol>
                        <% for (final PwmSetting.Category loopCategory : PwmSetting.valuesByCategory().keySet()) { %>
                        <li><a href="#<%=loopCategory%>"><%=loopCategory.getLabel(request.getLocale())%></a></li>
                        <% } %>
                    </ol>
                </td>
                <td style="border: 0">
                    <table style="border: 0">
                        <tr style="border: 0">
                            <td style="border: 0; text-align: center;">
                                <input tabindex="3" type="file" class="btn"
                                       name="fileInput"
                                       id="fileInput"/>
                            </td>
                        </tr>
                        <tr style="border: 0">
                            <td style="border: 0; text-align: center;">
                                <input tabindex="3" type="submit" class="btn"
                                       name="generate"
                                       value="   Input Configuration File  "
                                       id="uploadFile"/>
                            </td>
                        </tr>
                        <tr style="border: 0">
                            <td style="border: 0; text-align: center;">
                                &nbsp;
                                <br/>
                                &nbsp;
                            </td>
                        </tr>
                        <tr style="border: 0">
                            <td style="border: 0; text-align: center;">
                                <input tabindex="3" type="submit" class="btn"
                                       name="generate"
                                       value="   Generate Configuration File  "
                                       id="generateBtn"/>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
        <br class="clear"/>
        <br class="clear"/>
        <%
            for (final PwmSetting.Category loopCategory : PwmSetting.valuesByCategory().keySet()) {
                final List<PwmSetting> loopSettings = PwmSetting.valuesByCategory().get(loopCategory);
        %>
        <table>
            <tr>
                <td class="title" colspan="10">
                    <a name="<%=loopCategory%>"><%= loopCategory.getLabel(request.getLocale()) %></a>
                </td>
            </tr>
            <tr>
                <td colspan="10">
                    <%= loopCategory.getDescription(request.getLocale()) %>
                </td>
            </tr>
            <%  for (final PwmSetting loopSetting : loopSettings) { %>
            <tr>
                <td class="key" style="width:100px; text-align:center;">
                    <%= loopSetting.getLabel(request.getLocale()) %>
                </td>
                <td>
                    <p><%= loopSetting.getDescription(request.getLocale()) %></p>
                    <% if (loopSetting.isLocalizable()) { %>
                    <b>Default</b> <input name="setting_<%=loopSetting.getKey()%>" size="60"
                                   value="<%= configMap.get(loopSetting.getKey()).get("") %>"/>
                    <%
                        Map<String,String> localizedValues = configMap.get(loopSetting.getKey());
                        localizedValues.remove("");
                        for (String localeKey : localizedValues.keySet()) {
                    %>
                    <%= localeKey %> <input name="setting_<%=loopSetting.getKey()%>_<%=localeKey%>" size="60"
                                            value="<%= configMap.get(localizedValues.get(localeKey)) %>"/>

                    <% } %>
                    <select name="addLocale_<%=loopSetting.getKey()%>">
                        <% for (Locale loopLocale : Locale.getAvailableLocales()) { %>
                        <option value="<%=loopLocale.toString() %>"><%=loopLocale.getDisplayName()%> </option>
                        <% } %>
                    </select>
                    <input tabindex="3" type="submit" class="btn"
                           name="generate"
                           value=" Add "
                           id="add_locale"/>

                    <% } else { %>
                    <% if (loopSetting.getSyntax() == PwmSetting.Syntax.BOOLEAN) { %>
                    <select name="setting_<%=loopSetting.getKey()%>">
                        <option value="true" <%="true".equalsIgnoreCase(configMap.get(loopSetting.getKey()).get("")) ? "selected=\"true\"" :""%>>True</option>
                        <option value="false" <%="false".equalsIgnoreCase(configMap.get(loopSetting.getKey()).get("")) ? "selected=\"true\"" :""%>>False</option>
                    </select>
                    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.PASSWORD) { %>
                    <input name="setting_<%=loopSetting.getKey()%>" size="60" type="password"
                           value="<%= configMap.get(loopSetting.getKey()).get("") %>"/>
                    <% } else if (loopSetting.getSyntax() == PwmSetting.Syntax.NUMERIC) { %>
                    <input name="setting_<%=loopSetting.getKey()%>" size="6" 
                           value="<%= configMap.get(loopSetting.getKey()).get("") %>"/>
                    <% } else { %>
                    <input name="setting_<%=loopSetting.getKey()%>" size="60"
                           value="<%= configMap.get(loopSetting.getKey()).get("") %>"/>
                    <% } %>
                    <% } %>
                </td>
            </tr>
            <% } %>
        </table>
        <br class="clear"/>
        <% } %>
        <br class="clear"/>
    </form>
</div>
<br class="clear"/>
<%@ include file="footer.jsp" %>
</body>
</html>

