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

<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.error.PwmOperationalException" %>
<%@ page import="password.pwm.i18n.Admin" %>
<%@ page import="password.pwm.svc.token.TokenPayload" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="password.pwm.util.java.JsonUtil" %>

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
        <% if (tokenKey != null && tokenKey.length() > 0) { %>
        <table>
            <tr>
                <td colspan="10" class="title">Token Information</td>
            </tr>
            <tr>
                <td class="key">
                    Key
                </td>
                <td>
                    <%=tokenKey%>
                </td>
            </tr>
            <%
                TokenPayload tokenPayload = null;
                boolean tokenExpired = false;
                String lookupError = null;
                try {
                    tokenPayload = tokenlookup_pwmRequest.getPwmApplication().getTokenService().retrieveTokenData(tokenlookup_pwmRequest.getSessionLabel(), tokenKey);
                } catch (PwmOperationalException e) {
                    tokenExpired= e.getError() == PwmError.ERROR_TOKEN_EXPIRED;
                    lookupError = e.getMessage();
                }
            %>
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
                    <%=JspUtility.freindlyWrite(pageContext, JsonUtil.serialize( tokenPayload.getDestination() ) )%>
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
            <% } else { %>
            <tr>
                <td class="key">
                    Status
                </td>
                <td>
                    <% if (tokenExpired) { %>Expired<% } else { %>Token Not Found<% } %>
                </td>
            </tr>
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
        <br/>
        <% } %>
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
