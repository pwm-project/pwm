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


<%@ page import="password.pwm.config.value.data.FormConfiguration" %>
<%@ page import="password.pwm.http.servlet.updateprofile.UpdateProfileServlet" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.util.Map" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<% final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext);%>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UpdateProfileConfirm"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_UpdateProfileConfirm" displayIfMissing="true"/></h1>
        <p><pwm:display key="Display_UpdateProfileConfirm"/></p>
        <%@ include file="fragment/message.jsp" %>
        <br/>
        <% final Map<FormConfiguration,String> formDataMap = (Map<FormConfiguration,String>)JspUtility.getAttribute(pageContext, PwmRequestAttribute.FormData); %>
        <table id="ConfirmProfileTable">
            <% for (final Map.Entry<FormConfiguration, String> entry : formDataMap.entrySet()) { %>
            <% final FormConfiguration formConfiguration = entry.getKey(); %>
            <tr>
                <td class="key">
                    <%=formConfiguration.getLabel(JspUtility.locale(request))%>
                </td>
                <td>
                    <%
                        final String value = entry.getValue();
                        if (formConfiguration.getType() == FormConfiguration.Type.checkbox) {
                    %>
                    <label class="checkboxWrapper">
                        <input id="<%=formConfiguration.getName()%>" name="<%=formConfiguration.getName()%>" disabled type="checkbox" <%=(Boolean.parseBoolean(value))?"checked":""%>/>
                    </label>
                    <% } else if (formConfiguration.getType() == FormConfiguration.Type.photo) { %>
                    <% if (StringUtil.isEmpty( value) ) { %>
                    <div class="formfield-photo-missing">
                    </div>
                    <% } else { %>
                    <img class="formfield-photo" src="<pwm:current-url/>?processAction=readPhoto&field=<%=formConfiguration.getName()%>"/>
                    <% } %>
                    <% } else { %>
                    <%=StringUtil.escapeHtml(formConfiguration.displayValue(value, JspUtility.locale(request), JspUtility.getPwmRequest(pageContext).getConfig()))%>
                    <% } %>
                </td>
            </tr>
            <% } %>

        </table>
        <div class="buttonbar">
            <form style="display: inline" action="<pwm:current-url/>" method="post" name="confirm" enctype="application/x-www-form-urlencoded" class="pwm-form">
                <button id="confirmBtn" type="submit" class="btn" name="button">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                    <pwm:display key="Button_Confirm"/>
                </button>
                <input type="hidden" name="processAction" value="confirm"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form style="display: inline" action="<pwm:current-url/>" method="post" name="unconfirm" enctype="application/x-www-form-urlencoded"
                  class="pwm-form">
                <button id="gobackBtn" type="submit" class="btn" name="button">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                    <pwm:display key="Button_GoBack"/>
                </button>
                <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=UpdateProfileServlet.UpdateProfileAction.reset%>"/>
                <input type="hidden" name="<%=PwmConstants.PARAM_RESET_TYPE%>" value="<%=UpdateProfileServlet.ResetAction.unConfirm%>"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <pwm:if test="<%=PwmIfTest.showCancel%>">
                <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form" autocomplete="off">
                    <button type="submit" name="button" class="btn" id="button-sendReset">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                        <pwm:display key="Button_Cancel"/>
                    </button>
                    <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=UpdateProfileServlet.UpdateProfileAction.reset%>"/>
                    <input type="hidden" name="<%=PwmConstants.PARAM_RESET_TYPE%>" value="<%=UpdateProfileServlet.ResetAction.exitProfileUpdate%>"/>
                    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                </form>
            </pwm:if>
        </div>
    </div>
    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>
