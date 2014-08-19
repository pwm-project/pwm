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

<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.ResponseInfoBean" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.config.option.ViewStatusFields" %>
<%@ page import="password.pwm.event.UserAuditRecord" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.Helper" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Set" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    final PwmApplication pwmApplication = ContextManager.getPwmApplication(request);
    final PwmSession pwmSession = PwmSession.getPwmSession(request);
    final UserInfoBean uiBean = pwmSession.getUserInfoBean();
    final SessionStateBean ssBean = pwmSession.getSessionStateBean();
    final DateFormat dateFormatter = PwmConstants.DEFAULT_DATETIME_FORMAT;
    final Set<ViewStatusFields> viewStatusFields = pwmApplication.getConfig().readSettingAsOptionList(PwmSetting.ACCOUNT_INFORMATION_VIEW_STATUS_VALUES,ViewStatusFields.class);
%>

<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper" class="nihilo">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_UserInformation"/>
</jsp:include>
<div id="centerbody">
<div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false">
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_UserInformation"/>">
    <table>
        <% if (viewStatusFields.contains(ViewStatusFields.Username)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_Username"/>
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(uiBean.getUsername()) %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.UserDN)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_UserDN"/>
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(uiBean.getUserIdentity().getUserDN()) %>
            </td>
        </tr>
        <% if (pwmApplication.getConfig().getLdapProfiles().size() > 1) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_LdapProfile"/>
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(pwmApplication.getConfig().getLdapProfiles().get(uiBean.getUserIdentity().getLdapProfileID()).getDisplayName(pwmSession.getSessionStateBean().getLocale())) %>
            </td>
        </tr>
        <% } %>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.UserEmail)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_UserEmail"/>
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(uiBean.getUserEmailAddress()) %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.GUID)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_UserGUID"/>
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(uiBean.getUserGuid()) %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.PasswordExpired)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_PasswordExpired"/>
            </td>
            <td>
                <%if (uiBean.getPasswordState().isExpired()) {%><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.PasswordPreExpired)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_PasswordPreExpired"/>
            </td>
            <td>
                <%if (uiBean.getPasswordState().isPreExpired()) {%><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.PasswordWarnPeriod)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_PasswordWithinWarningPeriod"/>
            </td>
            <td>
                <%if (uiBean.getPasswordState().isWarnPeriod()) { %><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.PasswordViolatesPolicy)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_PasswordViolatesPolicy"/>
            </td>
            <td>
                <% if (uiBean.getPasswordState().isViolatesPolicy()) {%><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.PasswordSetTime)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_PasswordSetTime"/>
            </td>
            <% if (uiBean.getPasswordLastModifiedTime() == null) { %>
            <td>
                <pwm:display key="Value_NotApplicable"/>
            </td>
            <% } else { %>
            <td class="timestamp">
                <%= dateFormatter.format(uiBean.getPasswordLastModifiedTime()) %>
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
                <%= uiBean.getPasswordLastModifiedTime() != null ? TimeDuration.fromCurrent(uiBean.getPasswordLastModifiedTime()).asLongString(ssBean.getLocale()) : Display.getLocalizedMessage(pwmSession.getSessionStateBean().getLocale(),"Value_NotApplicable",pwmApplicationHeader.getConfig())%>
            </td>
        </tr>
        <tr>
            <td class="key">
                <pwm:display key="Field_PasswordExpirationTime"/>
            </td>
            <% if (uiBean.getPasswordExpirationTime() == null) { %>
            <td>
                <pwm:display key="Value_NotApplicable"/>
            </td>
            <% } else { %>
            <td class="timestamp">
                <%= dateFormatter.format(uiBean.getPasswordExpirationTime()) %>
            </td>
            <% } %>
        </tr>
        <% } %>
        <% ResponseInfoBean responseInfoBean = pwmSession.getUserInfoBean().getResponseInfoBean(); %>
        <% if (viewStatusFields.contains(ViewStatusFields.ResponsesStored)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_ResponsesStored"/>
            </td>
            <td>
                <%if (!uiBean.isRequiresResponseConfig()) { %><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.ResponsesTimestamp)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_ResponsesTimestamp"/>
            </td>
            <% if (responseInfoBean == null || responseInfoBean.getTimestamp() == null ) { %>
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
                    <%if (uiBean.getOtpUserRecord() != null) {%><pwm:display key="Value_True"/><% } else { %><pwm:display key="Value_False"/><% } %>
                </td>
            </tr>
            <% } %>
            <% if (viewStatusFields.contains(ViewStatusFields.OTPTimestamp)) { %>
            <tr>
                <td class="key">
                    <pwm:display key="Field_OTP_Timestamp"/>
                </td>
                <% if (uiBean.getOtpUserRecord() == null || uiBean.getOtpUserRecord().getTimestamp() == null) { %>
                <td>
                    <pwm:display key="Value_NotApplicable"/>
                </td>
                <% } else { %>
                <td class="timestamp">
                    <%= dateFormatter.format(uiBean.getOtpUserRecord().getTimestamp()) %>
                </td>
                <% } %>
            </tr>
            <% } %>
        </pwm:if>
        <% if (viewStatusFields.contains(ViewStatusFields.NetworkAddress)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_NetworkAddress"/>
            </td>
            <td>
                <%= ssBean.getSrcAddress() %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.NetworkHost)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_NetworkHost"/>
            </td>
            <td>
                <%= ssBean.getSrcHostname() %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.LogoutURL)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_LogoutURL"/>
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(Helper.figureLogoutURL(pwmApplicationHeader, pwmSessionHeader)) %>
            </td>
        </tr>
        <% } %>
        <% if (viewStatusFields.contains(ViewStatusFields.ForwardURL)) { %>
        <tr>
            <td class="key">
                <pwm:display key="Field_ForwardURL"/>
            </td>
            <td>
                <%= StringEscapeUtils.escapeHtml(Helper.figureForwardURL(pwmApplicationHeader, pwmSessionHeader, request)) %>
            </td>
        </tr>
        <% } %>
    </table>
