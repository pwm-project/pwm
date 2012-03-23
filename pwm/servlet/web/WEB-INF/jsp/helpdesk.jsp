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

<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.UserHistory" %>
<%@ page import="password.pwm.bean.HelpdeskBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.config.Message" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.servlet.HelpdeskServlet" %>
<%@ page import="password.pwm.tag.PasswordRequirementsTag" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(request); %>
<% final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean(); %>
<% final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.FULL, SimpleDateFormat.FULL, pwmSession.getSessionStateBean().getLocale()); %>
<% final HelpdeskServlet.SETTING_PW_UI_MODE SETTING_PW_UI_MODE = HelpdeskServlet.SETTING_PW_UI_MODE.valueOf(pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_SET_PASSWORD_MODE)); %>
<% final HelpdeskServlet.SETTING_CLEAR_RESPONSES SETTING_CLEAR_RESPONSES = HelpdeskServlet.SETTING_CLEAR_RESPONSES.valueOf(pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_CLEAR_RESPONSES)); %>
<% final Map<String, String> attrMap = ContextManager.getPwmApplication(session).getConfig().readSettingAsStringMap(PwmSetting.HELPDESK_DISPLAY_ATTRIBUTES); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();getObject('username').focus();" class="tundra">
<script type="text/javascript"
        src="<%=request.getContextPath()%>/resources/<pwm:url url='changepassword.js'/>"></script>
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
</jsp:include>
<div id="centerbody">
<p><pwm:Display key="Display_Helpdesk"/></p>
<form action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded" name="search"
      onsubmit="handleFormSubmit('submitBtn');" onreset="handleFormClear();" id="searchForm">
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
<div style="width: 100%; height: 400px">
<div class="message message-info">
<div style="text-align: center; width: 100%"><%= StringEscapeUtils.escapeHtml(searchedUserInfo.getUserID()) %></div>
<div dojoType="dijit.layout.TabContainer" style="width: 100%; height: 100%;" doLayout="false">
<div dojoType="dijit.layout.ContentPane" title="Data">
    <table>
        <tr>
            <td class="key">
                User DN
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getUserDN()) %>
            </td>
        </tr>
        <tr>
            <td class="key">
                User GUID
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getUserGuid()) %>
            </td>
        </tr>
        <% for (Map.Entry<String, String> me : attrMap.entrySet()) { %>
        <tr>
            <td class="key">
                <%=me.getValue()%>
            </td>
            <td>
                <% final String loopValue = searchedUserInfo.getAllUserAttributes().get(me.getKey()); %>
                <%= loopValue == null ? "" : StringEscapeUtils.escapeHtml(loopValue) %>
            </td>
        </tr>
        <%  } %>
    </table>
</div>
<div dojoType="dijit.layout.ContentPane" title="Status">
<table>
    <tr>
        <td class="key">
            Username
        </td>
        <td>
            <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getUserID()) %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Account Enabled
        </td>
        <td>
            <%= helpdeskBean.isAccountEnabled() ? "true" : "false" %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Last Login Time
        </td>
        <td>
            <%= helpdeskBean.getLastLoginTime() != null ? dateFormatter.format(helpdeskBean.getLastLoginTime()) : ""%>
        </td>
    </tr>
    <% if (helpdeskBean.getLastLoginTime() != null) { %>
    <tr>
        <td class="key">
            Last Login Time Delta
        </td>
        <td>
            <%= TimeDuration.fromCurrent(helpdeskBean.getLastLoginTime()).asLongString() + " ago"%>
        </td>
    </tr>
    <% } %>
    <tr>
        <td class="key">
            Password Expired
        </td>
        <td>
            <%= searchedUserInfo.getPasswordState().isExpired() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Password Pre-Expired
        </td>
        <td>
            <%= searchedUserInfo.getPasswordState().isPreExpired() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Violates Password Policy
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
            Password Set Time (PWM)
        </td>
        <td>
            <%= searchedUserInfo.getPasswordLastModifiedTime() != null ? dateFormatter.format(searchedUserInfo.getPasswordLastModifiedTime()) : "n/a"%>
        </td>
    </tr>
    <tr>
        <td class="key">
            Password Expiration Time
        </td>
        <td>
            <%= searchedUserInfo.getPasswordExpirationTime() != null ? dateFormatter.format(searchedUserInfo.getPasswordExpirationTime()) : "n/a"%>
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
    <tr>
        <td class="key">
            Responses Stored
        </td>
        <td>
            <%= helpdeskBean.getResponseSet() != null %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Response Updates are Needed
        </td>
        <td>
            <%= helpdeskBean.getUserInfoBean().isRequiresResponseConfig() %>
        </td>
    </tr>
    <tr>
        <td class="key">
            Stored Response Timestamp
        </td>
        <td>
            <%= helpdeskBean.getResponseSet() != null && helpdeskBean.getResponseSet().getTimestamp() != null ? dateFormatter.format(helpdeskBean.getResponseSet().getTimestamp()) : "n/a" %>
        </td>
    </tr>
