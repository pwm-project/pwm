<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.i18n.LocaleHelper" %>
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
  --%>

<!DOCTYPE html>

<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext);
    String pageTitle = LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, pwmRequest);
%>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_HEADER_WARNINGS); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<script nonce="<pwm:value name="cspNonce"/>" type="text/javascript" src="<pwm:context/><pwm:url url="/public/resources/js/configmanager.js"/>"></script>
<div id="wrapper" class="login-wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=pageTitle%>"/>
    </jsp:include>
    <div id="centerbody">
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <form action="<pwm:url url='ConfigManager'/>" method="post" id="configLogin" name="configLogin" enctype="application/x-www-form-urlencoded"
              class="pwm-form">
            <h1>Configuration Password</h1>
            <br class="clear"/>
            <input type="<pwm:value name="passwordFieldType"/>" class="inputfield passwordfield" name="password" id="password" <pwm:autofocus/>/>
            <div class="buttonbar">
                <% if (!pwmRequest.getConfig().isDefaultValue(PwmSetting.PWM_SECURITY_KEY)) { %>
                <label class="checkboxWrapper">
                    <input type="checkbox" id="remember" name="remember"/>
                    Remember Password for 1 Hour
                </label>
                <br>
                <% } %>
                <button type="submit" class="btn" name="button" id="submitBtn">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-sign-in"></span></pwm:if>
                    <pwm:display key="Button_Login"/>
                </button>
                <%@ include file="/WEB-INF/jsp/fragment/button-cancel.jsp" %>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>" autofocus="autofocus"/>
            </div>
        </form>
    </div>
    <div class="push"></div>
</div>
<% JspUtility.setFlag(pageContext, PwmRequest.Flag.HIDE_LOCALE);%>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