</div>
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_PasswordPolicy"/>">
    <div style="max-height: 400px; overflow: auto;">
        <table>
            <tr>
                <td class="key">
                    <pwm:display key="Title_PasswordPolicy"/>
                </td>
                <td>
                    <ul>
                        <pwm:DisplayPasswordRequirements separator="</li>" prepend="<li>"/>
                    </ul>
                </td>
            </tr>
        </table>
    </div>
</div>
<% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.ACCOUNT_INFORMATION_HISTORY)) { %>
<%
    List<UserAuditRecord> auditRecords = Collections.emptyList();
    try { auditRecords = pwmApplicationHeader.getAuditManager().readUserHistory(pwmSessionHeader);} catch (Exception e) {/*noop*/}
    final Locale userLocale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale();
%>
<% if (auditRecords != null && !auditRecords.isEmpty()) { %>
<div data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_UserEventHistory"/>">
    <div style="max-height: 400px; overflow: auto;">
        <table style="border-collapse:collapse;  border: 2px solid #D4D4D4; width:100%">
            <% for (final UserAuditRecord record : auditRecords) { %>
            <tr>
                <td class="key" style="width:50%">
                            <span class="timestamp">
                            <%= dateFormatter.format(record.getTimestamp()) %>
                            </span>
                </td>
                <td>
                    <%= record.getEventCode().getLocalizedString(ContextManager.getPwmApplication(session).getConfig(),userLocale) %>
                </td>
            </tr>
            <% } %>
        </table>
    </div>
</div>
<% } %>
<% } %>
</div>
<div id="buttonbar">
    <form action="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>" method="post" enctype="application/x-www-form-urlencoded">
        <input type="hidden" name="processAction" value="continue"/>
        <button type="submit" name="button" class="btn" id="button_continue">
            <pwm:if test="showIcons"><span class="btn-icon fa fa-forward"></span></pwm:if>
            <pwm:display key="Button_Continue"/>
        </button>
    </form>
</div>
</div>
<div class="push"></div>
</div>
<pwm:script>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/layout/TabContainer","dijit/layout/ContentPane"],function(dojoParser){
            dojoParser.parse();
        });
    });
</script>
</pwm:script>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>                   
