<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
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

<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.config.PwmPasswordRule" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="com.novell.ldapchai.exception.ChaiUnavailableException" %>
<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="com.novell.ldapchai.cr.ResponseSet" %>
<%@ page import="password.pwm.PasswordUtility" %>
<%@ page import="java.text.SimpleDateFormat" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final UserInfoBean uiBean = pwmSession.getUserInfoBean(); %>
<% final SessionStateBean ssBean = pwmSession.getSessionStateBean(); %>
<% final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.FULL,SimpleDateFormat.FULL,request.getLocale()); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();">
<div id="wrapper">
    <jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="Title_UserInformation"/></jsp:include>
    <div id="centerbody">
        <table>
            <tr>
                <td colspan="10" class="title">
                    User Information
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    UserID
                </td>
                <td colspan="2">
                    <%= uiBean.getUserID() %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    UserDN
                </td>
                <td colspan="2">
                    <%= uiBean.getUserDN() %>
                </td>
            </tr>
        </table>
        <br class="clear"/>
        <table>
            <tr>
                <td colspan="10" class="title">
                    Session Information
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Locale
                </td>
                <td colspan="2">
                    <%= ssBean.getLocale() %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Source Address
                </td>
                <td colspan="2">
                    <%= ssBean.getSrcAddress() %> [ <%= ssBean.getSrcHostname() %> ]
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    SessionID
                </td>
                <td colspan="2">
                    <%= ssBean.getSessionID() %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Session Verification Key
                </td>
                <td colspan="2">
                    <%= ssBean.getSessionVerificationKey() %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Logout URL
                </td>
                <td colspan="2">
                    <%= ssBean.getLogoutURL() == null ? pwmSession.getConfig().readSettingAsString(PwmSetting.URL_LOGOUT) : ssBean.getLogoutURL() %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Forward URL
                </td>
                <td colspan="2">
                    <%= ssBean.getForwardURL() == null ? pwmSession.getConfig().readSettingAsString(PwmSetting.URL_FORWARD) : ssBean.getForwardURL() %>
                </td>
            </tr>
        </table>
        <br class="clear"/>
        <table>
            <tr>
                <td colspan="10" class="title">
                    Password Status
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Expired
                </td>
                <td>
                    <%= uiBean.getPasswordState().isExpired() %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Pre-Expired
                </td>
                <td>
                    <%= uiBean.getPasswordState().isPreExpired() %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Violates Policy
                </td>
                <td>
                    <%= uiBean.getPasswordState().isViolatesPolicy() %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Within Warning Period
                </td>
                <td>
                    <%= uiBean.getPasswordState().isWarnPeriod() %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Expiration Time
                </td>
                <td>
                    <%= uiBean.getPasswordExpirationTime() != null ? dateFormatter.format(uiBean.getPasswordExpirationTime()) : "n/a"%>
                </td>
                <td class="key" colspan="2">
                    &nbsp;
                </td>
            </tr>
        </table>
        <br class="clear"/>
        <table>
            <tr>
                <td colspan="10" class="title">
                    Forgotten Password Status
                    <%
                        boolean responsesConfigured = false;
                        ResponseSet userResponses = null;
                        PasswordUtility.readUserResponseSet(pwmSession,pwmSession.getSessionManager().getActor());
                        try {
                            responsesConfigured = !password.pwm.UserStatusHelper.checkIfResponseConfigNeeded(pwmSession);
                            userResponses = PasswordUtility.readUserResponseSet(pwmSession,pwmSession.getSessionManager().getActor());
                        } catch (ChaiUnavailableException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        } catch (PwmException e) {
                            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                        }
                    %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Responses Configured
                </td>
                <td>
                    <%= responsesConfigured %>
                </td>
            </tr>
            <tr>
                <td class="key" colspan="2">
                    Response Timestamp
                </td>
                <td>
                    <%= userResponses != null && userResponses.getTimestamp() != null ? dateFormatter.format(userResponses.getTimestamp()) : "n/a" %>
                </td>
            </tr>
        </table>
        <br class="clear"/>
        <table>
            <tr>
                <td colspan="10" class="title">
                    Password Policy
                </td>
            </tr>
            <% for (final PwmPasswordRule rule : PwmPasswordRule.values()) { %>
            <tr>
                <td class="key">
                    <%= rule.name() %>
                </td>
                <td>
                    <%= uiBean.getPasswordPolicy().getValue(rule) != null ? uiBean.getPasswordPolicy().getValue(rule) : "" %>
                </td>
            </tr>
            <% } %>
        </table>
    </div>
    <jsp:include page="../jsp/footer.jsp"/>
</body>
</html>