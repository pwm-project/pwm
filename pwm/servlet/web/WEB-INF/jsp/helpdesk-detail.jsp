<%--
  ~ Password Management Servlets (PWM)
  ~ http://code.google.com/p/pwm/
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2014 The PWM Project
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

<%@ page import="com.novell.ldapchai.ChaiPasswordRule" %>
<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="password.pwm.bean.ResponseInfoBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.config.ActionConfiguration" %>
<%@ page import="password.pwm.config.FormConfiguration" %>
<%@ page import="password.pwm.config.PwmPasswordRule" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.option.HelpdeskClearResponseMode" %>
<%@ page import="password.pwm.config.option.HelpdeskUIMode" %>
<%@ page import="password.pwm.config.option.ViewStatusFields" %>
<%@ page import="password.pwm.event.UserAuditRecord" %>
<%@ page import="password.pwm.http.bean.HelpdeskBean" %>
<%@ page import="password.pwm.http.tag.PasswordRequirementsTag" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmSession pwmSession = PwmSession.getPwmSession(request);
    final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
    final HelpdeskBean helpdeskBean = pwmSession.getHelpdeskBean();
    final DateFormat dateFormatter = PwmConstants.DEFAULT_DATETIME_FORMAT;
    final HelpdeskUIMode SETTING_PW_UI_MODE = HelpdeskUIMode.valueOf(
            pwmApplication.getConfig().readSettingAsString(PwmSetting.HELPDESK_SET_PASSWORD_MODE));
    final ResponseInfoBean responseInfoBean = helpdeskBean.getUserInfoBean().getResponseInfoBean();
    final String obfuscatedDN = helpdeskBean.getUserInfoBean().getUserIdentity().toObfuscatedKey(
            pwmApplication.getConfig());
    final UserInfoBean searchedUserInfo = helpdeskBean.getUserInfoBean();
    final String displayName = (String)request.getAttribute(PwmConstants.REQUEST_ATTR_HELPDESK_DETAILNAME);
    final Set<ViewStatusFields> viewStatusFields = pwmApplication.getConfig().readSettingAsOptionList(PwmSetting.HELPDESK_VIEW_STATUS_VALUES,ViewStatusFields.class);
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
</jsp:include>
<div id="centerbody">
<% if (displayName != null && !displayName.isEmpty()) { %>
<h2 style="text-align: center"><%=displayName%></h2>
<% } %>
<div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Field_Profile"/>">
    <div style="max-height: 400px; overflow: auto;">
        <table>
            <% for (FormConfiguration formItem : helpdeskBean.getAdditionalUserInfo().getSearchDetails().keySet()) { %>
            <tr>
                <td class="key" id="key_<%=formItem.getName()%>">
                    <%= formItem.getLabel(pwmSession.getSessionStateBean().getLocale())%>
                </td>
                <td id="value_<%=formItem.getName()%>">
                    <% final String loopValue = helpdeskBean.getAdditionalUserInfo().getSearchDetails().get(formItem); %>
                    <%= loopValue == null ? "" : StringUtil.escapeHtml(loopValue) %>
                </td>
            </tr>
            <%  } %>
        </table>
    </div>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_Status"/>">
