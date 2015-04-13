<%@ page import="password.pwm.error.PwmError" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.error.PwmOperationalException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.token.TokenPayload" %>
<%@ page import="java.util.Iterator" %>
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
    PwmRequest tokenlookup_pwmRequest = null;
    try {
        tokenlookup_pwmRequest = PwmRequest.forRequest(request, response);
    } catch (PwmException e) {
        JspUtility.logError(pageContext, "error during page setup: " + e.getMessage());
    }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Token Lookup"/>
    </jsp:include>
    <div id="centerbody">
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
                    tokenPayload = tokenlookup_pwmRequest.getPwmApplication().getTokenService().retrieveTokenData(tokenKey);
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
                    System: <%= PwmConstants.DEFAULT_DATETIME_FORMAT.format(tokenPayload.getDate()) %>
                    <br/>
                    Local: <%= SimpleDateFormat.getDateTimeInstance().format(tokenPayload.getDate()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Destination(s)
                </td>
                <td>
                    <%for (Iterator destIter = tokenPayload.getDest().iterator(); destIter.hasNext();) { %>
                    <%=destIter.next()%>
                    <% if (destIter.hasNext()) { %>
                    <br/>
                    <% } %>
                    <% } %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Payload
                </td>
                <td>
                    <table>
                        <%for (String key : tokenPayload.getData().keySet()) { %>
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
        <form id="tokenForm" action="Administration" method="post">
            <textarea name="token" id="token" style="width: 580px; height: 150px"></textarea>
            <div class="buttonbar">
                <button type="submit" name="submitBtn" class="btn" type="submit">
                    <pwm:if test="showIcons"><span class="btn-icon fa fa-search"></span></pwm:if>
                    Lookup Token
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
