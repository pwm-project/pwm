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

<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="password.pwm.ContextManager" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.bean.UserInformationServletBean" %>
<%@ page import="password.pwm.config.PwmPasswordRule" %>
<%@ page import="password.pwm.tag.PasswordRequirementsTag" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.List" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final UserInformationServletBean uisBean = pwmSession.getUserInformationServletBean(); %>
<% final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.FULL,SimpleDateFormat.FULL,request.getLocale()); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="../jsp/header.jsp" %>
<body onunload="unloadHandler();" onload="getObject('username').focus();">
<div id="wrapper">
<jsp:include page="../jsp/header-body.jsp"><jsp:param name="pwm.PageName" value="User Information"/></jsp:include>
<div id="centerbody">
<p style="text-align:center;">
    <a href="status.jsp">Status</a> | <a href="eventlog.jsp">Event Log</a> | <a href="intruderstatus.jsp">Intruder Status</a> | <a href="activesessions.jsp">Active Sessions</a> | <a href="config.jsp">Configuration</a> | <a href="threads.jsp">Threads</a> | <a href="UserInformation">User Information</a>
</p>
<form action="<pwm:url url='UserInformation'/>" method="post" enctype="application/x-www-form-urlencoded" name="search" autocomplete="off"
      onsubmit="handleFormSubmit('submitBtn');" onreset="handleFormClear();">
    <%  //check to see if there is an error
        if (PwmSession.getSessionStateBean(session).getSessionError() != null) {
    %>
            <span id="error_msg" class="msg-error">
                <pwm:ErrorMessage/>
            </span>
    <% } %>
    <p>Use this page to check user information.  The data on this page is gathered using the privileges of the logged in user.  If you do not have the appropriate directory privileges, this information may not be accurate.</p>

    <%  //check to see if any locations are configured.
        if (!ContextManager.getContextManager(this.getServletConfig().getServletContext()).getConfig().getLoginContexts().isEmpty()) {
    %>
    <h2><pwm:Display key="Field_Location"/></h2>
    <select name="context">
        <pwm:DisplayLocationOptions name="context"/>
    </select>
    <% } %>

    <h2><pwm:Display key="Field_Username"/></h2>
    <input tabindex="1" type="text" id="username" name="username" class="inputfield" value="<pwm:ParamValue name='username'/>"/>
    <div id="buttonbar">
        <input type="hidden"
               name="processAction"
               value="search"/>
        <input tabindex="3" type="submit" class="btn"
               name="search"
               value="     <pwm:Display key="Button_Search"/>     "
               id="submitBtn"/>
        <input tabindex="4" type="reset" class="btn"
               name="reset" onclick="getObject('username').value = '';getObject('username').focus();"
               value="     <pwm:Display key="Button_Reset"/>     "/>
    </div>
