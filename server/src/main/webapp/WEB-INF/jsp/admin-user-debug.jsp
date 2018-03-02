<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2018 The PWM Project
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
<%@ page import="password.pwm.Permission" %>
<%@ page import="password.pwm.bean.ResponseInfoBean" %>
<%@ page import="password.pwm.bean.pub.PublicUserInfoBean" %>
<%@ page import="password.pwm.config.profile.ChallengeProfile" %>
<%@ page import="password.pwm.config.profile.ProfileType" %>
<%@ page import="password.pwm.config.profile.PwmPasswordPolicy" %>
<%@ page import="password.pwm.config.profile.PwmPasswordRule" %>
<%@ page import="password.pwm.http.servlet.admin.UserDebugDataBean" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="java.util.Map" %>
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
        <h1 id="page-content-title">User Debug</h1>
        <%@ include file="fragment/admin-nav.jsp" %>

        <% final UserDebugDataBean userDebugDataBean = (UserDebugDataBean)JspUtility.getAttribute(pageContext, PwmRequestAttribute.UserDebugData); %>
        <% if (userDebugDataBean == null) { %>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <div id="panel-searchbar" class="searchbar">
            <form method="post" class="pwm-form">
                <input id="username" name="username" placeholder="<pwm:display key="Placeholder_Search"/>" title="<pwm:display key="Placeholder_Search"/>" class="helpdesk-input-username" <pwm:autofocus/> autocomplete="off"/>
                <input type="hidden" id="pwmFormID" name="pwmFormID" value="<pwm:FormID/>"/>
                <button type="submit" class="btn"><pwm:display key="Button_Search"/></button>
            </form>
        </div>

        <% } else { %>
        <div class="buttonbar">
            <form method="get" class="pwm-form">
                <button type="submit" class="btn"><pwm:display key="Button_Continue"/></button>
            </form>
        </div>
        <div class="buttonbar">
            <form method="get">
                <input type="hidden" name="processAction" value="<%=AdminServlet.AdminAction.downloadUserDebug.toString()%>"/>
                <button type="submit" class="btn">Download</button>
            </form>
        </div>
        <% final PublicUserInfoBean userInfo = userDebugDataBean.getPublicUserInfoBean(); %>
        <% if (userInfo != null) { %>
        <table>
            <tr>
                <td colspan="10" class="title">Identity</td>
            </tr>
            <tr>
                <td class="key">UserDN</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getUserDN())%></td>
            </tr>
            <tr>
                <td class="key">Ldap Profile</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getLdapProfile())%></td>
            </tr>
            <tr>
                <td class="key">Username</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getUserID())%></td>
            </tr>
            <tr>
                <td class="key"><%=PwmConstants.PWM_APP_NAME%> GUID</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getUserGUID())%></td>
            </tr>
        </table>
        <br/>
        <table>
            <tr>
                <td colspan="10" class="title">Status</td>
            </tr>
            <tr>
                <td class="key">Last Login Time</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getPasswordLastModifiedTime())%></td>
            </tr>
            <tr>
                <td class="key">Account Expiration Time</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getAccountExpirationTime())%></td>
            </tr>
            <tr>
                <td class="key">Password Expiration</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getPasswordExpirationTime())%></td>
            </tr>
            <tr>
                <td class="key">Password Last Modified</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getPasswordLastModifiedTime())%></td>
            </tr>
            <tr>
                <td class="key">Email Address</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getUserEmailAddress())%></td>
            </tr>
            <tr>
                <td class="key">Phone Number</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userDebugDataBean.getUserInfo().getUserSmsNumber())%></td>
            </tr>
            <tr>
                <td class="key">Username</td>
                <td><%=JspUtility.freindlyWrite(pageContext, userInfo.getUserID())%></td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_PasswordExpired"/>
                </td>
                <td id="PasswordExpired">
                    <%= JspUtility.freindlyWrite(pageContext, userInfo.getPasswordStatus().isExpired()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_PasswordPreExpired"/>
                </td>
                <td id="PasswordPreExpired">
                    <%= JspUtility.freindlyWrite(pageContext, userInfo.getPasswordStatus().isPreExpired()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_PasswordWithinWarningPeriod"/>
                </td>
                <td id="PasswordWithinWarningPeriod">
                    <%= JspUtility.freindlyWrite(pageContext, userInfo.getPasswordStatus().isWarnPeriod()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <pwm:display key="Field_PasswordViolatesPolicy"/>
                </td>
                <td id="PasswordViolatesPolicy">
                    <%= JspUtility.freindlyWrite(pageContext, userInfo.getPasswordStatus().isViolatesPolicy()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Password Readable From LDAP
                </td>
                <td>
                    <%= JspUtility.freindlyWrite(pageContext, userDebugDataBean.isPasswordReadable()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Requires New Password
                </td>
                <td>
                    <%= JspUtility.freindlyWrite(pageContext, userInfo.isRequiresNewPassword()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Requires Response Setup
                </td>
                <td>
                    <%= JspUtility.freindlyWrite(pageContext, userInfo.isRequiresResponseConfig()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Requires OTP Setup
                </td>
                <td>
                    <%= JspUtility.freindlyWrite(pageContext, userInfo.isRequiresOtpConfig()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Requires Profile Update
                </td>
                <td>
                    <%= JspUtility.freindlyWrite(pageContext, userInfo.isRequiresUpdateProfile()) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    Password is Within Minimum Lifetime
                </td>
                <td>
                    <%= JspUtility.freindlyWrite(pageContext, userDebugDataBean.isPasswordWithinMinimumLifetime()) %>
                </td>
            </tr>
        </table>
        <br/>
        <table>
            <tr>
                <td colspan="10" class="title">Applied Configuration</td>
            </tr>
            <tr>
                <td class="key">Profiles</td>
                <td>
                    <table>
                        <tr>
                            <td class="key">Service</td>
                            <td class="key">ProfileID</td>
                        </tr>
                        <% for (final ProfileType profileType : userDebugDataBean.getProfiles().keySet()) { %>
                        <tr>
                            <td><%=profileType%></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, userDebugDataBean.getProfiles().get(profileType))%></td>
                        </tr>
                        <% } %>
                    </table>
                </td>
            </tr>
            <tr>
                <td class="key">Permissions</td>
                <td>
                    <table>
                        <tr>
                            <td class="key">Permission</td>
                            <td class="key">Status</td>
                        </tr>
                        <% for (final Permission permission : userDebugDataBean.getPermissions().keySet()) { %>
                        <tr>
                            <td><%=permission%></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, userDebugDataBean.getPermissions().get(permission))%></td>
                        </tr>
                        <% } %>
                    </table>
                </td>
            </tr>
        </table>
        <br/>
        <table>
            <tr>
                <td colspan="10" class="title">Password Policy</td>
            </tr>
            <% PwmPasswordPolicy userPolicy = userDebugDataBean.getUserInfo().getPasswordPolicy(); %>
            <% if (userPolicy != null) { %>
            <% PwmPasswordPolicy configPolicy = userDebugDataBean.getConfiguredPasswordPolicy(); %>
            <% PwmPasswordPolicy ldapPolicy = userDebugDataBean.getLdapPasswordPolicy(); %>
            <tr>
                <td colspan="10">
                    <table>
                        <tr class="title">
                            <td class="key" style="width: 1px;">Rule</td>
                            <td class="key" style="width: 1px;">Rule Type</td>
                            <td class="key" style="width: 20%;">Configured <%=PwmConstants.PWM_APP_NAME%> Policy</td>
                            <td class="key" style="width: 20%;">LDAP Policy</td>
                            <td class="key" style="width: 20%;">Effective Policy</td>
                        </tr>
                        <tr>
                            <td>ID</td>
                            <td><pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, configPolicy.getIdentifier())%></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, ldapPolicy.getIdentifier())%></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, userPolicy.getIdentifier())%></td>
                        </tr>
                        <tr>
                            <td>Display Name</td>
                            <td><pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, configPolicy.getDisplayName(JspUtility.locale(request)))%></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, ldapPolicy.getDisplayName(JspUtility.locale(request)))%></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, userPolicy.getDisplayName(JspUtility.locale(request)))%></td>
                        </tr>
                        <% for (final PwmPasswordRule rule : PwmPasswordRule.values()) { %>
                        <tr>
                            <td><span title="<%=rule.getKey()%>"><%=rule.getLabel(JspUtility.locale(request), JspUtility.getPwmRequest(pageContext).getConfig())%></span></td>
                            <td><%=rule.getRuleType()%></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, configPolicy.getValue(rule))%></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, ldapPolicy.getValue(rule))%></td>
                            <td><%=JspUtility.freindlyWrite(pageContext, userPolicy.getValue(rule))%></td>
                        </tr>
                        <% } %>
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
            <% final ResponseInfoBean responseInfoBean = userDebugDataBean.getUserInfo().getResponseInfoBean(); %>
            <% if (responseInfoBean == null) { %>
            <tr>
                <td>Stored Responses</td>
                <td><pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/></td>
            </tr>
            <% } else { %>
            <tr>
                <td>Identifier</td>
                <td><%=responseInfoBean.getCsIdentifier()%></td>
            </tr>
            <tr>
                <td>Storage Type</td>
                <td><%=responseInfoBean.getDataStorageMethod()%></td>
            </tr>
            <tr>
                <td>Format</td>
                <td><%=responseInfoBean.getFormatType()%></td>
            </tr>
            <tr>
                <td>Locale</td>
                <td><%=responseInfoBean.getLocale()%></td>
            </tr>
            <tr>
                <td>Storage Timestamp</td>
                <td><%=JspUtility.freindlyWrite(pageContext, responseInfoBean.getTimestamp())%></td>
            </tr>
            <tr>
                <td>Answered Challenges</td>
                <% final Map<Challenge,String> crMap = responseInfoBean.getCrMap(); %>
                <% if (crMap == null) { %>
                <td>
                    n/a
                </td>
                <% } else { %>
                <td>
                    <table>
                        <tr>
                            <td class="key">Type</td>
                            <td class="key">Required</td>
                            <td class="key">Text</td>
                        </tr>
                        <% for (final Challenge challenge : crMap.keySet()) { %>
                        <tr>
                            <td>
                                <%= challenge.isAdminDefined() ? "Admin Defined" : "User Defined" %>
                            </td>
                            <td>
                                <%= JspUtility.freindlyWrite(pageContext, challenge.isRequired())%>
                            </td>
                            <td>
                                <%= JspUtility.freindlyWrite(pageContext, challenge.getChallengeText())%>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                </td>
                <% } %>
            </tr>
            <tr>
                <td>
                    Minimum Randoms Required
                </td>
                <td>
                    <%=responseInfoBean.getMinRandoms()%>
                </td>
            </tr>
            <tr>
                <td>Helpdesk Answered Challenges</td>
                <% final Map<Challenge,String> helpdeskCrMap = responseInfoBean.getHelpdeskCrMap(); %>
                <% if (helpdeskCrMap == null) { %>
                <td>
                <pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/>
                </td>
                <% } else { %>
                <td>
                    <% for (final Challenge challenge : helpdeskCrMap.keySet()) { %>
                    <%= JspUtility.freindlyWrite(pageContext, challenge.getChallengeText())%><br/>
                    <% } %>
                </td>
                <% } %>
            </tr>
            <% } %>
        </table>
        <br/>
        <table>
            <tr>
                <td colspan="10" class="title">Challenge Profile</td>
            </tr>
            <% final ChallengeProfile challengeProfile = userDebugDataBean.getUserInfo().getChallengeProfile(); %>
            <% if (challengeProfile == null) { %>
            <tr>
                <td>Assigned Profile</td>
                <td><pwm:display key="<%=Display.Value_NotApplicable.toString()%>"/></td>
            </tr>
            <% } else { %>
            <tr>
                <td>Display Name</td>
                <td><%=challengeProfile.getDisplayName(JspUtility.locale(request))%></td>
            </tr>
            <tr>
                <td>Identifier</td>
                <td><%=challengeProfile.getIdentifier()%></td>
            </tr>
            <tr>
                <td>Locale</td>
                <td><%=challengeProfile.getLocale()%></td>
            </tr>
            <tr>
                <td>Challenges</td>
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
                        <% for (final Challenge challenge : challengeProfile.getChallengeSet().getChallenges()) { %>
                        <tr>
                            <td>
                                <%= challenge.isAdminDefined() ? "Admin Defined" : "User Defined" %>
                            </td>
                            <td>
                                <%= JspUtility.freindlyWrite(pageContext, challenge.getChallengeText())%>
                            </td>
                            <td>
                                <%= JspUtility.freindlyWrite(pageContext, challenge.isRequired())%>
                            </td>
                            <td>
                                <%= challenge.getMinLength() %>
                            </td>
                            <td>
                                <%= challenge.getMaxLength() %>
                            </td>
                            <td>
                                <%= JspUtility.freindlyWrite(pageContext, challenge.isEnforceWordlist())%>
                            </td>
                            <td>
                                <%= challenge.getMaxQuestionCharsInAnswer() %>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                </td>
            </tr>
            <% } %>
        </table>

        <% } %>
        <div class="buttonbar">
            <form method="get" class="pwm-form">
                <button type="submit" class="btn"><pwm:display key="Button_Continue"/></button>
            </form>
        </div>
        <% } %>
    </div>
    <div class="push"></div>
</div>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>
