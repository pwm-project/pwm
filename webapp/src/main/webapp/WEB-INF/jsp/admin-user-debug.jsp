<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2021 The PWM Project
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
--%>
<%--
       THIS FILE IS NOT INTENDED FOR END USER MODIFICATION.
       See the README.TXT file in WEB-INF/jsp before making changes.
--%>


<%@ page import="com.novell.ldapchai.cr.Challenge" %>
<%@ page import="password.pwm.Permission" %>
<%@ page import="password.pwm.bean.ResponseInfoBean" %>
<%@ page import="password.pwm.bean.pub.PublicUserInfoBean" %>
<%@ page import="password.pwm.config.profile.ChallengeProfile" %>
<%@ page import="password.pwm.config.profile.ProfileDefinition" %>
<%@ page import="password.pwm.config.profile.PwmPasswordPolicy" %>
<%@ page import="password.pwm.config.profile.PwmPasswordRule" %>
<%@ page import="password.pwm.http.servlet.admin.domain.UserDebugDataBean" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="java.util.Map" %>
<%@ page import="password.pwm.util.java.TimeDuration" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.svc.PwmService" %>
<%@ page import="password.pwm.http.servlet.admin.domain.DomainAdminUserDebugServlet" %>
<%@ page import="password.pwm.user.UserInfo" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="User Debug"/>
    </jsp:include>
    <div id="centerbody" class="wide">
        <h1 id="page-content-title"><pwm:display key="Title_UserDebug" bundle="Admin"/></h1>
        <%@ include file="fragment/admin-modular-nav.jsp" %>

        <% final UserDebugDataBean userDebugDataBean = (UserDebugDataBean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.UserDebugData); %>
        <% final UserInfo userInfo = (UserInfo)JspUtility.getAttribute(pageContext, PwmRequestAttribute.UserDebugInfo); %>
        <% if (userDebugDataBean == null) { %>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <div id="panel-searchbar" class="searchbar">
            <form method="post" class="pwm-form" action="<pwm:current-url/>?processAction=<%=DomainAdminUserDebugServlet.AdminUserDebugAction.searchUsername.toString()%>">
                <input id="username" name="username" placeholder="<pwm:display key="Placeholder_Search"/>" title="<pwm:display key="Placeholder_Search"/>" class="helpdesk-input-username" <pwm:autofocus/> autocomplete="off"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                <button type="submit" class="btn"><pwm:display key="Button_Search"/></button>
            </form>
        </div>

        <% } else { %>
        <div class="buttonbar center">
            <form method="get" class="pwm-form">
                <button type="submit" class="btn"><pwm:display key="Button_Reset"/></button>
            </form>
        </div>
        <% final PublicUserInfoBean publicUserInfoBean = userDebugDataBean.publicUserInfoBean(); %>
        <% if (publicUserInfoBean != null) { %>
        <table>
            <tr>
                <td colspan="10" class="title">Identity</td>
            </tr>
            <tr>
                <td class="key">UserDN</td>
                <td><%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getUserDN())%></td>
            </tr>
            <tr>
                <td class="key">Ldap Profile</td>
                <td><%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getLdapProfile())%></td>
            </tr>
            <tr>
                <td class="key">Username</td>
                <td><%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getUserID())%></td>
            </tr>
            <tr>
                <td class="key"><%=PwmConstants.PWM_APP_NAME%> GUID</td>
                <td><%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getUserGUID())%></td>
            </tr>
        </table>
        <br/>
        <table>
            <tr>
                <td colspan="10" class="title">Status</td>
            </tr>
            <tr>
                <td class="key">Last Login Time</td>
                <td>
                    <%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getLastLoginTime())%>
                    <% if ( publicUserInfoBean.getLastLoginTime() != null ) { %>
                    ( <%=TimeDuration.fromCurrent(publicUserInfoBean.getLastLoginTime()).asCompactString()%> )
                    <% } %>
                </td>
            </tr>
            <tr>
                <td class="key">Account Expiration Time</td>
                <td>
                    <%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getAccountExpirationTime())%>
                    <% if ( publicUserInfoBean.getAccountExpirationTime() != null ) { %>
                    ( <%=TimeDuration.fromCurrent(publicUserInfoBean.getAccountExpirationTime()).asCompactString()%> )
                    <% } %>
                </td>
            </tr>
            <tr>
                <td class="key">Password Expiration</td>
                <td>
                    <%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getPasswordExpirationTime())%>
                    <% if ( publicUserInfoBean.getPasswordExpirationTime() != null ) { %>
                    ( <%=TimeDuration.fromCurrent(publicUserInfoBean.getPasswordExpirationTime()).asCompactString()%> )
                    <% } %>
                </td>
            </tr>
            <tr>
                <td class="key">Password Last Modified</td>
                <td>
                    <%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getPasswordLastModifiedTime())%>
                    <% if ( publicUserInfoBean.getPasswordLastModifiedTime() != null ) { %>
                    ( <%=TimeDuration.fromCurrent(publicUserInfoBean.getPasswordLastModifiedTime()).asCompactString()%> )
                    <% } %>
                </td>
            </tr>
            <tr>
                <td class="key">Email Address 1</td>
                <td><%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getUserEmailAddress())%></td>
            </tr>
            <tr>
                <td class="key">Email Address 2</td>
                <td><%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getUserEmailAddress2())%></td>
            </tr>
            <tr>
                <td class="key">Email Address 3</td>
                <td><%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getUserEmailAddress3())%></td>
            </tr>
            <tr>
                <td class="key">Phone Number 1</td>
                <td><%=JspUtility.friendlyWrite(pageContext, userInfo.getUserSmsNumber())%></td>
            </tr>
            <tr>
                <td class="key">Phone Number 2</td>
                <td><%=JspUtility.friendlyWrite(pageContext, userInfo.getUserSmsNumber2())%></td>
            </tr>
            <tr>
                <td class="key">Phone Number 3</td>
                <td><%=JspUtility.friendlyWrite(pageContext, userInfo.getUserSmsNumber3())%></td>
            </tr>
            <tr>
                <td class="key">Username</td>
                <td><%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getUserID())%></td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_PasswordExpired"/>
                </td>
                <td id="PasswordExpired">
                    <%= JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getPasswordStatus().expired()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_PasswordPreExpired"/>
                </td>
                <td id="PasswordPreExpired">
                    <%= JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getPasswordStatus().preExpired()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_PasswordWithinWarningPeriod"/>
                </td>
                <td id="PasswordWithinWarningPeriod">
                    <%= JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getPasswordStatus().warnPeriod()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_PasswordViolatesPolicy"/>
                </td>
                <td id="PasswordViolatesPolicy">
                    <%= JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getPasswordStatus().violatesPolicy()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Password Readable From LDAP
                </td>
                <td>
                    <%= JspUtility.friendlyWrite(pageContext, userDebugDataBean.passwordReadable()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Requires New Password
                </td>
                <td>
                    <%= JspUtility.friendlyWrite(pageContext, publicUserInfoBean.isRequiresNewPassword()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Requires Response Setup
                </td>
                <td>
                    <%= JspUtility.friendlyWrite(pageContext, publicUserInfoBean.isRequiresResponseConfig()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Requires OTP Setup
                </td>
                <td>
                    <%= JspUtility.friendlyWrite(pageContext, publicUserInfoBean.isRequiresOtpConfig()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Requires Profile Update
                </td>
                <td>
                    <%= JspUtility.friendlyWrite(pageContext, publicUserInfoBean.isRequiresUpdateProfile()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Password is Within Minimum Lifetime
                </td>
                <td>
                    <%= JspUtility.friendlyWrite(pageContext, userDebugDataBean.passwordWithinMinimumLifetime()) %>
                </td>
            </tr>
            <tr>
                <td class="key">Stored Language</td>
                <td><%=JspUtility.friendlyWrite(pageContext, publicUserInfoBean.getLanguage() )%></td>
            </tr>
        </table>
        <br/>

        <% if (JspUtility.getPwmRequest( pageContext ).getDomainConfig().readSettingAsBoolean( PwmSetting.PW_EXPY_NOTIFY_ENABLE ) ) { %>
        <table>
            <tr>
                <td colspan="10" class="title">Password Notification Status</td>
            </tr>
            <% if ( userDebugDataBean.pwNotifyUserStatus() == null ) { %>
            <tr>
                <td class="key">Last Notification Sent</td>
                <td><pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/></td>
            </tr>
            <% } else { %>
            <tr>
                <td class="key">Last Notification Sent</td>
                <td>
                    <%=JspUtility.friendlyWrite(pageContext, userDebugDataBean.pwNotifyUserStatus().getLastNotice())%>
                </td>
            </tr>
            <tr>
                <td class="key">Last Notification Password Expiration Time</td>
                <td>
                    <%=JspUtility.friendlyWrite(pageContext, userDebugDataBean.pwNotifyUserStatus().getExpireTime())%>
                </td>
            </tr>
            <tr>
                <td class="key">Last Notification Interval</td>
                <td>
                    <%=userDebugDataBean.pwNotifyUserStatus().getInterval()%>
                </td>
            </tr>
            <% } %>
        </table>
        <br/>
        <% } %>

        <table>
            <tr>
                <td colspan="10" class="title">Applied Configuration</td>
            </tr>
            <tr>
                <td class="key">Profiles</td>
                <td>
                    <table>
                        <thead>
                        <tr>
                            <td class="key">Service</td>
                            <td class="key">ProfileID</td>
                        </tr>
                        </thead>
                        <tbody>
                        <% for (final ProfileDefinition profileDefinition : userDebugDataBean.profiles().keySet()) { %>
                        <tr>
                            <td><%=profileDefinition%></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, userDebugDataBean.profiles().get(profileDefinition).stringValue())%></td>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="key">Permissions</td>
                <td>
                    <table>
                        <thead>
                        <tr>
                            <td class="key">Permission</td>
                            <td class="key">Status</td>
                        </tr>
                        </thead>
                        <tbody>
                        <% for (final Permission permission : userDebugDataBean.permissions().keySet()) { %>
                        <tr>
                            <td><%=permission%></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, userDebugDataBean.permissions().get(permission))%></td>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                </td>
            </tr>
        </table>
        <br/>
        <table>
            <tr>
                <td colspan="10" class="title">Password Policy</td>
            </tr>
            <% PwmPasswordPolicy userPolicy = userInfo.getPasswordPolicy(); %>
            <% if (userPolicy != null) { %>
            <% PwmPasswordPolicy configPolicy = userDebugDataBean.configuredPasswordPolicy(); %>
            <% PwmPasswordPolicy ldapPolicy = userDebugDataBean.ldapPasswordPolicy(); %>
            <tr>
                <td colspan="10">
                    <table>
                        <thead>
                        <tr class="title">
                            <td class="key" style="width: 1px;">Rule</td>
                            <td class="key" style="width: 1px;">Rule Type</td>
                            <td class="key" style="width: 20%;">Configured <%=PwmConstants.PWM_APP_NAME%> Policy</td>
                            <td class="key" style="width: 20%;">LDAP Policy</td>
                            <td class="key" style="width: 20%;">Effective Policy</td>
                        </tr>
                        <tr>
                            <td class="key">ID</td>
                            <td><pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, () -> configPolicy.getId().stringValue())%></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, () -> ldapPolicy.getId().stringValue())%></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, () -> userPolicy.getId().stringValue())%></td>
                        </tr>
                        <tr>
                            <td class="key">Display Name</td>
                            <td><pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, configPolicy.getDisplayName(JspUtility.locale(request)))%></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, ldapPolicy.getDisplayName(JspUtility.locale(request)))%></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, userPolicy.getDisplayName(JspUtility.locale(request)))%></td>
                        </tr>
                        </thead>
                        <tbody>
                        <% for (final PwmPasswordRule rule : PwmPasswordRule.sortedByLabel(JspUtility.locale(request), JspUtility.getPwmRequest(pageContext).getDomainConfig())) { %>
                        <tr>
                            <td class="key"><span title="<%=rule.getKey()%>"><%=rule.getLabel(JspUtility.locale(request), JspUtility.getPwmRequest(pageContext).getDomainConfig())%></span></td>
                            <td><%=rule.getRuleType()%></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, configPolicy.getValue(rule))%></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, ldapPolicy.getValue(rule))%></td>
                            <td><%=JspUtility.friendlyWrite(pageContext, userPolicy.getValue(rule))%></td>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                </td>
            </tr>
            <% } %>
        </table>
        <br/>
        <table>
            <tr>
                <td colspan="10" class="title">Stored Responses</td>
            </tr>
            <% final ResponseInfoBean responseInfoBean = userDebugDataBean.responseInfoBean(); %>
            <% if (responseInfoBean == null) { %>
            <tr>
                <td class="key">Stored Responses</td>
                <td><pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/></td>
            </tr>
            <% } else { %>
            <tr>
                <td class="key">Identifier</td>
                <td><%=responseInfoBean.getCsIdentifier()%></td>
            </tr>
            <tr>
                <td class="key">Storage Type</td>
                <td><%=responseInfoBean.getDataStorageMethod()%></td>
            </tr>
            <tr>
                <td class="key">Format</td>
                <td><%=responseInfoBean.getFormatType()%></td>
            </tr>
            <tr>
                <td class="key">Locale</td>
                <td><%=responseInfoBean.getLocale()%></td>
            </tr>
            <tr>
                <td class="key">Storage Timestamp</td>
                <td><%=JspUtility.friendlyWrite(pageContext, responseInfoBean.getTimestamp())%></td>
            </tr>
            <tr>
                <td class="key">Answered Challenges</td>
                <% final Map<Challenge,String> crMap = responseInfoBean.getCrMap(); %>
                <% if (crMap == null) { %>
                <td>
                    n/a
                </td>
                <% } else { %>
                <td>
                    <table>
                        <thead>
                        <tr>
                            <td class="key">Type</td>
                            <td class="key">Required</td>
                            <td class="key">Text</td>
                        </tr>
                        </thead>
                        <tbody>
                        <% for (final Challenge challenge : crMap.keySet()) { %>
                        <tr>
                            <td>
                                <%= challenge.isAdminDefined() ? "Admin Defined" : "User Defined" %>
                            </td>
                            <td>
                                <%= JspUtility.friendlyWrite(pageContext, challenge.isRequired())%>
                            </td>
                            <td>
                                <%= JspUtility.friendlyWrite(pageContext, challenge.getChallengeText())%>
                            </td>
                        </tr>
                        <% } %>
                        </tbody>
                    </table>
                </td>
                <% } %>
            </tr>
            <tr>
                <td class="key">
                    Minimum Randoms Required
                </td>
                <td>
                    <%=responseInfoBean.getMinRandoms()%>
                </td>
            </tr>
            <tr>
                <td class="key">Helpdesk Answered Challenges</td>
                <% final Map<Challenge,String> helpdeskCrMap = responseInfoBean.getHelpdeskCrMap(); %>
                <% if (helpdeskCrMap == null) { %>
                <td>
                    <pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/>
                </td>
                <% } else { %>
                <td>
                    <% for (final Challenge challenge : helpdeskCrMap.keySet()) { %>
                    <%= JspUtility.friendlyWrite(pageContext, challenge.getChallengeText())%><br/>
                    <% } %>
                </td>
                <% } %>
            </tr>
            <% } %>
        </table>
        <br/>
        <% { %><%-- Begin Challenge Profile --%>
        <table>
            <tr>
                <td colspan="10" class="title">Challenge Profile</td>
            </tr>
            <% final ChallengeProfile challengeProfile = userInfo.getChallengeProfile(); %>
            <% if ( challengeProfile == null ) { %>
            <tr>
                <td class="key">Assigned Profile</td>
                <td><pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/></td>
            </tr>
            <% } else { %>
            <tr>
                <td class="key">Display Name</td>
                <td><%=challengeProfile.getDisplayName(JspUtility.locale(request))%></td>
            </tr>
            <tr>
                <td class="key">Identifier</td>
                <td><%=challengeProfile.getId()%></td>
            </tr>
            <tr>
                <td class="key">Locale</td>
                <td><%=challengeProfile.getLocale()%></td>
            </tr>
            <tr>
                <td class="key">Challenges</td>
                <td>
                    <table>
                        <tr>
                            <td class="key">Type</td>
                            <td class="key">Text</td>
                            <td class="key">Required</td>
                            <td class="key">Min Length</td>
                            <td class="key">Max Length</td>
                            <td class="key">Enforce Wordlist</td>
                            <td class="key">Max Question Characters</td>
                        </tr>
                        <% if ( challengeProfile.hasChallenges() ) { %>
                        <% for (final Challenge challenge : challengeProfile.getChallengeSet().get().getChallenges()) { %>
                        <tr>
                            <td>
                                <%= challenge.isAdminDefined() ? "Admin Defined" : "User Defined" %>
                            </td>
                            <td>
                                <%= JspUtility.friendlyWrite(pageContext, challenge.getChallengeText())%>
                            </td>
                            <td>
                                <%= JspUtility.friendlyWrite(pageContext, challenge.isRequired())%>
                            </td>
                            <td>
                                <%= challenge.getMinLength() %>
                            </td>
                            <td>
                                <%= challenge.getMaxLength() %>
                            </td>
                            <td>
                                <%= JspUtility.friendlyWrite(pageContext, challenge.isEnforceWordlist())%>
                            </td>
                            <td>
                                <%= challenge.getMaxQuestionCharsInAnswer() %>
                            </td>
                        </tr>
                        <% } %>
                        <% } %>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="key">Helpdesk Challenges</td>
                <td>
                    <table>
                        <thead>
                        <tr>
                            <td class="key">Type</td>
                            <td class="key">Text</td>
                            <td class="key">Required</td>
                            <td class="key">Min Length</td>
                            <td class="key">Max Length</td>
                            <td class="key">Enforce Wordlist</td>
                            <td class="key">Max Question Characters</td>
                        </tr>
                        </thead>
                        <tbody>
                        <% if ( challengeProfile.hasHelpdeskChallenges() ) { %>
                        <% for (final Challenge challenge : challengeProfile.getHelpdeskChallengeSet().get().getChallenges()) { %>
                        <tr>
                            <td>
                                <%= challenge.isAdminDefined() ? "Admin Defined" : "User Defined" %>
                            </td>
                            <td>
                                <%= JspUtility.friendlyWrite(pageContext, challenge.getChallengeText())%>
                            </td>
                            <td>
                                <%= JspUtility.friendlyWrite(pageContext, challenge.isRequired())%>
                            </td>
                            <td>
                                <%= challenge.getMinLength() %>
                            </td>
                            <td>
                                <%= challenge.getMaxLength() %>
                            </td>
                            <td>
                                <%= JspUtility.friendlyWrite(pageContext, challenge.isEnforceWordlist())%>
                            </td>
                            <td>
                                <%= challenge.getMaxQuestionCharsInAnswer() %>
                            </td>
                        </tr>
                        <% } %>
                        <% } %>
                        </tbody>
                    </table>
                </td>
            </tr>
            <% } %>
        </table>
        <% } %><%-- End Challenge Profile --%>
        <% } %>
        </tr>
        <div class="buttonbar center">
            <form method="get">
                <input type="hidden" name="processAction" value="<%=DomainAdminUserDebugServlet.AdminUserDebugAction.downloadUserDebug.toString()%>"/>
                <button type="submit" class="btn">Download</button>
            </form>
        </div>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
