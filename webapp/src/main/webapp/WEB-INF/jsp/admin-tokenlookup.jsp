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


<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.error.PwmOperationalException" %>
<%@ page import="password.pwm.i18n.Admin" %>
<%@ page import="password.pwm.svc.token.TokenPayload" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="password.pwm.util.java.JsonUtil" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.bean.TokenDestinationItem" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    PwmRequest tokenlookup_pwmRequest = null;
    try {
        tokenlookup_pwmRequest = PwmRequest.forRequest(request, response);
    } catch (PwmException e) {
        JspUtility.logError(pageContext, "error during page setup: " + e.getMessage());
    }
%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <% final String PageName = JspUtility.localizedString(pageContext,"Title_TokenLookup",Admin.class);%>
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=PageName%>"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_TokenLookup" bundle="Admin" displayIfMissing="true"/></h1>
        <%@ include file="fragment/admin-nav.jsp" %>
        <% final String tokenKey = tokenlookup_pwmRequest.readParameterAsString("token");%>
        <%
            TokenPayload tokenPayload = null;
            boolean tokenExpired = false;
            String lookupError = null;
        %>
        <%
            if ( !StringUtil.isEmpty( tokenKey ) )
            {
                try
                {
                    tokenPayload = tokenlookup_pwmRequest.getPwmApplication().getTokenService().retrieveTokenData(tokenlookup_pwmRequest.getLabel(), tokenKey);
                } catch ( PwmOperationalException e )
                {
                    tokenExpired = e.getError() == PwmError.ERROR_TOKEN_EXPIRED;
                    lookupError = e.getMessage();
                }
            }
        %>
        <% if ( !StringUtil.isEmpty( tokenKey ) ) { %>
        <table>
            <tr>
                <td colspan="10" class="title">Token Information</td>
            </tr>
            <% if (tokenPayload != null) { %>
            <tr>
                <td class="key">
                    Status
                </td>
                <td>
                    Valid
                </td>
            </tr>
            <tr>
                <td class="key">
                    Name
                </td>
                <td>
                    <%= tokenPayload.getName() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    UserDN
                </td>
                <td>
                    <%= tokenPayload.getUserIdentity() %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Issue Date
                </td>
                <td>
                    <span class="timestamp"><%= JavaHelper.toIsoDate(tokenPayload.getIssueTime()) %></span>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Expiration Date
                </td>
                <td>
                    <span class="timestamp"><%= JavaHelper.toIsoDate(tokenPayload.getExpiration()) %></span>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Destination(s)
                </td>
                <td>
                    <% TokenDestinationItem tokenDestinationItem = tokenPayload.getDestination(); %>
                    <% if ( tokenDestinationItem != null ) { %>
                    <table>
                        <tr>
                            <td>Type</td><td><%=tokenDestinationItem.getType()%></td>
                        </tr>
                        <tr>
                            <td>Destination</td><td><%=tokenDestinationItem.getDisplay()%></td>
                        </tr>
                    </table>
                    <% } %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Payload
                </td>
                <td>
                    <table>
                        <%for (final String key : tokenPayload.getData().keySet()) { %>
                        <tr>
                            <td>
                                <%=key%>
                            </td>
                            <td>
                                <%=tokenPayload.getData().get(key)%>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                </td>
            </tr>
            <% } %>
            <% if ( tokenPayload == null && lookupError == null ) { %>
            <tr>
                <td>Status</td><td>Not Found</td>
            </tr>
            <% } %>
            <%  if ( lookupError != null ) { %>
            <tr>
                <td class="key">
                    Lookup Error
                </td>
                <td>
                    <%=lookupError%>
                </td>
            </tr>
            <% } %>
        </table>
        <% } %>
        <br/>
        <form id="tokenForm" action="<pwm:current-url/>" method="post">
            <textarea name="token" id="token" style="width: 580px; height: 150px"></textarea>
            <div class="buttonbar">
                <button type="submit" name="submitBtn" class="btn" type="submit">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-search"></span></pwm:if>
                    <pwm:display key="Button_Search"/>
                </button>
            </div>
            <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
    </div>
    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
