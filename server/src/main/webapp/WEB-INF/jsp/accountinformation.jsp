<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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
<body class="nihilo">
<div id="wrapper" class="nihilo">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_UserInformation"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title" style="display: none;"><pwm:display key="Title_UserInformation" displayIfMissing="true"/></h1>
        <div class="tab-container" style="width: 100%; height: 100%;">
            <input name="tabs" type="radio" id="tab-1" checked="checked" class="input"/>
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
                                    <li><%=  StringUtil.escapeHtml(rule) %></li>
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