<table>
<% if (viewStatusFields.contains(ViewStatusFields.UserDN)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_UserDN"/>
    </td>
    <td>
        <span style="word-wrap: break-word; word-break: break-all">
        <%= StringUtil.escapeHtml(searchedUserInfo.getUserIdentity().getUserDN()) %>
        </span>
    </td>
</tr>
<% if (pwmApplication.getConfig().getLdapProfiles().size() > 1) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_LdapProfile"/>
    </td>
    <td>
        <%= StringUtil.escapeHtml(pwmApplication.getConfig().getLdapProfiles().get(searchedUserInfo.getUserIdentity().getLdapProfileID()).getDisplayName(pwmSession.getSessionStateBean().getLocale())) %>
    </td>
</tr>
<% } %>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.Username)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_Username"/>
    </td>
    <td>
        <%= StringUtil.escapeHtml(searchedUserInfo.getUsername()) %>
    </td>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.UserEmail)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_UserEmail"/>
    </td>
    <td>
        <%= StringUtil.escapeHtml(searchedUserInfo.getUserEmailAddress()) %>
    </td>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.AccountEnabled)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_AccountEnabled"/>
    </td>
    <td>
        <%if (helpdeskBean.getAdditionalUserInfo().isAccountEnabled()) { %><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
    </td>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.LastLoginTime)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_LastLoginTime"/>
    </td>
    <% if (helpdeskBean.getAdditionalUserInfo().getLastLoginTime() == null) { %>
    <td>
        <pwm:display key="Value_NotApplicable"/>
    </td>
    <% } else { %>
    <td class="timestamp">
        <%= dateFormatter.format(helpdeskBean.getAdditionalUserInfo().getLastLoginTime()) %>
    </td>
    <% } %>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.LastLoginTimeDelta)) { %>
<% if (helpdeskBean.getAdditionalUserInfo().getLastLoginTime() != null) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_LastLoginTimeDelta"/>
    </td>
    <td>
        <%= TimeDuration.fromCurrent(helpdeskBean.getAdditionalUserInfo().getLastLoginTime()).asLongString(pwmSession.getSessionStateBean().getLocale()) %>
    </td>
</tr>
<% } %>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.PasswordExpired)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_PasswordExpired"/>
    </td>
    <td>
        <%if (searchedUserInfo.getPasswordState().isExpired()) {%><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
    </td>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.PasswordPreExpired)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_PasswordPreExpired"/>
    </td>
    <td>
        <%if (searchedUserInfo.getPasswordState().isPreExpired()) {%><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
    </td>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.PasswordWarnPeriod)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_PasswordWithinWarningPeriod"/>
    </td>
    <td>
        <%if (searchedUserInfo.getPasswordState().isWarnPeriod()) { %><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
    </td>
</tr>
<% } %>
<!--
        <tr>
            <td class="key">
                <pwm:display key="Field_PasswordViolatesPolicy"/>
            </td>
            <td>
                <% if (searchedUserInfo.getPasswordState().isViolatesPolicy()) {%><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
            </td>
        </tr>
        -->
<% if (viewStatusFields.contains(ViewStatusFields.PasswordSetTime)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_PasswordSetTime"/>
    </td>
    <% if (searchedUserInfo.getPasswordLastModifiedTime() == null) { %>
    <td>
        <pwm:display key="Value_NotApplicable"/>
    </td>
    <% } else { %>
    <td class="timestamp">
        <%= dateFormatter.format(searchedUserInfo.getPasswordLastModifiedTime()) %>
    </td>
    <% } %>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.PasswordSetTimeDelta)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_PasswordSetTimeDelta"/>
    </td>
    <td>
        <%= searchedUserInfo.getPasswordLastModifiedTime() != null ? TimeDuration.fromCurrent(searchedUserInfo.getPasswordLastModifiedTime()).asLongString(pwmSession.getSessionStateBean().getLocale()) : Display.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(), "Value_NotApplicable", pwmApplicationHeader.getConfig())%>
    </td>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.PasswordExpireTime)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_PasswordExpirationTime"/>
    </td>
    <% if (searchedUserInfo.getPasswordExpirationTime() == null) { %>
    <td>
        <pwm:display key="Value_NotApplicable"/>
    </td>
    <% } else { %>
    <td class="timestamp">
        <%= dateFormatter.format(searchedUserInfo.getPasswordExpirationTime()) %>
    </td>
    <% } %>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.IntruderDetect)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_PasswordLocked"/>
    </td>
    <% if (helpdeskBean.getAdditionalUserInfo().isIntruderLocked()) { %>
    <td class="health-WARN">
        <pwm:display key="Value_True"/>
    </td>
    <% } else { %>
    <td>
        <pwm:display key="Value_False"/>
    </td>
    <% } %>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.ResponsesStored)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_ResponsesStored"/>
    </td>
    <td>
        <% if (helpdeskBean.getUserInfoBean().getResponseInfoBean() != null) { %>
        <pwm:display key="Value_True"/>
        <% } else { %>
        <pwm:display key="Value_False"/>
        <% } %>
    </td>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.ResponsesNeeded)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_ResponsesNeeded"/>
    </td>
    <td>
        <% if (helpdeskBean.getUserInfoBean().isRequiresResponseConfig()) { %>
        <pwm:display key="Value_True"/>
        <% } else { %>
        <pwm:display key="Value_False"/>
        <% } %>
    </td>
