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

<%@ page import="com.novell.ldapchai.cr.ResponseSet" %>
<%@ page import="com.novell.ldapchai.exception.ChaiUnavailableException" %>
<%@ page import="org.apache.commons.lang.StringEscapeUtils" %>
<%@ page import="password.pwm.bean.SessionStateBean" %>
<%@ page import="password.pwm.bean.UserInfoBean" %>
<%@ page import="password.pwm.util.TimeDuration" %>
<%@ page import="password.pwm.util.operations.CrService" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Collections" %>
<%@ page import="password.pwm.event.AuditRecord" %>
<%@ page import="password.pwm.config.PwmSetting" %>
<%@ page import="password.pwm.bean.ResponseInfoBean" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmSession pwmSession = PwmSession.getPwmSession(request); %>
<% final UserInfoBean uiBean = pwmSession.getUserInfoBean(); %>
<% final SessionStateBean ssBean = pwmSession.getSessionStateBean(); %>
<% final DateFormat dateFormatter = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.FULL, SimpleDateFormat.FULL, pwmSession.getSessionStateBean().getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body onload="pwmPageLoadHandler();" class="nihilo">
<div id="wrapper" class="nihilo">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Title_UserInformation"/>
</jsp:include>
    <div id="centerbody">
        <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false">
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:Display key="Title_UserInformation"/>">
                <table>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_Username"/>
                        </td>
                        <td>
                            <%= StringEscapeUtils.escapeHtml(uiBean.getUserID()) %>
                        </td>
                    </tr>
                    <%--
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_UserDN"/>
                        </td>
                        <td>
                            <%= StringEscapeUtils.escapeHtml(uiBean.getUserDN()) %>
                        </td>
                    </tr>
                    --%>
                </table>
                <br class="clear"/>
                <table>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordExpired"/>
                        </td>
                        <td>
                            <%if (uiBean.getPasswordState().isExpired()) {%><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordPreExpired"/>
                        </td>
                        <td>
                            <%if (uiBean.getPasswordState().isPreExpired()) {%><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordWithinWarningPeriod"/>
                        </td>
                        <td>
                            <%if (uiBean.getPasswordState().isWarnPeriod()) { %><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordViolatesPolicy"/>
                        </td>
                        <td>
                            <% if (uiBean.getPasswordState().isViolatesPolicy()) {%><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordSetTime"/>
                        </td>
                        <td>
                            <%= uiBean.getPasswordLastModifiedTime() != null ? dateFormatter.format(uiBean.getPasswordLastModifiedTime()) : "n/a"%>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordSetTimeDelta"/>
                        </td>
                        <td>
                            <%= uiBean.getPasswordLastModifiedTime() != null ? TimeDuration.fromCurrent(uiBean.getPasswordLastModifiedTime()).asLongString(ssBean.getLocale()) : "n/a"%>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_PasswordExpirationTime"/>
                        </td>
                        <td>
                            <%= uiBean.getPasswordExpirationTime() != null ? dateFormatter.format(uiBean.getPasswordExpirationTime()) : "n/a"%>
                        </td>
                    </tr>
                </table>
                <br/>
                <table>
                    <% ResponseInfoBean responseInfoBean = pwmSession.getUserInfoBean().getResponseInfoBean(); %>
                    <tr>
                        <td class="key">
                            <pwm:Display key="Field_ResponsesStored"/>
                        </td>
                        <td>
                            <%if (!uiBean.isRequiresResponseConfig()) { %><pwm:Display key="Value_True"/><% } else { %><pwm:Display key="Value_False"/><% } %>
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
                <br/>
                <table>
                    <tr>
                        <td class="key">
                            Network Address
                        </td>
                        <td>
                            <%= ssBean.getSrcAddress() %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            Network Host
                        </td>
                        <td>
                            <%= ssBean.getSrcHostname() %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            Logout URL
                        </td>
                        <td>
                            <%= StringEscapeUtils.escapeHtml(Helper.figureLogoutURL(pwmApplicationHeader, pwmSessionHeader)) %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            Forward URL
                        </td>
                        <td>
                            <%= StringEscapeUtils.escapeHtml(Helper.figureForwardURL(pwmApplicationHeader, pwmSessionHeader, request)) %>
                        </td>
                    </tr>
                </table>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:Display key="Title_PasswordPolicy"/>">
                <div style="max-height: 400px; overflow: auto;">
                    <table>
                        <tr>
                            <td class="key">
                                <pwm:Display key="Title_PasswordPolicy"/>
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
            <% if (ContextManager.getPwmApplication(session).getConfig() != null && ContextManager.getPwmApplication(session).getConfig().readSettingAsBoolean(PwmSetting.DISPLAY_PASSWORD_HISTORY)) { %>
            <%
                List<AuditRecord> auditRecords = Collections.emptyList();
                try { auditRecords = pwmApplicationHeader.getAuditManager().readUserAuditRecords(pwmSessionHeader);} catch (Exception e) {/*noop*/}
                final Locale userLocale = PwmSession.getPwmSession(session).getSessionStateBean().getLocale();
            %>
            <% if (auditRecords != null && !auditRecords.isEmpty()) { %>
            <div data-dojo-type="dijit.layout.ContentPane" title="<pwm:Display key="Title_UserEventHistory"/>">
                <div style="max-height: 400px; overflow: auto;">
                    <table style="border-collapse:collapse;  border: 2px solid #D4D4D4; width:100%">
                        <% for (final AuditRecord record : auditRecords) { %>
                        <tr>
                            <td class="key" style="width:50%">
                                <%= dateFormatter.format(record.getTimestamp()) %>
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
            <form action="<%=request.getContextPath()%>/public/<pwm:url url='CommandServlet'/>" method="post"
                  enctype="application/x-www-form-urlencoded">
                <input type="hidden"
                       name="processAction"
                       value="continue"/>
                <input type="submit" name="button" class="btn"
                       value="    <pwm:Display key="Button_Continue"/>    "
                       id="button_continue"/>
            </form>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    PWM_GLOBAL['startupFunctions'].push(function(){
        require(["dojo/parser","dijit/layout/TabContainer","dijit/layout/ContentPane"],function(dojoParser){
            dojoParser.parse();
        });
    });
</script>
<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>
</body>
</html>                   
