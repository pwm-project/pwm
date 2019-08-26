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


<%@ page import="password.pwm.config.Configuration" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.PwmLocaleBundle" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>

<%
  final List<Map<String,String>> settingData = new ArrayList<Map<String,String>>();
  final Map<String,Object> outputData = new HashMap<String,Object>();
  try {
    final PwmRequest pwmRequest = PwmRequest.forRequest(request,response);
    outputData.putAll((Map)pwmRequest.getAttribute(PwmRequestAttribute.ConfigurationSummaryOutput));

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
        Current Time: <span class="timestamp"><%=JavaHelper.toIsoDate(new Date())%></span>
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
      <% final Map<PwmLocaleBundle,Map<String,List<Locale>>> modifiedKeys = LocaleHelper.getModifiedKeysInConfig(pwmConfig); %>
      <% if (modifiedKeys != null && !modifiedKeys.isEmpty()) { %>
      <% for (final Map.Entry<PwmLocaleBundle,Map<String,List<Locale>>> entry : modifiedKeys.entrySet()) { %>
      <% final PwmLocaleBundle pwmLocaleBundle = entry.getKey(); %>
      <% for (final Map.Entry<String,List<Locale>> innerEntry : entry.getValue().entrySet()) { %>
      <% final String key = innerEntry.getKey(); %>
      <table style="width: 800px">
        <tr>
          <td colspan="5"><%=pwmLocaleBundle.getTheClass().getSimpleName()%> - <%= key %></td>
        </tr>
        <% for (final Locale locale : innerEntry.getValue()) { %>
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
