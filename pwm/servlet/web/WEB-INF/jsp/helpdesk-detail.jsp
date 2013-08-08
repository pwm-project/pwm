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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.servlet.HelpdeskBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.config.ActionConfiguration" %>
<%@ page import="password.pwm.config.FormConfiguration" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.event.AuditRecord" %>
<%@ page import="password.pwm.servlet.HelpdeskServlet" %>
<%@ page import="password.pwm.tag.PasswordRequirementsTag" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.List" %>
<%@ page import="password.pwm.config.PwmPasswordRule" %>
<%@ page import="password.pwm.util.operations.UserSearchEngine" %>
<%@ page import="password.pwm.bean.ResponseInfoBean" %>
<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(request); %>
<% final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean(); %>
<% final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.FULL, SimpleDateFormat.FULL, pwmSession.getSessionStateBean().getLocale()); %>
<% final HelpdeskServlet.SETTING_PW_UI_MODE SETTING_PW_UI_MODE = HelpdeskServlet.SETTING_PW_UI_MODE.valueOf(pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_SET_PASSWORD_MODE)); %>
<% final HelpdeskServlet.SETTING_CLEAR_RESPONSES SETTING_CLEAR_RESPONSES = HelpdeskServlet.SETTING_CLEAR_RESPONSES.valueOf(pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_CLEAR_RESPONSES)); %>
<% final ResponseInfoBean responseInfoBean = helpdeskBean.getUserInfoBean().getResponseInfoBean(); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler()" class="nihilo">
<script type="text/javascript"
        src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/changepassword.js'/>"></script>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody">
        <% final UserInfoBean searchedUserInfo = helpdeskBean.getUserInfoBean(); %>
        <h1><%=searchedUserInfo.getUserID()%></h1>
        <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:Display key="Title_UserInformation"/>">
                <div style="max-height: 400px; overflow: auto;">
                    <table>
                        <tr>
                            <td class="key">
                                <pwm:Display key="Field_Username"/>
                            </td>
                            <td>
                                <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getUserID()) %>
                            </td>
                        </tr>
                        <tr>
                            <td class="key">
                                <pwm:Display key="Field_UserDN"/>
                            </td>
                            <td>
                                <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getUserDN()) %>
                            </td>
                        </tr>
                    </table>
                    <table>
                        <% for (FormConfiguration formItem : helpdeskBean.getAdditionalUserInfo().getSearchDetails().keySet()) { %>
                        <tr>
                            <td class="key">
                                <%= formItem.getLabel(pwmSession.getSessionStateBean().getLocale())%>
                            </td>
                            <td>
                                <% final String loopValue = helpdeskBean.getAdditionalUserInfo().getSearchDetails().get(formItem); %>
                                <%= loopValue == null ? "" : StringEscapeUtils.escapeHtml(loopValue) %>
                            </td>
                        </tr>
                        <%  } %>
                    </table>
                    <table>
                        <tr>
                            <td class="key">
                                <%=PwmConstants.PWM_APP_NAME%> <pwm:Display key="Field_UserGUID"/>
                            </td>
                            <td>
                                <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getUserGuid()) %>
                            </td>
                        </tr>
                    </table>
                </div>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="Status">
                <table>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_Username"/>
                        </td>
                        <td>
                            <%= StringEscapeUtils.escapeHtml(searchedUserInfo.getUserID()) %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_AccountEnabled"/>
                        </td>
                        <td>
                            <%if (helpdeskBean.getAdditionalUserInfo().isAccountEnabled()) { %><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_LastLoginTime"/>
                        </td>
                        <td>
                            <%= helpdeskBean.getAdditionalUserInfo().getLastLoginTime() != null ? dateFormatter.format(helpdeskBean.getAdditionalUserInfo().getLastLoginTime()) : ""%>
                        </td>
                    </tr>
                    <% if (helpdeskBean.getAdditionalUserInfo().getLastLoginTime() != null) { %>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_LastLoginTimeDelta"/>
                        </td>
                        <td>
                            <%= TimeDuration.fromCurrent(helpdeskBean.getAdditionalUserInfo().getLastLoginTime()).asLongString(pwmSession.getSessionStateBean().getLocale()) %>
                        </td>
                    </tr>
                    <% } %>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordExpired"/>
                        </td>
                        <td>
                            <%if (searchedUserInfo.getPasswordState().isExpired()) {%><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordPreExpired"/>
                        </td>
                        <td>
                            <%if (searchedUserInfo.getPasswordState().isPreExpired()) {%><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordWithinWarningPeriod"/>
                        </td>
                        <td>
                            <%if (searchedUserInfo.getPasswordState().isWarnPeriod()) { %><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordViolatesPolicy"/>
                        </td>
                        <td>
                            <% if (searchedUserInfo.getPasswordState().isViolatesPolicy()) {%><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordSetTime"/>
                        </td>
                        <td>
                            <%= searchedUserInfo.getPasswordLastModifiedTime() != null ? dateFormatter.format(searchedUserInfo.getPasswordLastModifiedTime()) : "n/a"%>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordSetTimeDelta"/>
                        </td>
                        <td>
                            <%= searchedUserInfo.getPasswordLastModifiedTime() != null ? TimeDuration.fromCurrent(searchedUserInfo.getPasswordLastModifiedTime()).asLongString(pwmSession.getSessionStateBean().getLocale()) : "n/a"%>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordExpirationTime"/>
                        </td>
                        <td>
                            <%= searchedUserInfo.getPasswordExpirationTime() != null ? dateFormatter.format(searchedUserInfo.getPasswordExpirationTime()) : "n/a"%>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordLocked"/>
                        </td>
                        <% if (helpdeskBean.getAdditionalUserInfo().isIntruderLocked()) { %>
                        <td class="health-WARN">
                            <pwm:Display key="Value_True"/>
                        </td>
                        <% } else { %>
                        <td>
                            <pwm:Display key="Value_False"/>
                        </td>
                        <% } %>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_ResponsesStored"/>
                        </td>
                        <td>
                            <% if (helpdeskBean.getUserInfoBean().getResponseInfoBean() != null) { %>
                            <pwm:Display key="Value_True"/>
                            <% } else { %>
                            <pwm:Display key="Value_False"/>
                            <% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_ResponsesNeeded"/>
                        </td>
                        <td>
                            <% if (helpdeskBean.getUserInfoBean().isRequiresResponseConfig()) { %>
                            <pwm:Display key="Value_True"/>
                            <% } else { %>
                            <pwm:Display key="Value_False"/>
                            <% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_ResponsesTimestamp"/>
                        </td>
                        <td>
                            <%= responseInfoBean != null && responseInfoBean.getTimestamp() != null ? dateFormatter.format(responseInfoBean.getTimestamp()) : "n/a" %>
                        </td>
                    </tr>
                </table>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:Display key="Title_UserEventHistory"/>">
                <% if (helpdeskBean.getAdditionalUserInfo().getUserHistory() != null && !helpdeskBean.getAdditionalUserInfo().getUserHistory().isEmpty()) { %>
                <div style="max-height: 400px; overflow: auto;">
                    <table>
                        <% for (final AuditRecord record : helpdeskBean.getAdditionalUserInfo().getUserHistory()) { %>
                        <tr>
                            <td class="key" style="width:50%">
                                <%= dateFormatter.format(record.getTimestamp()) %>
                            </td>
                            <td>
                                <%= record.getEventCode().getLocalizedString(ContextManager.getPwmApplication(session).getConfig(), pwmSession.getSessionStateBean().getLocale()) %>
                                <%= record.getMessage() != null && record.getMessage().length() > 1 ? " (" + record.getMessage() + ") " : "" %>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                </div>
                <% } else { %>
                <div style="width:100%; text-align: center">
                    <pwm:Display key="Display_SearchResultsNone"/>
                </div>
                <% } %>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:Display key="Title_PasswordPolicy"/>">
                <div style="max-height: 400px; overflow: auto;">
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
                        <% for (final PwmPasswordRule rule : PwmPasswordRule.values()) { %>
                        <tr>
                            <td class="key">
                                <%= rule.name() %>
                            </td>
                            <td>
                                <%= searchedUserInfo.getPasswordPolicy().getValue(rule) != null ? StringEscapeUtils.escapeHtml(searchedUserInfo.getPasswordPolicy().getValue(rule)) : "" %>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                </div>
            </div>
            <% if (responseInfoBean != null && responseInfoBean.getHelpdeskCrMap() != null && !responseInfoBean.getHelpdeskCrMap().isEmpty()) { %>
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:Display key="Title_SecurityResponses"/>">
                <table>
                    <% for (final Challenge challenge : responseInfoBean.getHelpdeskCrMap().keySet()) { %>
                    <tr>
                        <td class="key">
                            <%=challenge.getChallengeText()%>
                        </td>
                        <td>
                            <%=responseInfoBean.getHelpdeskCrMap().get(challenge)%>
                        </td>
                    </tr>
                    <% } %>
                </table>
            </div>
            <% } %>
        </div>
        <div id="buttonbar">
            <% if (SETTING_PW_UI_MODE != HelpdeskServlet.SETTING_PW_UI_MODE.none) { %>
            <button class="btn" onclick="initiateChangePasswordDialog()"><pwm:Display key="Button_ChangePassword"/></button>
            <% } %>
            <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) { %>
            <% if (helpdeskBean.getAdditionalUserInfo().isIntruderLocked()) { %>
            <button class="btn" onclick="document.ldapUnlockForm.submit()">Unlock</button>
            <% } else { %>
            <button id="unlockBtn" class="btn" disabled="disabled">Unlock</button>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    require(["dojo/domReady!","dijit/Tooltip"],function(dojo,Tooltip){
                        new Tooltip({
                            connectId: ["unlockBtn"],
                            label: 'User is not locked'
                        });
                    });
                });
            </script>
            <% } %>
            <button name="button_continue" class="btn" onclick="getObject('continueForm').submit()" id="button_continue"><pwm:Display key="Button_Continue"/></button>
            <% } %>
            <br/>
            <% final List<ActionConfiguration> actions = pwmApplication.getConfig().readSettingAsAction(PwmSetting.HELPDESK_ACTIONS); %>
            <% for (final ActionConfiguration loopAction : actions) { %>
            <button class="btn" name="action-<%=loopAction.getName()%>" id="action-<%=loopAction.getName()%>" onclick="executeAction('<%=StringEscapeUtils.escapeJavaScript(loopAction.getName())%>')"><%=StringEscapeUtils.escapeHtml(loopAction.getName())%></button>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    require(["dojo/domReady!","dijit/Tooltip"],function(dojo,Tooltip){
                        new Tooltip({
                            connectId: ["action-<%=loopAction.getName()%>"],
                            position: 'above',
                            label: '<%=StringEscapeUtils.escapeJavaScript(loopAction.getDescription())%>'
                        });
                    });
                });
            </script>
            <% } %>
        
        
            <form name="continueForm" id="continueForm" method="post" action="Helpdesk" enctype="application/x-www-form-urlencoded">
                <input type="hidden" name="processAction" value="continue"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
            <form name="ldapUnlockForm" action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded" onsubmit="handleFormSubmit('unlockBtn', this)">
                <input type="hidden" name="processAction" value="doUnlock"/>
                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    function initiateChangePasswordDialog() {
        <% if (SETTING_PW_UI_MODE == HelpdeskServlet.SETTING_PW_UI_MODE.autogen) { %>
        generatePasswordPopup();
        <% } else if (SETTING_PW_UI_MODE == HelpdeskServlet.SETTING_PW_UI_MODE.sendpassword) { %>
        setRandomPasswordPopup();
        <% } else { %>
        changePasswordPopup();
        <% } %>
    }

    function setRandomPasswordPopup() {
        var title = '<pwm:Display key="Title_ChangePassword"/>: <%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserID())%>';
        var body = PWM_STRINGS['Display_SetRandomPasswordPrompt'];
        var yesAction = function() {
            doPasswordChange('[' + PWM_STRINGS['Display_Random'] +  ']',true);
        }
        showConfirmDialog(title,body,yesAction);
    }

    function changePasswordPopup() {
        require(["dijit/Dialog"],function(){
            var bodyText = '<span id="message" class="message message-info" style="width: 400"><pwm:Display key="Field_NewPassword"/></span>'
            <% if (SETTING_PW_UI_MODE == HelpdeskServlet.SETTING_PW_UI_MODE.both) { %>
            bodyText += '<p>&nbsp;»&nbsp; <a href="#" onclick="clearDijitWidget(\'changepassword-popup\');generatePasswordPopup();"><pwm:Display key="Display_AutoGeneratedPassword"/></a></p>';
            <% } %>
            bodyText += '<table style="border: 0">';
            bodyText += '<tr style="border: 0"><td style="border: 0"><input type="text" name="password1" id="password1" class="inputfield" style="width: 260px" autocomplete="off" onkeyup="validatePasswords(\'<%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserDN())%>\');"/></td>';
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.PASSWORD_SHOW_STRENGTH_METER)) { %>
            bodyText += '<td style="border:0"><div id="strengthBox" style="visibility:hidden;">';
            bodyText += '<div id="strengthLabel"><pwm:Display key="Display_StrengthMeter"/></div>';
            bodyText += '<div class="progress-container" style="margin-bottom:10px">';
            bodyText += '<div id="strengthBar" style="width:0">&nbsp;</div></div></div></td>';
            <% } %>
            bodyText += '</tr><tr style="border: 0">';
            bodyText += '<td style="border: 0"><input type="text" name="password2" id="password2" class="inputfield" style="width: 260px" autocomplete="off" onkeyup="validatePasswords(\'<%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserDN())%>\');""/></td>';

            bodyText += '<td style="border: 0"><div style="margin:0;">';
            bodyText += '<img style="visibility:hidden;" id="confirmCheckMark" alt="checkMark" height="15" width="15" src="<%=request.getContextPath()%><pwm:url url='/public/resources/greenCheck.png'/>">';
            bodyText += '<img style="visibility:hidden;" id="confirmCrossMark" alt="crossMark" height="15" width="15" src="<%=request.getContextPath()%><pwm:url url='/public/resources/redX.png'/>">';
            bodyText += '</div></td>';

            bodyText += '</tr></table>';
            bodyText += '<button name="change" class="btn" id="password_button" onclick="var pw=getObject(\'password1\').value;clearDijitWidget(\'changepassword-popup\');doPasswordChange(pw)" disabled="true"/><pwm:Display key="Button_ChangePassword"/></button>';
            try { getObject('message').id = "base-message"; } catch (e) {}

            clearDijitWidget('changepassword-popup');
            var theDialog = new dijit.Dialog({
                id: 'dialogPopup',
                title: '<pwm:Display key="Title_ChangePassword"/>: <%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserID())%>',
                style: "width: 450px",
                content: bodyText,
                hide: function(){
                    closeWaitDialog();
                    getObject('base-message').id = "message";
                }
            });
            theDialog.show();
            setTimeout(function(){ getObject('password1').focus();},500);
        });
    }
    function generatePasswordPopup() {
        var dataInput = {};
        dataInput['username'] = '<%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserDN())%>';
        dataInput['strength'] = 0;

        var randomConfig = {};
        randomConfig['dataInput'] = dataInput;
        randomConfig['finishAction'] = "clearDijitWidget('randomPasswordDialog');doPasswordChange(PWM_GLOBAL['SelectedRandomPassword'])";
        doRandomGeneration(randomConfig);
    }

    function doPasswordChange(password, random) {
        require(["dojo","dijit/Dialog"],function(dojo,Dialog){
            showWaitDialog('<pwm:Display key="Title_PleaseWait"/>','<pwm:Display key="Field_NewPassword"/>: <b>' + password + '</b><br/><br/><br/><div id="WaitDialogBlank"/>');
            var inputValues = {};
            inputValues['username'] = '<%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserDN())%>';
            if (random) {
                inputValues['random'] = true;
            } else {
                inputValues['password'] = password;
            }
            setTimeout(function(){
                dojo.xhrPost({
                    url: PWM_GLOBAL['url-restservice'] + "/setpassword?pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    headers: {"Accept":"application/json"},
                    content: inputValues,
                    preventCache: true,
                    timeout: 90000,
                    sync: false,
                    handleAs: "json",
                    load: function(results){
                        var bodyText = "";
                        if (results['error'] == true) {
                            bodyText += results['errorMessage'];
                            bodyText += '<br/><br/>';
                            bodyText += results['errorDetail'];
                        } else {
                            bodyText += '<br/>';
                            bodyText += results['successMessage'];
                            bodyText += '</br></br>';
                            bodyText += '<pwm:Display key="Field_NewPassword"/>: <b>' + password + '</b>';
                            bodyText += '<br/>';
                        }
                        bodyText += '<br/><br/><button class="btn" onclick="getObject(\'continueForm\').submit();"> OK </button>';
                        <% if (SETTING_CLEAR_RESPONSES == HelpdeskServlet.SETTING_CLEAR_RESPONSES.ask) { %>
                        bodyText += '<span style="padding-left: 10px">&nbsp;</span>';
                        bodyText += '<button class="btn" onclick="doResponseClear(\'<%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserDN())%>\')">';
                        bodyText += 'Clear Responses</button>';
                        <% } %>
                        closeWaitDialog();
                        var theDialog = new Dialog({
                            id: 'dialogPopup',
                            title: '<pwm:Display key="Title_ChangePassword"/>: <%=StringEscapeUtils.escapeJavaScript(helpdeskBean.getUserInfoBean().getUserID())%>',
                            style: "width: 450px",
                            content: bodyText,
                            closable: false,
                            hide: function(){
                                closeWaitDialog();
                            }
                        });
                        theDialog.show();
                    },
                    error: function(errorObj){
                        closeWaitDialog();
                        showError("unexpected set password error: " + errorObj);
                    }
                });
            },300);
        });
    }
    function doResponseClear(username) {
        require(["dojo","dijit/Dialog"],function(dojo){
            closeWaitDialog();
            showWaitDialog(PWM_STRINGS['Display_PleaseWait']);
            var inputValues = { 'username':username };
            setTimeout(function(){
                dojo.xhrDelete({
                    url: PWM_GLOBAL['url-restservice'] + "/challenges?pwmFormID=" + PWM_GLOBAL['pwmFormID'],
                    headers: {"Accept":"application/json"},
                    content: inputValues,
                    preventCache: true,
                    timeout: 90000,
                    sync: false,
                    handleAs: "json",
                    load: function(results){
                        var bodyText = "";
                        if (results['error'] != true) {
                            bodyText += results['successMessage'];
                        } else {
                            bodyText += results['errorMsg'];
                        }
                        bodyText += '<br/><br/><button class="btn" onclick="getObject(\'continueForm\').submit();"> OK </button>';
                        closeWaitDialog();
                        var theDialog = new dijit.Dialog({
                            id: 'dialogPopup',
                            title: 'Clear Responses',
                            style: "width: 450px",
                            content: bodyText,
                            closable: false,
                            hide: function(){
                                clearDijitWidget('result-popup');
                            }
                        });
                        theDialog.show();
                    },
                    error: function(errorObj){
                        closeWaitDialog();
                        showError("unexpected clear responses error: " + errorObj);
                    }
                });
            },100);
        });
    }

    function executeAction(actionName) {
        showWaitDialog(null,null,function(){
            require(["dojo","dijit/Dialog"],function(dojo){
                dojo.xhrGet({
                    url: "Helpdesk?pwmFormID=" + PWM_GLOBAL['pwmFormID'] + "&processAction=executeAction&name=" + actionName,
                    preventCache: true,
                    dataType: "json",
                    handleAs: "json",
                    timeout: 90000,
                    load: function(data){
                        closeWaitDialog();
                        if (data['error'] == true) {
                            showDialog(PWM_STRINGS['Title_Error'],data['errorDetail']);
                        } else {
                            showDialog(PWM_STRINGS['Title_Success'],data['successMessage'],function(){getObject('continueForm').submit();});
                        }
                    },
                    error: function(errorObj){
                        closeWaitDialog();
                        showError('error executing action: ' + errorObj);
                    }
                });
            });
        });
    }

    function startupPage() {
        require(["dojo/parser","dojo/domReady!","dijit/layout/TabContainer","dijit/layout/ContentPane"],function(dojoParser){
            dojoParser.parse();
        });
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
        startupPage();
    });
</script>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
