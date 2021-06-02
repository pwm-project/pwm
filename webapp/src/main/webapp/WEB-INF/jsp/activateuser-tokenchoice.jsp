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


<%@ page import="password.pwm.bean.TokenDestinationItem" %>
<%@ page import="java.util.List" %>
<%@ page import="password.pwm.http.servlet.activation.ActivateUserServlet" %>

<!DOCTYPE html>
<% List<TokenDestinationItem> tokenDestinationItems = (List)JspUtility.getAttribute(pageContext, PwmRequestAttribute.TokenDestItems ); %>
<% boolean singleItem = tokenDestinationItems.size() == 1; %>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_ActivateUser"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_ActivateUser" displayIfMissing="true"/></h1>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <p>
            <% if (singleItem) { %>
            <pwm:display key="Display_RecoverTokenSendOneChoice" value1="<%=tokenDestinationItems.iterator().next().getDisplay()%>"/>
            <% } else { %>
            <pwm:display key="Display_RecoverTokenSendChoices"/>
            <% } %>
        </p>
        <% if (!singleItem) { %>
        <table class="noborder">
            <% for (final TokenDestinationItem item : tokenDestinationItems) { %>
            <tr>
                <td style="text-align: center">
                    <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form">
                        <button class="btn" type="submit" name="submitBtn">
                            <% if (item.getType() == TokenDestinationItem.Type.email) { %>
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-file-text"></span></pwm:if>
                            <pwm:display key="Button_Email"/>
                            <% } else { %>
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-phone"></span></pwm:if>
                            <pwm:display key="Button_SMS"/>
                            <% } %>
                        </button>
                        <input type="hidden" name="choice" value="<%=item.getId()%>"/>
                        <input type="hidden" name="processAction" value="<%=ActivateUserServlet.ActivateUserAction.tokenChoice%>"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </td>
                <td>
                    <% if (item.getType() == TokenDestinationItem.Type.email) { %>
                    <pwm:display key="Display_RecoverTokenSendChoiceEmail"/>
                    <% } else { %>
                    <pwm:display key="Display_RecoverTokenSendChoiceSMS"/>
                    <% } %>
                </td>
            </tr>
            <pwm:if test="<%=PwmIfTest.showMaskedTokenSelection%>">
                <tr>
                    <td>
                    </td>
                    <td>
                        <%=item.getDisplay()%>
                    </td>
                </tr>
                <tr>
                    <td>
                        &nbsp;
                    </td>
                </tr>
            </pwm:if>
            <% } %>
        </table>
        <% } %>
        <div>
            <div class="buttonbar">
                <% if (singleItem) { %>
                <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form">
                    <button type="submit" id="button-continue" name="button-continue" class="btn">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-forward"></span></pwm:if>
                        <pwm:display key="Button_Continue"/>
                    </button>
                    <input type="hidden" name="choice" value="<%=tokenDestinationItems.iterator().next().getId()%>"/>
                    <input type="hidden" name="processAction" value="<%=ActivateUserServlet.ActivateUserAction.tokenChoice%>"/>
                    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                </form>
                <% } %>
                <pwm:if test="<%=PwmIfTest.showCancel%>">
                    <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" name="search" class="pwm-form" autocomplete="off">
                        <button type="submit" name="button" class="btn" id="button-sendReset">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                            <pwm:display key="Button_Cancel"/>
                        </button>
                        <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=ActivateUserServlet.ActivateUserAction.reset%>"/>
                        <input type="hidden" name="<%=PwmConstants.PARAM_RESET_TYPE%>" value="<%=ActivateUserServlet.ResetType.exitActivation%>"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </pwm:if>
            </div>
        </div>
        </form>
    </div>

    <div class="push"></div>
</div>
<%@ include file="fragment/footer.jsp" %>
</body>
</html>

