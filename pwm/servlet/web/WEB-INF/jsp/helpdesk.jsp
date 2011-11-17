<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2011 The PWM Project
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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.PwmSession" %>
<%@ page import="password.pwm.UserHistory" %>
<%@ page import="password.pwm.bean.HelpdeskBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean(); %>
<% final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.FULL, SimpleDateFormat.FULL, pwmSession.getSessionStateBean().getLocale()); %>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en" lang="en">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();getObject('username').focus();" class="tundra">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody">
        <form action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded" name="search"
              onsubmit="handleFormSubmit('submitBtn');" onreset="handleFormClear();">
            <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
            <% //check to see if any locations are configured.
                if (!ContextManager.getPwmApplication(session).getConfig().getLoginContexts().isEmpty()) {
            %>
            <h2><label for="context"><pwm:Display key="Field_Location"/></label></h2>
            <select name="context">
                <pwm:DisplayLocationOptions name="context"/>
            </select>
            <% } %>

            <h2><label for="username"><pwm:Display key="Field_Username"/></label></h2>

            <input type="search" id="username" name="username" class="inputfield"
                   value="<pwm:ParamValue name='username'/>"/>
            <input type="submit" class="btn"
                   name="search"
                   value="<pwm:Display key="Button_Search"/>"
                   id="submitBtn"/>
            <input type="hidden"
                   name="processAction"
                   value="search"/>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
        </form>
        <br class="clear"/>
        <% if (helpdeskBean.isUserExists()) { %>
        <% final UserInfoBean searchedUserInfo = helpdeskBean.getUserInfoBean(); %>
        <table id="form">
            <tr>
                <td colspan="10" class="title">
                    <%= helpdeskBean.getUserInfoBean().getUserID() %>
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
            <%  Map<String, String> attrMap = ContextManager.getPwmApplication(session).getConfig().readSettingAsStringMap(PwmSetting.HELPDESK_DISPLAY_ATTRIBUTES);
                for (Map.Entry<String, String> me : attrMap.entrySet()) {
            %>
            <tr>
                <td class="key">
                    <%=me.getValue()%>
                </td>
                <td>
                    <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getAllUserAttributes().get(me.getKey())) %>
                </td>
            </tr>
            <%  } %>
            <tr>
                <td class="key">
                    Last Login Time
                </td>
                <td>
                    <%= helpdeskBean.getLastLoginTime() != null ? dateFormatter.format(helpdeskBean.getLastLoginTime()) : ""%>
                </td>
            </tr>
            <tr>
                <td colspan="10" class="title">
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
            <tr>
                <td colspan="10" class="title">
                </td>
            </tr>
            <tr>
                <td class="key">
                    LDAP Password Locked
                </td>
                <% if (helpdeskBean.isIntruderLocked()) { %>
                <td class="health-WARN">
                    true
                </td>
                <% } else { %>
                <td>
                    false
                </td>
                <% } %>
            </tr>
            <tr>
                <td class="key">
                    PWM Intruder Locked
                </td>
                <% if (helpdeskBean.isPwmIntruder()) { %>
                <td class="health-WARN">
                    true
                </td>
                <% } else { %>
                <td>
                    false
                </td>
                <% } %>
            </tr>
        </table>

        <br class="clear"/>
        <div style="margin-left: 20%; margin-right: 20%; text-align: center">
            <table>
                <% for (final UserHistory.Record record : helpdeskBean.getUserHistory().getRecords()) { %>
                <tr>
                    <td class="key" style="width: 200px">
                        <%= (DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, pwmSession.getSessionStateBean().getLocale())).format(new java.util.Date(record.getTimestamp())) %>
                    </td>
                    <td>
                        <%= record.getEventCode().getLocalizedString(ContextManager.getPwmApplication(session).getConfig(), pwmSession.getSessionStateBean().getLocale()) %>
                        <%= record.getMessage() != null ? record.getMessage() : "" %>
                    </td>
                </tr>
                <% } %>
            </table>
        </div>

        <div id="buttonbar">
            <button class="btn" onclick="changePasswordPopup()">Change Password</button>
            <% if (helpdeskBean.isIntruderLocked()) { %>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) { %>
            <button class="btn" onclick="document.ldapUnlockForm.submit()">Unlock</button>
            <% } else { %>
            <button class="btn" disabled="yes" onclick="alert('User is not locked');">Unlock</button>
            <% } %>
            <% } %>
        </div>
    </div>
    <% } else { %>
    <div>No such user holmes, try again</div>
    <% } %>

</div>

<form name="ldapUnlockForm" action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded">
    <input type="hidden" name="processAction" value="doUnlock"/>
    <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
</form>
<script type="text/javascript">
    function changePasswordPopup() {
        var bodyText = '<form action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded">';
        bodyText += '<input type="password" name="password1" id="password1" class="inputfield" style="width: 200px"/>';
        bodyText += '<input type="password" name="password2" id="password2" class="inputfield" style="width: 200px"/>';
        bodyText += '<input type="hidden" name="processAction" value="doReset"/>';
        bodyText += '<input type="submit" name="change" class="btn" id="password_button" value=" <pwm:Display key="Button_ChangePassword"/> "/>';
        bodyText += '<input type="hidden" name="pwmFormID" id="pwmFormID" value="<pwm:FormID/>"/>';
        bodyText += '</form>';
        showWaitDialog("Change Password",bodyText);
    }
</script>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
