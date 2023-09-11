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


<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.http.servlet.PwmServletDefinition" %>
<%@ page import="password.pwm.http.servlet.command.CommandServlet" %>
<%@ page import="password.pwm.http.bean.DisplayElement" %>
<%@ page import="password.pwm.util.java.CollectionUtil" %>
<%@ page import="password.pwm.http.servlet.accountinfo.AccountInformationBean" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.http.servlet.helpdesk.HelpdeskUserDetail" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.http.servlet.helpdesk.HelpdeskServlet" %>
<%@ page import="password.pwm.http.servlet.helpdesk.HelpdeskDetailButton" %>
<%@ page import="password.pwm.http.servlet.helpdesk.HelpdeskClientData" %>
<%@ page import="java.util.Map" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final String userKey = (String)JspUtility.getAttribute(pageContext, PwmRequestAttribute.HelpdeskUserKey); %>
<% final HelpdeskClientData clientData = ( HelpdeskClientData ) JspUtility.getAttribute(pageContext, PwmRequestAttribute.HelpdeskClientData);%>
<% final HelpdeskUserDetail detailBean = ( HelpdeskUserDetail )JspUtility.getAttribute(pageContext, PwmRequestAttribute.HelpdeskDetailInfo);%>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body>
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="Title_Helpdesk"/>
    </jsp:include>
    <div id="centerbody" class="tall">
        <div id="page-content-title"><pwm:display key="Title_Helpdesk" displayIfMissing="true"/></div>
        <div class="detail-content-wrapper" >
            <div id="detail-card-view"></div>
            <div id="detail-actions">
                <div class="buttonbar">
                    <button type="submit" name="button-back" class="btn" id="button-back">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-backward"></span></pwm:if>
                        <pwm:display key="Button_GoBack"/>
                    </button>
                    <button type="submit" name="button-refresh" class="btn" id="button-refresh">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh">&nbsp;</span></pwm:if>
                        <pwm:display key="Button_Refresh" bundle="Admin"/>
                    </button>
                    </form>
                    <% if (detailBean.visibleButtons().contains( HelpdeskDetailButton.changePassword)) {%>
                    <% final boolean changePasswordEnabled = detailBean.enabledButtons().contains( HelpdeskDetailButton.changePassword);%>
                    <button type="button" name="button-changePassword" class="btn" id="button-changePassword" <%if (!changePasswordEnabled){%> disabled<%}%>>
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-key"></span></pwm:if>
                        <pwm:display key="Button_ChangePassword"/>
                    </button>
                    <% } %>
                    <% if (detailBean.visibleButtons().contains( HelpdeskDetailButton.unlock)) {%>
                    <% final boolean unlockEnabled = detailBean.enabledButtons().contains( HelpdeskDetailButton.unlock);%>
                    <button type="button" name="button-unlock" class="btn" id="button-unlock" <%if (!unlockEnabled){%> disabled<%}%>>
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-lock"></span></pwm:if>
                        <pwm:display key="Button_Unlock"/>
                    </button>
                    <% } %>
                    <% if (detailBean.visibleButtons().contains( HelpdeskDetailButton.clearResponses)) {%>
                    <% final boolean clearResponsesEnabled = detailBean.enabledButtons().contains( HelpdeskDetailButton.clearResponses);%>
                    <button type="button" name="button-clearResponses" class="btn" id="button-clearResponses" <%if (!clearResponsesEnabled){%> disabled<%}%>>
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-trash-o"></span></pwm:if>
                        <pwm:display key="Button_ClearResponses"/>
                    </button>
                    <% } %>
                    <% if (detailBean.visibleButtons().contains( HelpdeskDetailButton.clearOtpSecret)) {%>
                    <% final boolean clearOtpEnabled = detailBean.enabledButtons().contains( HelpdeskDetailButton.clearOtpSecret);%>
                    <button type="button" name="button-clearOtp" class="btn" id="button-clearOtp" <%if (!clearOtpEnabled){%> disabled<%}%>>
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-trash-o"></span></pwm:if>
                        <pwm:display key="Button_HelpdeskClearOtpSecret"/>
                    </button>
                    <% } %>
                    <% if (detailBean.visibleButtons().contains( HelpdeskDetailButton.verification)) {%>
                    <% final boolean verificationEnabled = detailBean.enabledButtons().contains( HelpdeskDetailButton.verification);%>
                    <button type="button" name="button-verification" class="btn" id="button-verification" <%if (!verificationEnabled){%> disabled<%}%>>
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-check"></span></pwm:if>
                        <pwm:display key="Button_Verifications"/>
                    </button>
                    <% } %>
                    <% if (detailBean.visibleButtons().contains( HelpdeskDetailButton.deleteUser)) {%>
                    <% final boolean deleteEnabled = detailBean.enabledButtons().contains( HelpdeskDetailButton.deleteUser);%>
                    <button type="button" name="button-deleteUser" class="btn" id="button-deleteUser" <%if (!deleteEnabled){%> disabled<%}%>>
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-trash"></span></pwm:if>
                        <pwm:display key="Button_Delete"/>
                    </button>
                    <% } %>
                    <% for ( final Map.Entry<String, HelpdeskClientData.ActionInformation> entry : clientData.actions().entrySet()) {%>
                    <button type="button" name="button-action-<%=entry.getKey()%>" class="btn" id="button-action-<%=entry.getKey()%>">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-caret-square-o-right"></span></pwm:if>
                        <%=entry.getValue().name()%>
                    </button>
                    <% } %>
                </div>
            </div>
            <div id="detail-info-view">
                <div class="tab-container">
                    <% if (!CollectionUtil.isEmpty(detailBean.profileData())) { %>
                    <input name="tabs" type="radio" id="tab-profile" checked="checked" class="input" <pwm:autofocus/> <pwm:tabindex/> >
                    <label for="tab-profile" class="label"><pwm:display key="Field_Profile"/></label>
                    <div class="tab-content-pane" id="UserInformation" title="<pwm:display key="Field_Profile"/>">
                        <table class="nomargin">
                            <% for (final DisplayElement displayElement : detailBean.profileData()) { %>
                            <% request.setAttribute("displayElement", displayElement); %>
                            <jsp:include page="fragment/displayelement-row.jsp"/>
                            <% } %>
                        </table>
                    </div>
                    <% } %>
                    <% if (!CollectionUtil.isEmpty(detailBean.statusData())) { %>
                    <input name="tabs" type="radio" id="tab-status" checked="checked" class="input" <pwm:autofocus/> <pwm:tabindex/> >
                    <label for="tab-status" class="label"><pwm:display key="Title_Status"/></label>
                    <div class="tab-content-pane tabContent" id="UserInformation" title="<pwm:display key="Title_Status"/>">
                        <table class="nomargin">
                            <% for (final DisplayElement displayElement : detailBean.statusData()) { %>
                            <% request.setAttribute("displayElement", displayElement); %>
                            <jsp:include page="fragment/displayelement-row.jsp"/>
                            <% } %>
                        </table>
                    </div>
                    <% } %>
                    <% if (!CollectionUtil.isEmpty(detailBean.userHistory())) { %>
                    <input name="tabs" type="radio" id="tab-userHistory" checked="checked" class="input" <pwm:autofocus/> <pwm:tabindex/> >
                    <label for="tab-userHistory" class="label"><pwm:display key="Title_UserEventHistory"/></label>
                    <div class="tab-content-pane tabContent" id="UserInformation" title="<pwm:display key="Title_UserEventHistory"/>">
                        <table class="nomargin">
                            <% for (final DisplayElement displayElement : detailBean.userHistory() ) { %>
                            <% request.setAttribute("displayElement", displayElement); %>
                            <jsp:include page="fragment/displayelement-row.jsp"/>
                            <% } %>
                        </table>
                    </div>
                    <% } %>
                    <% if (!CollectionUtil.isEmpty(detailBean.helpdeskResponses())) { %>
                    <input name="tabs" type="radio" id="tab-securityResponses" class="input"/>
                    <label for="tab-securityResponses" class="label"><pwm:display key="Title_SecurityResponses"/></label>
                    <div class="tab-content-pane" id="PasswordPolicy" title="<pwm:display key="Title_SecurityResponses"/>">
                        <table class="nomargin">
                            <% for (final DisplayElement displayElement : detailBean.helpdeskResponses()) { %>
                            <% request.setAttribute("displayElement", displayElement); %>
                            <jsp:include page="fragment/displayelement-row.jsp"/>
                            <% } %>
                        </table>
                    </div>
                    <% } %>
                    <% if (!CollectionUtil.isEmpty(detailBean.passwordPolicyRules())) { %>
                    <input name="tabs" type="radio" id="tab-passwordPolicy" class="input"/>
                    <label for="tab-passwordPolicy" class="label"><pwm:display key="Title_PasswordPolicy"/></label>
                    <div class="tab-content-pane" id="PasswordPolicy" title="<pwm:display key="Title_PasswordPolicy"/>">
                        <table class="nomargin">
                            <% for (final DisplayElement displayElement : detailBean.passwordPolicyRules()) { %>
                            <% request.setAttribute("displayElement", displayElement); %>
                            <jsp:include page="fragment/displayelement-row.jsp"/>
                            <% } %>
                        </table>
                    </div>
                    <% } %>
                    <div class="tab-end"></div>
                </div>
            </div>
        </div>
    </div>
</div>
<div class="push"></div>
</div>
<template id="template-helpdeskDialog">
    <div id="helpdeskChangePwDialog-wrapper">
        <form id="helpdeskChangePwDialog-form">
            <%@ include file="fragment/message.jsp" %>
            <br/>
            <jsp:include page="fragment/form-field-newpassword.jsp" />
            <button type="submit" class="nodisplay">Submit</button>
        </form>
    </div>
</template>

<jsp:include page="/WEB-INF/jsp/fragment/footer.jsp"/>

<link href="<pwm:url url='/public/resources/helpdesk.css' addContext="true"/>" rel="stylesheet" type="text/css" media="screen"/>

<script type="module" nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>">
    import {PWM_HELPDESK} from "<pwm:url url="/public/resources/js/helpdesk.js" addContext="true"/>";
    PWM_HELPDESK.initHelpdeskDetailPage('<%=userKey%>');
</script>
</body>
</html>
