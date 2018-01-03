<%--
  ~ Password Management Servlets (PWM)
  ~ http://www.pwm-project.org
  ~
  ~ Copyright (c) 2006-2009 Novell, Inc.
  ~ Copyright (c) 2009-2017 The PWM Project
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

<%@ page import="password.pwm.http.servlet.helpdesk.HelpdeskDetailInfoBean" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="java.time.Instant" %>
<%@ page import="password.pwm.http.bean.DisplayElement" %>
<%@ page import="password.pwm.http.servlet.accountinfo.AccountInformationBean" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmRequest pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<% final HelpdeskDetailInfoBean helpdeskDetailInfoBean = (HelpdeskDetailInfoBean)pwmRequest.getAttribute(PwmRequestAttribute.HelpdeskDetail); %>

<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody" style="min-width: 800px">
        <div id="page-content-title"><pwm:display key="Title_Helpdesk" displayIfMissing="true"/></div>
        <% if (!StringUtil.isEmpty(helpdeskDetailInfoBean.getUserDisplayName())) { %>
        <h2 style="text-align: center"><%=StringUtil.escapeHtml(helpdeskDetailInfoBean.getUserDisplayName())%></h2>
        <% } %>
        <pwm:script>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function(){
                    PWM_VAR["helpdesk_obfuscatedDN"] = '<%=JspUtility.getAttribute(pageContext, PwmRequestAttribute.HelpdeskObfuscatedDN)%>';
                    PWM_VAR["helpdesk_username"] = '<%=helpdeskDetailInfoBean.getUserDisplayName()%>';
                });
            </script>
        </pwm:script>
        <%@ include file="/WEB-INF/jsp/fragment/message.jsp" %>
        <table class="noborder">
            <tr>
                <td class="noborder" style="width: 600px; max-width:600px; vertical-align: top">
                    <div id="panel-helpdesk-detail" data-dojo-type="dijit.layout.TabContainer" style="max-width: 600px; height: 100%;" data-dojo-props="doLayout: false, persist: true" >
                        <div id="Field_Profile" data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Field_Profile"/>" class="tabContent">
                            <div style="max-height: 400px; overflow: auto;">
                                <table class="nomargin">
                                    <% for (final DisplayElement displayElement : helpdeskDetailInfoBean.getProfileData()) { %>
                                    <% request.setAttribute("displayElement", displayElement); %>
                                    <jsp:include page="fragment/displayelement-row.jsp"/>
                                    <% } %>
                                </table>
                            </div>
                        </div>
                        <div id="Title_Status" data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_Status"/>" class="tabContent">
                            <table class="nomargin">
                                <% for (final DisplayElement displayElement : helpdeskDetailInfoBean.getStatusData()) { %>
                                <% request.setAttribute("displayElement", displayElement); %>
                                <jsp:include page="fragment/displayelement-row.jsp"/>
                                <% } %>
                            </table>
                        </div>
                        <% if (helpdeskDetailInfoBean.getUserHistory() != null && !helpdeskDetailInfoBean.getUserHistory().isEmpty()) { %>
                        <div id="Title_UserEventHistory" data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_UserEventHistory"/>" class="tabContent">
                            <div style="max-height: 400px; overflow: auto;">
                                <table class="nomargin">
                                    <% for (final AccountInformationBean.ActivityRecord record : helpdeskDetailInfoBean.getUserHistory()) { %>
                                    <tr>
                                        <td class="key timestamp" style="width:50%">
                                            <%= JavaHelper.toIsoDate(record.getTimestamp()) %>
                                        </td>
                                        <td>
                                            <%= record.getLabel() %>
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
                                            <%= StringUtil.escapeHtml(helpdeskDetailInfoBean.getPasswordPolicyDN()) %>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="key">
                                            <pwm:display key="Field_Profile"/>
                                        </td>
                                        <td>
                                            <%= StringUtil.escapeHtml(helpdeskDetailInfoBean.getPasswordPolicyID()) %>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="key">
                                            <pwm:display key="Field_Display"/>
                                        </td>
                                        <td>
                                            <ul>
                                                <% for (final String requirementLine : helpdeskDetailInfoBean.getPasswordRequirements()) { %>
                                                <li><%=requirementLine%>
                                                </li>
                                                <% } %>
                                            </ul>
                                        </td>
                                    </tr>
                                </table>
                                <table class="nomargin">
                                    <% for (final String key : helpdeskDetailInfoBean.getPasswordPolicyRules().keySet()) { %>
                                    <tr>
                                        <td class="key">
                                            <%= StringUtil.escapeHtml(key) %>
                                        </td>
                                        <td>
                                            <%= StringUtil.escapeHtml(helpdeskDetailInfoBean.getPasswordPolicyRules().get(key)) %>
                                        </td>
                                    </tr>
                                    <% } %>
                                </table>
                            </div>
                        </div>
                        <% if (!JavaHelper.isEmpty(helpdeskDetailInfoBean.getHelpdeskResponses())) { %>
                        <div id="Title_SecurityResponses" data-dojo-type="dijit.layout.ContentPane" title="<pwm:display key="Title_SecurityResponses"/>" class="tabContent">
                            <table class="nomargin">
                                <% for (final DisplayElement displayElement : helpdeskDetailInfoBean.getHelpdeskResponses()) { %>
                                <% request.setAttribute("displayElement", displayElement); %>
                                <jsp:include page="fragment/displayelement-row.jsp"/>
                                <% } %>
                            </table>
                        </div>
                        <% } %>
                    </div>
                    <br/>
                    <div class="footnote"><span class="timestamp"><%=JavaHelper.toIsoDate(Instant.now())%></span></div>
                </td>
                <td class="noborder" style="width: 200px; max-width:200px; text-align: left; vertical-align: top">
                    <div class="noborder" style="margin-top: 25px; margin-left: 5px">
                        <% if (helpdeskDetailInfoBean.getVisibleButtons().contains(HelpdeskDetailInfoBean.StandardButton.back)) { %>
                        <button name="they" class="helpdesk-detail-btn btn" id="button_continue" autofocus>
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                            <pwm:display key="Button_GoBack"/>
                        </button>
                        <% } %>

                        <% if (helpdeskDetailInfoBean.getVisibleButtons().contains(HelpdeskDetailInfoBean.StandardButton.refresh)) { %>
                        <button name="button_refresh" class="helpdesk-detail-btn btn" id="button_refresh">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh"></span></pwm:if>
                            <pwm:display key="Display_CaptchaRefresh"/>
                        </button>
                        <% } %>

                        <br/><br/>

                        <% if (helpdeskDetailInfoBean.getVisibleButtons().contains(HelpdeskDetailInfoBean.StandardButton.changePassword)) { %>
                        <button class="helpdesk-detail-btn btn" id="helpdesk_ChangePasswordButton">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-key"></span></pwm:if>
                            <pwm:display key="Button_ChangePassword"/>
                        </button>
                        <% } %>

                        <% if (helpdeskDetailInfoBean.getVisibleButtons().contains(HelpdeskDetailInfoBean.StandardButton.unlock)) { %>
                        <% if (helpdeskDetailInfoBean.getEnabledButtons().contains(HelpdeskDetailInfoBean.StandardButton.unlock)) { %>
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

                        <% if (helpdeskDetailInfoBean.getVisibleButtons().contains(HelpdeskDetailInfoBean.StandardButton.clearResponses)) { %>
                        <% if (helpdeskDetailInfoBean.getEnabledButtons().contains(HelpdeskDetailInfoBean.StandardButton.clearResponses)) { %>
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
                                });</script>
                        </pwm:script>
                        <% } %>
                        <% } %>

                        <% if (helpdeskDetailInfoBean.getVisibleButtons().contains(HelpdeskDetailInfoBean.StandardButton.clearOtpSecret)) { %>
                        <% if (helpdeskDetailInfoBean.getEnabledButtons().contains(HelpdeskDetailInfoBean.StandardButton.clearOtpSecret)) { %>
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

                        <% if (helpdeskDetailInfoBean.getVisibleButtons().contains(HelpdeskDetailInfoBean.StandardButton.verification)) { %>
                        <button id="sendTokenButton" class="helpdesk-detail-btn btn">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-mobile-phone"></span></pwm:if>
                            <pwm:display key="Button_Verify"/>
                        </button>
                        <% } %>

                        <% if (helpdeskDetailInfoBean.getVisibleButtons().contains(HelpdeskDetailInfoBean.StandardButton.deleteUser)) { %>
                        <button class="helpdesk-detail-btn btn" id="helpdesk_deleteUserButton">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-user-times"></span></pwm:if>
                            <pwm:display key="Button_Delete"/>
                        </button>
                        <% } %>

                        <button id="loadDetail" style="display:none">Load Detail</button>
                        <pwm:script>
                            <script type="text/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    PWM_MAIN.addEventHandler('loadDetail','click',function(){
                                        var url = 'helpdesk';
                                        url = PWM_MAIN.addParamToUrl(url, 'processAction', 'detail');
                                        url = PWM_MAIN.addParamToUrl(url, 'userKey', PWM_VAR['helpdesk_obfuscatedDN']);
                                        //url = PWM_MAIN.addParamToUrl(url, 'verificationState', PWM_MAIN.Preferences.readSessionStorage(PREF_KEY_VERIFICATION_STATE));
                                        PWM_MAIN.ajaxRequest(url,function () {
                                        });
                                    });
                                });
                            </script>
                        </pwm:script>

                        <% if (!JavaHelper.isEmpty(helpdeskDetailInfoBean.getCustomButtons())) { %>
                        <% for (final HelpdeskDetailInfoBean.ButtonInfo customButton : helpdeskDetailInfoBean.getCustomButtons()) { %>
                        <button class="helpdesk-detail-btn btn" name="action-<%=customButton.getName()%>" id="action-<%=customButton.getName()%>">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-location-arrow"></span></pwm:if>
                            <%=StringUtil.escapeHtml(customButton.getLabel())%>
                        </button>
                        <pwm:script>
                            <script type="text/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    PWM_MAIN.addEventHandler('action-<%=customButton.getName()%>','click',function(){
                                        PWM_HELPDESK.executeAction('<%=StringUtil.escapeJS(customButton.getName())%>');
                                    });
                                    PWM_MAIN.showTooltip({
                                        id: "action-<%=customButton.getName()%>",
                                        position: 'above',
                                        text: '<%=StringUtil.escapeJS(customButton.getDescription())%>'
                                    });
                                });
                            </script>
                        </pwm:script>
                        <% } %>
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