</tr>
<% } %>
<% if (viewStatusFields.contains(ViewStatusFields.ResponsesTimestamp)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_ResponsesTimestamp"/>
    </td>
    <% if (responseInfoBean == null || responseInfoBean.getTimestamp() == null) { %>
    <td>
        <pwm:display key="Value_NotApplicable"/>
    </td>
    <% } else { %>
    <td class="timestamp">
        <%= dateFormatter.format(responseInfoBean.getTimestamp()) %>
    </td>
    <% } %>
</tr>
<% } %>
<pwm:if test="otpEnabled">
    <% if (viewStatusFields.contains(ViewStatusFields.OTPStored)) { %>
    <tr>
        <td class="key">
            <pwm:display key="Field_OTP_Stored"/>
        </td>
        <td>
            <%if (searchedUserInfo.getOtpUserRecord() != null) {%><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
        </td>
    </tr>
    <% } %>
    <% if (viewStatusFields.contains(ViewStatusFields.OTPTimestamp)) { %>
    <tr>
        <td class="key">
            <pwm:display key="Field_OTP_Timestamp"/>
        </td>
        <% if (searchedUserInfo.getOtpUserRecord() == null || searchedUserInfo.getOtpUserRecord().getTimestamp() == null) { %>
        <td>
            <pwm:display key="Value_NotApplicable"/>
        </td>
        <% } else { %>
        <td class="timestamp">
            <%= dateFormatter.format(searchedUserInfo.getOtpUserRecord().getTimestamp()) %>
        </td>
        <% } %>
    </tr>
    <% } %>
</pwm:if>
<% if (viewStatusFields.contains(ViewStatusFields.GUID)) { %>
<tr>
    <td class="key">
        <pwm:display key="Field_UserGUID"/>
    </td>
    <td>
        <%= StringUtil.escapeHtml(searchedUserInfo.getUserGuid()) %>
    </td>
</tr>
<% } %>
</table>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_UserEventHistory"/>">
    <% if (helpdeskBean.getAdditionalUserInfo().getUserHistory() != null && !helpdeskBean.getAdditionalUserInfo().getUserHistory().isEmpty()) { %>
    <div style="max-height: 400px; overflow: auto;">
        <table>
            <% for (final UserAuditRecord record : helpdeskBean.getAdditionalUserInfo().getUserHistory()) { %>
            <tr>
                <td class="key timestamp" style="width:50%">
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
        <pwm:display key="Display_SearchResultsNone"/>
    </div>
    <% } %>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_PasswordPolicy"/>">
    <div style="max-height: 400px; overflow: auto;">
        <table>
            <tr>
                <td class="key">
                    <pwm:display key="Field_Policy"/>
                </td>
                <td>
                    <% if ((searchedUserInfo.getPasswordPolicy() != null)
                            && (searchedUserInfo.getPasswordPolicy().getChaiPasswordPolicy() != null)
                            && (searchedUserInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry() != null)
                            && (searchedUserInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry().getEntryDN() != null)) { %>
                    <%= searchedUserInfo.getPasswordPolicy().getChaiPasswordPolicy().getPolicyEntry().getEntryDN() %>
                    <% } else { %>
                    <pwm:display key="Value_NotApplicable"/>
                    <% } %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_Profile"/>
                </td>
                <td>
                    <%= searchedUserInfo.getPasswordPolicy().getIdentifier() == null
                            ? Display.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),"Value_NotApplicable",pwmApplication.getConfig())
                            : PwmConstants.DEFAULT_PASSWORD_PROFILE.equalsIgnoreCase(searchedUserInfo.getPasswordPolicy().getIdentifier())
                            ? Display.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),"Value_Default",pwmApplication.getConfig())
                            : searchedUserInfo.getPasswordPolicy().getIdentifier()
                    %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_Display"/>
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
        <table>
            <% for (final PwmPasswordRule rule : PwmPasswordRule.values()) { %>
            <tr>
                <td class="key">
                    <%= rule.getLabel(pwmSession.getSessionStateBean().getLocale(),pwmApplication.getConfig()) %>
                </td>
                <td>
                    <% if (searchedUserInfo.getPasswordPolicy().getValue(rule) != null) { %>
                    <% if (ChaiPasswordRule.RuleType.BOOLEAN == rule.getRuleType()) { %>
                    <% if (Boolean.parseBoolean(searchedUserInfo.getPasswordPolicy().getValue(rule))) { %>
                    <pwm:display key="Value_True"/>
                    <% } else { %>
                    <pwm:display key="Value_False"/>
                    <% } %>
                    <% } else { %>
                    <%= StringUtil.escapeHtml(searchedUserInfo.getPasswordPolicy().getValue(rule)) %>
                    <% } %>
                    <% } %>
                </td>
            </tr>
            <% } %>
        </table>
    </div>
