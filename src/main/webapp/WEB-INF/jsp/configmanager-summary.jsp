<%@ page import="password.pwm.config.Configuration" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.i18n.PwmLocaleBundle" %>
<%@ page import="password.pwm.util.LocaleHelper" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="java.util.*" %>
<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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

<%
  final List<Map<String,String>> settingData = new ArrayList<Map<String,String>>();
  final Map<String,Object> outputData = new HashMap<String,Object>();
  try {
    final PwmRequest pwmRequest = PwmRequest.forRequest(request,response);
    outputData.putAll((Map)pwmRequest.getAttribute(PwmRequest.Attribute.ConfigurationSummaryOutput));

    settingData.addAll((List<Map<String,String>>)outputData.get("settings"));
  } catch (PwmException e) {
          /* noop */
  }
%>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_WARNINGS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_REQ_COUNTER); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_HEADER_BUTTONS); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_FOOTER_TEXT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.NO_IDLE_TIMEOUT); %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
  <div style="padding:10px">
    <br/>
    <div style="text-align: center; width: 100%">
      <h1>
      <pwm:display key="Title_ConfigurationSummary" bundle="Config"/>
      </h1>
      <div>
        <%=PwmConstants.PWM_APP_NAME%> &nbsp; <%=PwmConstants.SERVLET_VERSION%>
      </div>
      <div>
        Current Time: <span class="timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date())%></span>
        <br/>
        <br/>
        <br/>
        <span class="footnote">Only settings modified from their default value are shown.</span>
      </div>

      <br/>
      <% for (final Map<String,String> record : settingData) { %>
      <table style="width:800px">
        <col class="key" style="width:100px">
        <col style="max-width: 700px; overflow: auto">
        <tr>
          <td>
            Setting
          </td>
          <td>
            <b><%=record.get("label")%></b>
          </td>
        </tr>
        <% if (record.containsKey("profile")) { %>
        <tr>
          <td>
            Profile
          </td>
          <td>
            <div>
              <%=StringUtil.escapeHtml(record.get("profile"))%>
            </div>
          </td>
        </tr>
        <% } %>
        <% if (record.containsKey("modifyTime")) { %>
        <tr>
          <td>
            Modify Time
          </td>
          <td>
            <div>
              <span class="timestamp"><%=StringUtil.escapeHtml(record.get("modifyTime"))%></span>
            </div>
          </td>
        </tr>
        <% } %>
        <% if (record.containsKey("modifyUser")) { %>
        <tr>
          <td>
            Modified by
          </td>
          <td>
            <div>
              <%=StringUtil.escapeHtml(record.get("modifyUser"))%>
            </div>
          </td>
        </tr>
        <% } %>
        <tr>
          <td>
            Value
          </td>
          <td>
            <div>
              <pre style="white-space: pre-wrap"><%=StringUtil.escapeHtml(record.get("value"))%></pre>
            </div>
          </td>
        </tr>
      </table>
      <br/>
      <br/>
      <% } %>
      <% final Configuration pwmConfig = JspUtility.getPwmRequest(pageContext).getConfig(); %>
      <% Map<PwmLocaleBundle,Map<String,List<Locale>>> modifiedKeys = LocaleHelper.getModifiedKeysInConfig(pwmConfig); %>
      <% if (modifiedKeys != null && !modifiedKeys.isEmpty()) { %>
      <% for (final PwmLocaleBundle pwmLocaleBundle : modifiedKeys.keySet()) { %>
      <% for (final String key : modifiedKeys.get(pwmLocaleBundle).keySet()) { %>
      <table style="width: 800px">
        <tr>
          <td colspan="5"><%=pwmLocaleBundle.getTheClass().getSimpleName()%> - <%= key%></td>
        </tr>
        <% for (final Locale locale : modifiedKeys.get(pwmLocaleBundle).get(key)) { %>
        <tr>
          <td class="key"><%=LocaleHelper.debugLabel(locale)%></td>
          <td><%=LocaleHelper.getLocalizedMessage(locale,key,pwmConfig,pwmLocaleBundle.getTheClass())%></td>
        </tr>
        <% } %>
      </table>
      <br/>
      <% } %>
      <% } %>
      <% } %>
    </div>
  </div>
</div>
<div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
