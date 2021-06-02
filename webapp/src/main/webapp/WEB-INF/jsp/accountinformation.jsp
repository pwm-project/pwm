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
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.servlet.command.CommandServlet" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.http.servlet.accountinfo.AccountInformationBean" %>
<%@ page import="password.pwm.http.bean.DisplayElement" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final AccountInformationBean accountInformationBean = (AccountInformationBean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.AccountInfo); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body>
<div id="wrapper" class="nihilo">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UserInformation"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title" style="display: none;"><pwm:display key="Title_UserInformation" displayIfMissing="true"/></h1>
        <div class="tab-container" style="width: 100%; height: 100%;">
            <input name="tabs" type="radio" id="tab-1" checked="checked" class="input" <pwm:autofocus/> tabindex="5"/>
            <label for="tab-1" class="label"><pwm:display key="Title_UserInformation"/></label>
            <div class="tab-content-pane" id="UserInformation" title="<pwm:display key="Title_UserInformation"/>" class="tabContent">
                <table class="nomargin">
                    <% for (final DisplayElement displayElement : accountInformationBean.getAccountInfo()) { %>
                    <% request.setAttribute("displayElement", displayElement); %>
                    <jsp:include page="fragment/displayelement-row.jsp"/>
                    <% } %>
                </table>
            </div>
            <% if (!JavaHelper.isEmpty(accountInformationBean.getFormData())) { %>
            <input name="tabs" type="radio" id="tab-2" class="input"/>
            <label for="tab-2" class="label"><pwm:display key="Title_UserData"/></label>
            <div class="tab-content-pane" id="UserData" title="<pwm:display key="<%=Display.Title_UserData.toString()%>"/>" class="tabContent">
                <div style="max-height: 400px; overflow: auto;">
                    <table class="nomargin">
                        <% for (final DisplayElement displayElement : accountInformationBean.getFormData()) { %>
                        <% request.setAttribute("displayElement", displayElement); %>
                        <jsp:include page="fragment/displayelement-row.jsp"/>
                        <% } %>
                    </table>
                </div>
            </div>
            <% } %>
            <% if (!JavaHelper.isEmpty(accountInformationBean.getPasswordRules())) { %>
            <input name="tabs" type="radio" id="tab-3" class="input"/>
            <label for="tab-3" class="label"><pwm:display key="Title_PasswordPolicy"/></label>
            <div class="tab-content-pane" id="PasswordPolicy" title="<pwm:display key="Title_PasswordPolicy"/>" class="tabContent">
                <div style="max-height: 400px; overflow: auto;">
                    <table class="nomargin">
                        <tr>
                            <td class="key">
                                <pwm:display key="Title_PasswordPolicy"/>
                            </td>
                            <td id="PasswordRequirements">
                                <ul>
                                    <% for (final String rule : accountInformationBean.getPasswordRules()) { %>
                                    <li><%=  rule %></li>
                                    <% } %>
                                </ul>
                            </td>
                        </tr>
                    </table>
                </div>
            </div>
            <% } %>
            <% if (!JavaHelper.isEmpty(accountInformationBean.getAuditData())) {%>
            <input name="tabs" type="radio" id="tab-4" class="input"/>
            <label for="tab-4" class="label"><pwm:display key="Title_UserEventHistory"/></label>
            <div class="tab-content-pane" id="UserEventHistory" title="<pwm:display key="Title_UserEventHistory"/>" class="tabContent">
                <div style="max-height: 400px; overflow: auto;">
                    <table class="nomargin">
                        <% for (final AccountInformationBean.ActivityRecord record : accountInformationBean.getAuditData()) { %>
                        <tr>
                            <td class="key" style="width:50%">
                            <span class="timestamp">
                            <%= JavaHelper.toIsoDate(record.getTimestamp()) %>
                            </span>
                            </td>
                            <td>
                                <%= StringUtil.escapeHtml(record.getLabel()) %>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                </div>
            </div>
            <% } %>
            <div class="tab-end"></div>
        </div>
        <div class="buttonbar">
            <form action="<pwm:url url='<%=PwmServletDefinition.PublicCommand.servletUrl()%>' addContext="true"/>" method="post" enctype="application/x-www-form-urlencoded">
                <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=CommandServlet.CommandAction.next.toString()%>"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                <button type="submit" name="button" class="btn" id="button_continue">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                    <pwm:display key="Button_Continue"/>
                </button>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>                   
