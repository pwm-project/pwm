<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2016 The PWM Project
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
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.option.HelpdeskUIMode" %>
<%@ page import="password.pwm.config.option.ViewStatusFields" %>
<%@ page import="password.pwm.config.profile.HelpdeskProfile" %>
<%@ page import="password.pwm.config.profile.PwmPasswordRule" %>
<%@ page import="password.pwm.http.PwmSession" %>
<%@ page import="password.pwm.http.servlet.helpdesk.HelpdeskDetailInfoBean" %>
<%@ page import="password.pwm.http.tag.PasswordRequirementsTag" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.svc.event.UserAuditRecord" %>
<%@ page import="password.pwm.util.StringUtil" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="password.pwm.util.macro.MacroMachine" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext);
    final PwmSession pwmSession = pwmRequest.getPwmSession();
    final PwmApplication pwmApplication = pwmRequest.getPwmApplication();
    final HelpdeskProfile helpdeskProfile = pwmSession.getSessionManager().getHelpdeskProfile(pwmApplication);
    final DateFormat dateFormatter = PwmConstants.DEFAULT_DATETIME_FORMAT;
    final HelpdeskUIMode SETTING_PW_UI_MODE = HelpdeskUIMode.valueOf(helpdeskProfile.readSettingAsString(PwmSetting.HELPDESK_SET_PASSWORD_MODE));

    // user info
    final HelpdeskDetailInfoBean helpdeskDetailInfoBean = (HelpdeskDetailInfoBean)pwmRequest.getAttribute(PwmRequest.Attribute.HelpdeskDetail);
    final UserInfoBean searchedUserInfo = helpdeskDetailInfoBean.getUserInfoBean();
    final ResponseInfoBean responseInfoBean = searchedUserInfo.getResponseInfoBean();

    final String displayName = helpdeskDetailInfoBean.getUserDisplayName();
    final Set<ViewStatusFields> viewStatusFields = helpdeskProfile.readSettingAsOptionList(PwmSetting.HELPDESK_VIEW_STATUS_VALUES,ViewStatusFields.class);
    final boolean hasOtp = searchedUserInfo.getOtpUserRecord() != null;