</div>
<% if (responseInfoBean != null && responseInfoBean.getHelpdeskCrMap() != null && !responseInfoBean.getHelpdeskCrMap().isEmpty()) { %>
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_SecurityResponses"/>">
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
    <% if (SETTING_PW_UI_MODE != HelpdeskUIMode.none) { %>
    <button class="btn" onclick="initiateChangePasswordDialog()">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-key"></span></pwm:if>
        <pwm:display key="Button_ChangePassword"/>
    </button>
    <% } %>
    <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_RESPONSES_BUTTON)) { %>
    <% if (helpdeskBean.getUserInfoBean().getResponseInfoBean() != null) { %>
    <button id="clearResponsesBtn" class="btn" onclick="PWM_HELPDESK.doResponseClear()">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-eraser"></span></pwm:if>
        <pwm:display key="Button_ClearResponses"/>
    </button>
    <% } else { %>
    <button id="clearResponsesBtn" class="btn" disabled="disabled">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-eraser"></span></pwm:if>
        <pwm:display key="Button_ClearResponses"/>
    </button>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.showTooltip({
                id: "clearResponsesBtn",
                text: 'User does not have responses'
            });
        });
    </script>
    <% } %>
    <% } %>
    <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_OTP_BUTTON) &&
            ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.OTP_ENABLED)) { %>
    <button id="clearOtpSecretBtn" class="btn" onclick="document.clearOtpSecretForm.submit()"><pwm:display key="Button_ClearOtpSecret"/></button>
    <% } %>
    <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) { %>
    <% if (helpdeskBean.getAdditionalUserInfo().isIntruderLocked()) { %>
    <button class="btn" onclick="document.ldapUnlockForm.submit()">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-unlock"></span></pwm:if>
        <pwm:display key="Button_Unlock"/>
    </button>
    <% } else { %>
    <button id="unlockBtn" class="btn" disabled="disabled">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-unlock"></span></pwm:if>
        <pwm:display key="Button_Unlock"/>
    </button>
    <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                PWM_MAIN.showTooltip({
                    id: "unlockBtn",
                    text: 'User is not locked'
                });
            });
        </script>
    </pwm:script>
    <% } %>
    <% if (ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.HELPDESK_DELETE_USER_BUTTON)) { %>
    <button class="btn" onclick="PWM_HELPDESK.deleteUser('<%=StringUtil.escapeHtml(obfuscatedDN)%>')">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-times"></span></pwm:if>
        Delete User
    </button>
    <% } %>
    <button name="button_continue" class="btn" onclick="window.location = window.location" id="button_continue">
        <pwm:if test="showIcons"><span class="btn-icon fa fa-backward"></span></pwm:if>
        <pwm:display key="Button_GoBack"/>
    </button>
    <% } %>
    <br/>
    <% final List<ActionConfiguration> actions = pwmApplication.getConfig().readSettingAsAction(PwmSetting.HELPDESK_ACTIONS); %>
    <% for (final ActionConfiguration loopAction : actions) { %>
    <button class="btn" name="action-<%=loopAction.getName()%>" id="action-<%=loopAction.getName()%>" onclick="PWM_HELPDESK.executeAction('<%=StringUtil.escapeJS(loopAction.getName())%>')"><%=StringUtil.escapeHtml(loopAction.getName())%></button>
    <pwm:script>
        <script type="text/javascript">
            PWM_GLOBAL['startupFunctions'].push(function(){
                PWM_MAIN.showTooltip({
                    id: "action-<%=loopAction.getName()%>",
                    position: 'above',
                    text: '<%=StringUtil.escapeJS(loopAction.getDescription())%>'
                });
            });
        </script>
    </pwm:script>
    <% } %>
    <form name="continueForm" id="continueForm" method="post" action="Helpdesk" enctype="application/x-www-form-urlencoded">
        <input type="hidden" name="processAction" value="detail"/>
        <input type="hidden" name="userKey" value="<%=StringUtil.escapeHtml(obfuscatedDN)%>"/>
        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
    </form>
    <form name="ldapUnlockForm" action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form">
        <input type="hidden" name="processAction" value="doUnlock"/>
        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
    </form>
    <form name="clearOtpSecretForm" action="<pwm:url url='Helpdesk'/>" method="post" enctype="application/x-www-form-urlencoded" class="pwm-form">
        <input type="hidden" name="processAction" value="doClearOtpSecret"/>
        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
    </form>