</form>
<br class="clear"/>
<% if (uisBean.isUserExists()) { %>
<% final UserInfoBean searchedUserInfo = uisBean.getUserInfoBean(); %>
<table>
    <tr>
        <td colspan="10" class="title">
            User Information
        </td>
    </tr>
    <tr>
        <td class="key">
            UserID
        </td>
        <td>
            <%= searchedUserInfo.getUserID() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            UserDN
        </td>
        <td>
            <%= searchedUserInfo.getUserDN() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Given Name
        </td>
        <td>
            <%= searchedUserInfo.getAllUserAttributes().getProperty("givenName") %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Surname
        </td>
        <td>
            <%= searchedUserInfo.getAllUserAttributes().getProperty("sn") %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Last Login Time
        </td>
        <td>
            <%= uisBean.getLastLoginTime() != null ? dateFormatter.format(uisBean.getLastLoginTime()) : "n/a"%>
        </td>
    </tr>

    <tr>
        <td class="key">
            Intruder Locked
        </td>
        <td>
            <%= uisBean.isIntruderLocked() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            PWM Intruder Locked
        </td>
        <td>
            <% if (uisBean.isPwmIntruder()) { %>
            <a href="intruderstatus.jsp">
                <%= uisBean.isPwmIntruder() %>
            </a>
            <% } else { %>
            <%= uisBean.isPwmIntruder() %>
            <% } %>
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
        <td class="key">
            Expired
        </td>
        <td>
            <%= searchedUserInfo.getPasswordState().isExpired() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Pre-Expired
        </td>
        <td>
            <%= searchedUserInfo.getPasswordState().isPreExpired() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Violates Policy
        </td>
        <td>
            <%= searchedUserInfo.getPasswordState().isViolatesPolicy() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Within Warning Period
        </td>
        <td>
            <%= searchedUserInfo.getPasswordState().isWarnPeriod() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Retrievable by PWM
        </td>
        <td>
            <%= uisBean.isPasswordRetrievable() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Last Modified Time (PWM)
        </td>
        <td>
            <%= searchedUserInfo.getPasswordLastModifiedTime() != null ? dateFormatter.format(searchedUserInfo.getPasswordLastModifiedTime()) : "n/a"%>
        </td>
    </tr>
    <tr>
        <td class="key">
            Expiration Time
        </td>
        <td>
            <%= searchedUserInfo.getPasswordExpirationTime() != null ? dateFormatter.format(searchedUserInfo.getPasswordExpirationTime()) : "n/a"%>
        </td>
    </tr>
</table>
<br class="clear"/>
<table>
    <tr>
        <td colspan="10" class="title">
            Forgotten Password
            <%
            %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Responses Configured
        </td>
        <td>
            <%= uisBean.getResponseSet() != null %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Responses Are Valid
        </td>
        <td>
            <%= !uisBean.getUserInfoBean().isRequiresResponseConfig() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Response Timestamp
        </td>
        <td>
            <%= uisBean.getResponseSet() != null && uisBean.getResponseSet().getTimestamp() != null ? dateFormatter.format(uisBean.getResponseSet().getTimestamp()) : "n/a" %>
        </td>
    </tr>
    <tr>
        <td class="key">
            ChallengeSet
        </td>
        <td>
            Parsed For Locale: <%=searchedUserInfo.getChallengeSet().getLocale()%><br/>
            Identifier: <%=searchedUserInfo.getChallengeSet().getIdentifier()%><br/>
            Minimum Random: <%=searchedUserInfo.getChallengeSet().getMinRandomRequired()%><br/>
            <br/>
            <% for (final Challenge loopChallange : searchedUserInfo.getChallengeSet().getChallenges()) { %>
            <p><%= loopChallange.toString() %></p>
            <% } %>
        </td>
    </tr>
</table>
<br class="clear"/>
<table>
    <tr>
        <td colspan="10" class="title">
            Effective Password Policy
        </td>
    </tr>
    <tr>
        <td class="key">
            Policy DN
        </td>
        <td>
            <%= uisBean.getPasswordPolicyDN() == null ? "n/a" : uisBean.getPasswordPolicyDN() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Display
        </td>
        <td>
            <ul>
                <% final List<String> requirementLines = PasswordRequirementsTag.getPasswordRequirementsStrings(searchedUserInfo.getPasswordPolicy(),pwmSession.getContextManager(),request.getLocale()); %>
                <% for (final String requirementLine : requirementLines) { %>
                <li><%=requirementLine%></li>
                <% } %>
            </ul>
        </td>
    </tr>
    <% for (final PwmPasswordRule rule : PwmPasswordRule.values()) { %>
    <tr>
        <td class="key">
            <%= rule.name() %>
        </td>
        <td>
            <%= searchedUserInfo.getPasswordPolicy().getValue(rule) != null ? searchedUserInfo.getPasswordPolicy().getValue(rule) : "" %>
        </td>
    </tr>
    <% } %>
</table>
<% } %>
</div>
<jsp:include page="../jsp/footer.jsp"/>
</body>
</html>