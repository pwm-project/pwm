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
<%@ page import="java.util.TreeMap" %>

<%
  final Map<String,String> outputData = new TreeMap<>();
  try {
    final PwmRequest pwmRequest = PwmRequest.forRequest(request,response);
    outputData.putAll((Map)pwmRequest.getAttribute(PwmRequestAttribute.ConfigurationSummaryOutput));
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
    <% for (final Map.Entry<String,String> record : outputData.entrySet()) { %>
    <p>
    <div><b><%=record.getKey()%></b></div>
    <div><code class="pre-whitespace"><%=StringUtil.escapeHtml(record.getValue())%></code></div>
    </p>
    <% } %>
  </div>
<div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