</table>
<div id="buttonbar">
    <% if (SETTING_PW_UI_MODE != HelpdeskServlet.SETTING_PW_UI_MODE.none) { %>
    <button class="btn" onclick="initiateChangePasswordDialog()">Change Password</button>
    <% } %>
    <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) { %>
    <% if (helpdeskBean.isIntruderLocked()) { %>
    <button class="btn" onclick="document.ldapUnlockForm.submit()">Unlock</button>
    <% } else { %>
    <button class="btn" disabled="disabled" onclick="alert('User is not locked');">Unlock</button>
    <% } %>
    <% } %>
    <form name="ldapUnlockForm" action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded">
        <input type="hidden" name="processAction" value="doUnlock"/>
        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
    </form>
    <script type="text/javascript">
        function initiateChangePasswordDialog() {
        <% if (SETTING_PW_UI_MODE == HelpdeskServlet.SETTING_PW_UI_MODE.autogen) { %>
            generatePasswordPopup();
        <% } else { %>
            changePasswordPopup();
        <% } %>
        }
        function changePasswordPopup() {
            var bodyText = '<form action="#" method="post" enctype="application/x-www-form-urlencoded" autocomplete="off"';
            bodyText += ' onkeyup="validatePasswords(\'<%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserDN())%>\');">';
            bodyText += '<span id="message" class="message message-info" style="width: 400">Please type new password</span>'
        <% if (SETTING_PW_UI_MODE == HelpdeskServlet.SETTING_PW_UI_MODE.both) { %>
            bodyText += '<p>&nbsp;»&nbsp; <a href="#" onclick="clearDigitWidget(\'changepassword-popup\');generatePasswordPopup();"><pwm:Display key="Display_AutoGeneratedPassword"/></a></p>';
        <% } %>
            bodyText += '<input type="text" name="password1" id="password1" class="inputfield" style="width: 200px"/>';
            bodyText += '<br/>';
            bodyText += '<input type="text" name="password2" id="password2" class="inputfield" style="width: 200px"/>';
            bodyText += '<br/>';
            bodyText += '<input type="submit" name="change" class="btn" id="password_button" value=" <pwm:Display key="Button_ChangePassword"/> " onclick="var pw=getObject(\'password1\').value;clearDigitWidget(\'changepassword-popup\');doPasswordChange(pw)" disabled="true"/>';
            bodyText += '</form>';
            try { getObject('message').id = "base-message"; } catch (e) {}
            clearDigitWidget('changepassword-popup');
            dojo.require("dijit.Dialog");
            var theDialog = new dijit.Dialog({
                id: 'changepassword-popup',
                title: 'Change Password for <%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserID())%>',
                style: "width: 450px",
                content: bodyText,
                hide: function(){
                    clearDigitWidget('changepassword-popup');
                    getObject('base-message').id = "message";
                }
            });
            theDialog.show();
            setTimeout(function(){ getObject('password1').focus();},500);
        }
        function generatePasswordPopup() {
            var dataInput = {};
            dataInput['username'] = '<%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserDN())%>';
            dataInput['strength'] = 0;

            var randomConfig = {};
            randomConfig['dataInput'] = dataInput;
            randomConfig['finishAction'] = "clearDigitWidget('randomPasswordDialog');doPasswordChange(PWM_GLOBAL['SelectedRandomPassword'])";
            doRandomGeneration(randomConfig);
        }
        function doPasswordChange(password) {
            showWaitDialog('Setting: <b>' + password + '</b>');
            var inputValues = {};
            inputValues['username'] = '<%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserDN())%>';
            inputValues['password'] = password;
            setTimeout(function(){
                dojo.xhrPost({
                    url: PWM_GLOBAL['url-restservice'] + "/setpassword",
                    headers: {"Accept":"application/json"},
                    content: inputValues,
                    timeout: 90000,
                    sync: true,
                    handleAs: "json",
                    load: function(results){
                        var bodyText = "";
                        if (results['success'] == 'true') {
                            bodyText += PWM_STRINGS['Message_SuccessUnknown'];
                            bodyText += '<br/><br/><b>' + password + '</b><br/';
                        <% if (SETTING_CLEAR_RESPONSES == HelpdeskServlet.SETTING_CLEAR_RESPONSES.ask) { %>
                            bodyText += '<br/><br/><button onclick="doResponseClear(\'<%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserDN())%>\')">';
                            bodyText += 'Clear Responses</button><br/>';
                        <% } %>
                        } else {
                            bodyText += results['errorMsg'];
                        }
                        bodyText += '<br/><br/><button onclick="getObject(\'searchForm\').submit();"> OK </button>';
                        clearDigitWidget('waitDialogID');
                        dojo.require("dijit.Dialog");
                        var theDialog = new dijit.Dialog({
                            id: 'result-popup',
                            //title: '',
                            style: "width: 450px",
                            content: bodyText,
                            hide: function(){
                                clearDigitWidget('result-popup');
                            }
                        });
                        theDialog.show();
                    },
                    error: function(errorObj){
                        clearDigitWidget('waitDialogID');
                        showError("unexpected set password error: " + errorObj);
                    }
                });
            },300);
        }
        function doResponseClear(username) {
            clearDigitWidget('result-popup');
            showWaitDialog(PWM_STRINGS['Display_PleaseWait']);
            var inputValues = { 'username':username };
            setTimeout(function(){
                dojo.xhrPost({
                    url: PWM_GLOBAL['url-restservice'] + "/clearresponses",
                    headers: {"Accept":"application/json"},
                    content: inputValues,
                    timeout: 90000,
                    sync: true,
                    handleAs: "json",
                    load: function(results){
                        var bodyText = "";
                        if (results['success'] == 'true') {
                            bodyText += PWM_STRINGS['Message_SuccessUnknown'];
                        } else {
                            bodyText += results['errorMsg'];
                        }
                        bodyText += '<br/><br/><button onclick="getObject(\'searchForm\').submit();"> OK </button>';
                        clearDigitWidget('waitDialogID');
                        dojo.require("dijit.Dialog");
                        var theDialog = new dijit.Dialog({
                            id: 'result-popup',
                            style: "width: 450px",
                            content: bodyText,
                            hide: function(){
                                clearDigitWidget('result-popup');
                            }
                        });
                        theDialog.show();
                    },
                    error: function(errorObj){
                        clearDigitWidget('waitDialogID');
                        showError("unexpected clear responses error: " + errorObj);
                    }
                });
            },100);
        }
    </script>
