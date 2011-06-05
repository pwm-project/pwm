<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2010 The PWM Project
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
<%@ page import="password.pwm.PwmConstants" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.bean.UserInformationServletBean" %>
<%@ page import="password.pwm.config.Configuration" %>
<%@ page import="password.pwm.config.PwmPasswordRule" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.tag.PasswordRequirementsTag" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.List" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final UserInformationServletBean uisBean = pwmSession.getUserInformationServletBean(); %>
<% final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.FULL, SimpleDateFormat.FULL, request.getLocale()); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="/WEB-INF/jsp/header.jsp" %>
<body onload="pwmPageLoadHandler();getObject('username').focus();" class="tundra">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/header-body.jsp">
    <jsp:param name="pwm.PageName" value="User Information"/>
</jsp:include>
<div id="centerbody">
<%@ include file="admin-nav.jsp" %>
<form action="<pwm:url url='UserInformation'/>" method="post" enctype="application/x-www-form-urlencoded" name="search"
      onsubmit="handleFormSubmit('submitBtn');" onreset="handleFormClear();">
    <% //check to see if there is an error
        if (PwmSession.getSessionStateBean(session).getSessionError() != null) {
    %>
            <span id="error_msg" class="msg-error">
                <pwm:ErrorMessage/>
            </span>
    <% } %>
    <p>Use this page to check user information. The data on this page is gathered using the privileges of the logged in
        user. If you do not have the appropriate directory privileges, this information may not be accurate.</p>

    <% //check to see if any locations are configured.
        if (!PwmSession.getPwmSession(session).getConfig().getLoginContexts().isEmpty()) {
    %>
    <h2><label for="context"><pwm:Display key="Field_Location"/></label></h2>
    <select name="context">
        <pwm:DisplayLocationOptions name="context"/>
    </select>
    <% } %>

    <h2><label for="username"><pwm:Display key="Field_Username"/></label></h2>
    <input tabindex="1" type="search" id="username" name="username" class="inputfield"
           value="<pwm:ParamValue name='username'/>"/>

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
        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
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
            <%= searchedUserInfo.getUserID() == null ? "" : searchedUserInfo.getUserID() %>
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
            GUID
        </td>
        <td>
            <%= searchedUserInfo.getUserGuid() == null ? "" : searchedUserInfo.getUserGuid() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Given Name
        </td>
        <td>
            <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getAllUserAttributes().get("givenName")) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Surname
        </td>
        <td>
            <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getAllUserAttributes().get("sn")) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            <%= pwmSession.getConfig().readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE)%>
        </td>
        <td>
            <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getAllUserAttributes().get(pwmSession.getConfig().readSettingAsString(PwmSetting.EMAIL_USER_MAIL_ATTRIBUTE))) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Last Login Time
        </td>
        <td>
            <%= uisBean.getLastLoginTime() != null ? dateFormatter.format(uisBean.getLastLoginTime()) : ""%>
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
            <%= searchedUserInfo.getPasswordLastModifiedTime() != null ? dateFormatter.format(searchedUserInfo.getPasswordLastModifiedTime()) : ""%>
        </td>
    </tr>
    <tr>
        <td class="key">
            Expiration Time
        </td>
        <td>
            <%= searchedUserInfo.getPasswordExpirationTime() != null ? dateFormatter.format(searchedUserInfo.getPasswordExpirationTime()) : ""%>
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
            <% if (searchedUserInfo.getChallengeSet() != null) { %>
            Parsed For Locale: <%=searchedUserInfo.getChallengeSet().getLocale()%><br/>
            Identifier: <%=searchedUserInfo.getChallengeSet().getIdentifier()%><br/>
            Minimum Random: <%=searchedUserInfo.getChallengeSet().getMinRandomRequired()%><br/>
            <br/>
            <% for (final Challenge loopChallange : searchedUserInfo.getChallengeSet().getChallenges()) { %>
            <p><%= StringEscapeUtils.escapeHtml(loopChallange.toString()) %>
            </p>
            <% } %>
            <% } else { %>
            none
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
                <%
                    final List<String> requirementLines = PasswordRequirementsTag.getPasswordRequirementsStrings(searchedUserInfo.getPasswordPolicy(), pwmSession.getContextManager().getConfig(), request.getLocale()); %>
                <% for (final String requirementLine : requirementLines) { %>
                <li><%=requirementLine%>
                </li>
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
<jsp:include page="/WEB-INF/jsp/footer.jsp"/>
</body>
</html>