%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody" style="min-width: 800px">
        <div id="page-content-title"><pwm:display key="Title_Helpdesk" displayIfMissing="true"/></div>
        <% if (displayName != null && !displayName.isEmpty()) { %>
        <h2 style="text-align: center"><%=displayName%></h2>
        <% } %>
        <pwm:script>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_VAR["helpdesk_obfuscatedDN"] = '<%=JspUtility.getAttribute(pageContext, PwmRequest.Attribute.HelpdeskObfuscatedDN)%>';
                    PWM_VAR["helpdesk_username"] = '<%=JspUtility.getAttribute(pageContext, PwmRequest.Attribute.HelpdeskUsername)%>';
                });
            </script>
        </pwm:script>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <table style="border:0">
            <tr>
                <td style="border:0; width: 600px; max-width:600px; vertical-align: top">
                    <div id="panel-helpdesk-detail" data-dojo-type="dijit.layout.TabContainer" style="max-width: 600px; height: 100%;" data-dojo-props="doLayout: false, persist: true" >
                        <div id="Field_Profile" data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Field_Profile"/>" class="tabContent">
                            <div style="max-height: 400px; overflow: auto;">
                                <table class="nomargin">
                                    <% for (FormConfiguration formItem : helpdeskDetailInfoBean.getSearchDetails().keySet()) { %>
                                    <tr>
                                        <td class="key" id="key_<%=StringUtil.escapeHtml(formItem.getName())%>" title="<%=StringUtil.escapeHtml(formItem.getDescription(pwmRequest.getLocale()))%>">
                                            <%= formItem.getLabel(pwmSession.getSessionStateBean().getLocale())%>
                                        </td>
                                        <td id="value_<%=formItem.getName()%>">
                                            <% for (Iterator<String> iter = helpdeskDetailInfoBean.getSearchDetails().get(formItem).iterator(); iter.hasNext(); ) { %>
                                            <% final String loopValue = iter.next(); %>
                                            <%= loopValue == null ? "" : StringUtil.escapeHtml(loopValue) %>
                                            <% if (iter.hasNext()) { %> <br/> <% } %>
                                            <% } %>
                                        </td>
                                    </tr>
                                    <%  } %>
                                </table>
                            </div>
                        </div>
                        <div id="Title_Status" data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_Status"/>" class="tabContent">
                            <table class="nomargin">
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
                                        <% if (searchedUserInfo.getUserEmailAddress() == null) { %>
                                        <pwm:display key="Value_NotApplicable"/>
                                        <% } else { %>
                                        <%= StringUtil.escapeHtml(searchedUserInfo.getUserEmailAddress()) %>
                                        <% } %>
                                    </td>
                                </tr>
                                <% } %>
                                <% if (viewStatusFields.contains(ViewStatusFields.UserSMS)) { %>
                                <tr>
                                    <td class="key">
                                        <pwm:display key="Field_UserSMS"/>
                                    </td>
                                    <td>
                                        <% if (searchedUserInfo.getUserSmsNumber() == null) { %>
                                        <pwm:display key="Value_NotApplicable"/>
                                        <% } else { %>
                                        <%= StringUtil.escapeHtml(searchedUserInfo.getUserSmsNumber()) %>
                                        <% } %>
                                    </td>
                                </tr>
                                <% } %>
                                <% if (viewStatusFields.contains(ViewStatusFields.AccountEnabled)) { %>
                                <tr>
                                    <td class="key">
                                        <pwm:display key="Field_AccountEnabled"/>
                                    </td>
                                    <td>
                                        <%if (helpdeskDetailInfoBean.isAccountEnabled()) { %><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
                                    </td>
                                </tr>
                                <% } %>
                                <% if (viewStatusFields.contains(ViewStatusFields.AccountExpired)) { %>
                                <tr>
                                    <td class="key">
                                        <pwm:display key="Field_AccountExpired"/>
                                    </td>
                                    <td>
                                        <%if (helpdeskDetailInfoBean.isAccountExpired()) { %><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
                                    </td>
                                </tr>
                                <% } %>
                                <% if (viewStatusFields.contains(ViewStatusFields.AccountExpirationTime)) { %>
                                <tr>
                                    <td class="key">
                                        <pwm:display key="Field_AccountExpirationTime"/>
                                    </td>
                                    <% if (searchedUserInfo.getAccountExpirationTime() == null) { %>
                                    <td>
                                        <pwm:display key="Value_NotApplicable"/>
                                    </td>
                                    <% } else { %>
                                    <td class="timestamp">
                                        <%= dateFormatter.format(searchedUserInfo.getAccountExpirationTime()) %>
                                    </td>
                                    <% } %>
                                    </td>
                                </tr>
                                <% } %>
                                <% if (viewStatusFields.contains(ViewStatusFields.LastLoginTime)) { %>
                                <tr>
                                    <td class="key">
                                        <pwm:display key="Field_LastLoginTime"/>
                                    </td>
                                    <% if (helpdeskDetailInfoBean.getLastLoginTime() == null) { %>
                                    <td>
                                        <pwm:display key="Value_NotApplicable"/>
                                    </td>
                                    <% } else { %>
                                    <td class="timestamp">
                                        <%= dateFormatter.format(helpdeskDetailInfoBean.getLastLoginTime()) %>
                                    </td>
                                    <% } %>
                                </tr>
                                <% } %>
                                <% if (viewStatusFields.contains(ViewStatusFields.LastLoginTimeDelta)) { %>
                                <% if (helpdeskDetailInfoBean.getLastLoginTime() != null) { %>
                                <tr>
                                    <td class="key">
                                        <pwm:display key="Field_LastLoginTimeDelta"/>
                                    </td>
                                    <td>
                                        <%= TimeDuration.fromCurrent(helpdeskDetailInfoBean.getLastLoginTime()).asLongString(pwmSession.getSessionStateBean().getLocale()) %>
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
                                        <%= helpdeskDetailInfoBean.getPasswordSetDelta() %>
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
                                    <% if (helpdeskDetailInfoBean.isIntruderLocked()) { %>
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
                                        <% if (responseInfoBean != null) { %>
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
                                        <% if (searchedUserInfo.isRequiresResponseConfig()) { %>
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
                                <pwm:if test="<%=PwmIfTest.otpEnabled%>">
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
                        <% if (helpdeskDetailInfoBean.getUserHistory() != null && !helpdeskDetailInfoBean.getUserHistory().isEmpty()) { %>
                        <div id="Title_UserEventHistory" data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_UserEventHistory"/>" class="tabContent">
                            <div style="max-height: 400px; overflow: auto;">
                                <table class="nomargin">
                                    <% for (final UserAuditRecord record : helpdeskDetailInfoBean.getUserHistory()) { %>
                                    <tr>
                                        <td class="key timestamp" style="width:50%">
                                            <%= dateFormatter.format(record.getTimestamp()) %>
                                        </td>
                                        <td>
                                            <%= record.getEventCode().getLocalizedString(pwmRequest.getConfig(), pwmRequest.getLocale()) %>
                                            <%= record.getMessage() != null && record.getMessage().length() > 1 ? " (" + record.getMessage() + ") " : "" %>
                                        </td>
                                    </tr>
                                    <% } %>
                                </table>
                            </div>
                        </div>
                        <% } %>
                        <div id="Title_PasswordPolicy" data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_PasswordPolicy"/>" class="tabContent">
                            <div style="max-height: 400px; overflow: auto;">
                                <table class="nomargin">
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
                                                    ? JspUtility.getMessage(pageContext, Display.Value_NotApplicable)
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
                                                    MacroMachine macroMachine = JspUtility.getPwmSession(pageContext).getSessionManager().getMacroMachine(ContextManager.getPwmApplication(session));
                                                    final List<String> requirementLines = PasswordRequirementsTag.getPasswordRequirementsStrings(searchedUserInfo.getPasswordPolicy(), ContextManager.getPwmApplication(session).getConfig(), pwmSession.getSessionStateBean().getLocale(), macroMachine); %>
                                                <% for (final String requirementLine : requirementLines) { %>
                                                <li><%=requirementLine%>
                                                </li>
                                                <% } %>
                                            </ul>
                                        </td>
                                    </tr>
                                </table>
                                <table class="nomargin">
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
                        <div id="Title_SecurityResponses" data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_SecurityResponses"/>" class="tabContent">
                            <table class="nomargin">
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
                    <br/>
                    <div class="footnote"><div class="timestamp"><%=PwmConstants.DEFAULT_DATETIME_FORMAT.format(new Date())%></div></div>
                </td>
                <td style="border:0; width: 200px; max-width:200px; text-align: left; vertical-align: top">
                    <div style="border:0; margin-top: 25px; margin-left: 5px">
                        <button name="button_continue" class="helpdesk-detail-btn btn" id="button_continue" autofocus>
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                            <pwm:display key="Button_GoBack"/>
                        </button>
                        <button name="button_refresh" class="helpdesk-detail-btn btn" id="button_refresh">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh"></span></pwm:if>
                            <pwm:display key="Display_CaptchaRefresh"/>
                        </button>
                        <br/><br/>
                        <% if (SETTING_PW_UI_MODE != HelpdeskUIMode.none) { %>
                        <button class="helpdesk-detail-btn btn" id="helpdesk_ChangePasswordButton">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-key"></span></pwm:if>
                            <pwm:display key="Button_ChangePassword"/>
                        </button>
                        <% } %>
                        <% if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_ENABLE_UNLOCK)) { %>
                        <% if (helpdeskDetailInfoBean.isIntruderLocked()) { %>
                        <button id="helpdesk_unlockBtn" class="helpdesk-detail-btn btn">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-unlock"></span></pwm:if>
                            <pwm:display key="Button_Unlock"/>
                        </button>
                        <% } else { %>
                        <button id="helpdesk_unlockBtn" class="helpdesk-detail-btn btn" disabled="disabled">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-unlock"></span></pwm:if>
                            <pwm:display key="Button_Unlock"/>
                        </button>
                        <% } %>
                        <% } %>
                        <% if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_RESPONSES_BUTTON)) { %>
                        <% if (responseInfoBean != null) { %>
                        <button id="helpdesk_clearResponsesBtn" class="helpdesk-detail-btn btn">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-eraser"></span></pwm:if>
                            <pwm:display key="Button_ClearResponses"/>
                        </button>
                        <% } else { %>
                        <button id="helpdesk_clearResponsesBtn" class="helpdesk-detail-btn btn" disabled="disabled">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-eraser"></span></pwm:if>
                            <pwm:display key="Button_ClearResponses"/>
                        </button>
                        <pwm:script>
                            <script type="text/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    PWM_MAIN.showTooltip({
                                        id: "helpdesk_clearResponsesBtn",
                                        text: 'User does not have responses'
                                    });
                                });
                            </script>
                        </pwm:script>
                        <% } %>
                        <% } %>
                        <% if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_CLEAR_OTP_BUTTON) && pwmRequest.getConfig().readSettingAsBoolean(PwmSetting.OTP_ENABLED)) { %>
                        <% if (hasOtp) { %>
                        <button id="helpdesk_clearOtpSecretBtn" class="helpdesk-detail-btn btn">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-eraser"></span></pwm:if>
                            <pwm:display key="Button_HelpdeskClearOtpSecret"/>
                        </button>
                        <% } else { %>
                        <button id="helpdesk_clearOtpSecretBtn" class="helpdesk-detail-btn btn" disabled="disabled">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-eraser"></span></pwm:if>
                            <pwm:display key="Button_HelpdeskClearOtpSecret"/>
                        </button>
                        <% } %>
                        <% } %>
                        <% if ((Boolean)JspUtility.getPwmRequest(pageContext).getAttribute(PwmRequest.Attribute.HelpdeskVerificationEnabled) == true) { %>
                        <button id="sendTokenButton" class="helpdesk-detail-btn btn">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-mobile-phone"></span></pwm:if>
                            <pwm:display key="Button_Verify"/>
                        </button>
                        <% } %>
                        <% if (helpdeskProfile.readSettingAsBoolean(PwmSetting.HELPDESK_DELETE_USER_BUTTON)) { %>
                        <button class="helpdesk-detail-btn btn" id="helpdesk_deleteUserButton">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-user-times"></span></pwm:if>
                            <pwm:display key="Button_Delete"/>
                        </button>
                        <% } %>
                        <% final List<ActionConfiguration> actions = helpdeskProfile.readSettingAsAction(PwmSetting.HELPDESK_ACTIONS); %>
                        <% for (final ActionConfiguration loopAction : actions) { %>
                        <button class="helpdesk-detail-btn btn" name="action-<%=loopAction.getName()%>" id="action-<%=loopAction.getName()%>">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-location-arrow"></span></pwm:if>
                            <%=StringUtil.escapeHtml(loopAction.getName())%>
                        </button>
                        <pwm:script>
                            <script type="text/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    PWM_MAIN.addEventHandler('action-<%=loopAction.getName()%>','click',function(){
                                        PWM_HELPDESK.executeAction('<%=StringUtil.escapeJS(loopAction.getName())%>');
                                    });
                                    PWM_MAIN.showTooltip({
                                        id: "action-<%=loopAction.getName()%>",
                                        position: 'above',
                                        text: '<%=StringUtil.escapeJS(loopAction.getDescription())%>'
                                    });
                                });
                            </script>
                        </pwm:script>
                        <% } %>
                    </div>
                </td>
            </tr>
        </table>
    </div>
    <div class="push"></div>
</div>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
<pwm:script-ref url="/public/resources/js/helpdesk.js"/>
<pwm:script-ref url="/public/resources/js/changepassword.js"/>
</body>
</html>