</div>
</div>
<div dojoType="dijit.layout.ContentPane" title="History">
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
<div dojoType="dijit.layout.ContentPane" title="Password Policy">
    <table>
        <tr>
            <td class="key">
                Policy DN
            </td>
            <td>
                <% if ((searchedUserInfo.getPasswordPolicy() != null) && (searchedUserInfo.getPasswordPolicy().getChaiPasswordPolicy() != null) && (searchedUserInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry() != null) && (searchedUserInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry().getEntryDN() != null)) { %>
                <%= searchedUserInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry().getEntryDN() %><% } else { %>n/a
                <% } %>
            </td>
        </tr>
        <tr>
            <td class="key">
                Display
            </td>
            <td>
                <ul>
                    <%
                        final List<String> requirementLines = PasswordRequirementsTag.getPasswordRequirementsStrings(searchedUserInfo.getPasswordPolicy(), ContextManager.getPwmApplication(session).getConfig(), pwmSession.getSessionStateBean().getLocale()); %>
                    <% for (final String requirementLine : requirementLines) { %>
                    <li><%=requirementLine%>
                    </li>
                    <% } %>
                </ul>
            </td>
        </tr>
    </table>
</div>
<div dojoType="dijit.layout.ContentPane" title="ChallengeSet">
    <table>
        <% if (searchedUserInfo.getChallengeSet() != null) { %>
        <tr>
            <td class="key">
                ChallengeSet Locale
            </td>
            <td>
                <%=searchedUserInfo.getChallengeSet().getLocale()%>
            </td>
        </tr>
        <tr>
            <td class="key">
                Identifier
            </td>
            <td>
                <%=searchedUserInfo.getChallengeSet().getIdentifier()%>
            </td>
        </tr>
        <tr>
            <td class="key">
                Minimum Random
            </td>
            <td>
                <%=searchedUserInfo.getChallengeSet().getMinRandomRequired()%>
            </td>
        </tr>
        <% for (final Challenge loopChallange : searchedUserInfo.getChallengeSet().getRequiredChallenges()) { %>
        <tr>
            <td class="key">
                Required Challenge
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(loopChallange.getChallengeText()) %>
            </td>
        </tr>
        <% } %>
        <% for (final Challenge loopChallange : searchedUserInfo.getChallengeSet().getRandomChallenges()) { %>
        <tr>
            <td class="key">
                Random Challenge
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(loopChallange.getChallengeText()) %>
            </td>
        </tr>
        <% } %>
        <% } else { %>
        <tr>
            <td class="key">
                ChallengeSet
            </td>
            <td>
                ChallengeSet not configured for user
            </td>
        </tr>
        <% } %>
    </table>
</div>
</div>
</div>
</div>
<% } else { %>
<div>&nbsp;</div>
<% } %>
</div>
</div>
<script type="text/javascript">
    PWM_STRINGS['Title_RandomPasswords'] = "<pwm:Display key="Title_RandomPasswords"/>";
    PWM_STRINGS['Message_SuccessUnknown'] = "<%=Message.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), Message.SUCCESS_UNKNOWN, pwmApplication.getConfig())%>";
    PWM_STRINGS['Display_PasswordGeneration'] = "<pwm:Display key="Display_PasswordGeneration"/>";
    dojo.addOnLoad(function(){
        dojo.require("dijit.layout.TabContainer");
        dojo.require("dijit.layout.ContentPane");
        dojo.require("dojo.parser");
        dojo.parser.parse();
    });
</script>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