</div>
</div>
<div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        function initiateChangePasswordDialog() {
            <% if (SETTING_PW_UI_MODE == HelpdeskUIMode.autogen) { %>
            PWM_HELPDESK.generatePasswordPopup();
            <% } else if (SETTING_PW_UI_MODE == HelpdeskUIMode.random) { %>
            PWM_HELPDESK.setRandomPasswordPopup();
            <% } else { %>
            PWM_HELPDESK.changePasswordPopup();
            <% } %>
        }

        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dojo/domReady!","dijit/layout/TabContainer","dijit/layout/ContentPane"],function(dojoParser){
                dojoParser.parse();
                PWM_VAR['helpdesk_obfuscatedDN'] = '<%=StringUtil.escapeJS(obfuscatedDN)%>';
                PWM_VAR['helpdesk_username'] = '<%=StringUtil.escapeJS(helpdeskBean.getUserInfoBean().getUsername())%>';
                PWM_VAR['helpdesk_setting_clearResponses'] = '<%=pwmApplication.getConfig().readSettingAsEnum(PwmSetting.HELPDESK_CLEAR_RESPONSES,HelpdeskClearResponseMode.class)%>';
                PWM_VAR['helpdesk_setting_PwUiMode'] = '<%=pwmApplication.getConfig().readSettingAsEnum(PwmSetting.HELPDESK_SET_PASSWORD_MODE,HelpdeskUIMode.class) %>';
            });
        });
    </script>
</pwm:script>
<script type="text/javascript" defer="defer" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/helpdesk.js'/>"></script>
<script type="text/javascript" defer="defer" src="<%=request.getContextPath()%><pwm:url url='/public/resources/js/changepassword.js'/>"></script>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
