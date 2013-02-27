<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2012 The PWM Project
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

<%@ page import="com.novell.ldapchai.cr.ResponseSet" %>
<%@ page import="com.novell.ldapchai.exception.ChaiUnavailableException" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.config.PwmPasswordRule" %>
<%@ page import="password.pwm.util.operations.CrUtility" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final UserInfoBean uiBean = pwmSession.getUserInfoBean(); %>
<% final SessionStateBean ssBean = pwmSession.getSessionStateBean(); %>
<% final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.FULL, SimpleDateFormat.FULL, pwmSession.getSessionStateBean().getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper" class="nihilo">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_UserInformation"/>
</jsp:include>
<div id="centerbody">
    <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false">
        <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:Display key="Title_UserInformation"/>">
            <table>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_Username"/>
                    </td>
                    <td>
                        <%= StringEscapeUtils.escapeHtml(uiBean.getUserID()) %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_UserDN"/>
                    </td>
                    <td>
                        <%= StringEscapeUtils.escapeHtml(uiBean.getUserDN()) %>
                    </td>
                </tr>
            </table>
            <br class="clear"/>
            <table>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_PasswordExpired"/>
                    </td>
                    <td>
                        <%if (uiBean.getPasswordState().isExpired()) {%><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_PasswordPreExpired"/>
                    </td>
                    <td>
                        <%if (uiBean.getPasswordState().isPreExpired()) {%><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_PasswordWithinWarningPeriod"/>
                    </td>
                    <td>
                        <%if (uiBean.getPasswordState().isWarnPeriod()) { %><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_PasswordViolatesPolicy"/>
                    </td>
                    <td>
                        <% if (uiBean.getPasswordState().isViolatesPolicy()) {%><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_PasswordSetTime"/>
                    </td>
                    <td>
                        <%= uiBean.getPasswordLastModifiedTime() != null ? dateFormatter.format(uiBean.getPasswordLastModifiedTime()) : "n/a"%>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_PasswordExpirationTime"/>
                    </td>
                    <td>
                        <%= uiBean.getPasswordExpirationTime() != null ? dateFormatter.format(uiBean.getPasswordExpirationTime()) : "n/a"%>
                    </td>
                </tr>
            </table>
            <br class="clear"/>
            <table>
                <%
                    ResponseSet userResponses = null;
                    try {
                        userResponses = CrUtility.readUserResponseSet(pwmSession, ContextManager.getPwmApplication(session), pwmSession.getSessionManager().getActor());
                    } catch (ChaiUnavailableException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    } catch (PwmUnrecoverableException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                %>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_ResponsesStored"/>
                    </td>
                    <td>
                        <%if (!uiBean.isRequiresResponseConfig()) { %><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        <pwm:Display key="Field_ResponsesTimestamp"/>
                    </td>
                    <td>
                        <%= userResponses != null && userResponses.getTimestamp() != null ? dateFormatter.format(userResponses.getTimestamp()) : "n/a" %>
                    </td>
                </tr>
            </table>
        </div>
        <div data-dojo-type="dijit.layout.ContentPane" title="Session Information">
            <table>
                <tr>
                    <td class="key">
                        Locale
                    </td>
                    <td>
                        <%= ssBean.getLocale() %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        IP Address
                    </td>
                    <td>
                        <%= ssBean.getSrcAddress() %> [ <%= ssBean.getSrcHostname() %> ]
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        SessionID
                    </td>
                    <td>
                        <%= ssBean.getSessionID() %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        Session Verification Key
                    </td>
                    <td>
                        <%= ssBean.getSessionVerificationKey() %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        Auth method requires a new password immediately
                    </td>
                    <td>
                        <%= pwmSession.getUserInfoBean().isRequiresNewPassword() %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        Logout URL
                    </td>
                    <td>
                        <%= StringEscapeUtils.escapeHtml(Helper.figureLogoutURL(pwmApplicationHeader, pwmSessionHeader)) %>
                    </td>
                </tr>
                <tr>
                    <td class="key">
                        Forward URL
                    </td>
                    <td>
                        <%= StringEscapeUtils.escapeHtml(Helper.figureForwardURL(pwmApplicationHeader, pwmSessionHeader, request)) %>
                    </td>
                </tr>
            </table>
        </div>
        <div data-dojo-type="dijit.layout.ContentPane" title="Password Policy">
            <div style="height: 400px; overflow: auto;">

                <table>
                    <% for (final PwmPasswordRule rule : PwmPasswordRule.values()) { %>
                    <tr>
                        <td class="key">
                            <%= rule.name() %>
                        </td>
                        <td>
                            <%= uiBean.getPasswordPolicy().getValue(rule) != null ? StringEscapeUtils.escapeHtml(uiBean.getPasswordPolicy().getValue(rule)) : "" %>
                        </td>
                    </tr>
                    <% } %>
                </table>
            </div>
        </div>
    </div>
    <div id="buttonbar">
        <form action="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>" method="post"
              enctype="application/x-www-form-urlencoded">
            <input type="hidden"
                   name="processAction"
                   value="continue"/>
            <input type="submit" name="button" class="btn"
                   value="    <pwm:Display key="Button_Continue"/>    "
                   id="button_continue"/>
        </form>
    </div>
    <br class="clear"/>
</div>
</div>
<script type="text/javascript">
    require(["dojo/parser","dijit/layout/TabContainer","dijit/layout/ContentPane"],function(dojoParser){
        dojoParser.parse();
    });
</script>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>                   