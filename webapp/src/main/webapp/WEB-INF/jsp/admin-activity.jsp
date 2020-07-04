<%--
 ~ Password Management Servlets (PWM)
 ~ http://www.pwm-project.org
 ~
 ~ Copyright (c) 2006-2009 Novell, Inc.
 ~ Copyright (c) 2009-2020 The PWM Project
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


<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.tag.value.PwmValue" %>
<%@ page import="password.pwm.i18n.Admin" %>
<%@ page import="password.pwm.svc.intruder.RecordType" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmRequest activity_pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<style nonce="<pwm:value name="<%=PwmValue.cspNonce%>"/>" type="text/css">
    .analysisGrid {
        min-height: 55vh;
    }

    .dgrid-row {
        max-height: 46px;
    }

    .dgrid-cell {
        text-overflow: ellipsis;
        white-space: nowrap;
    }
</style>
<div id="wrapper">
        <% final String PageName = JspUtility.localizedString(pageContext,"Title_UserActivity",Admin.class);%>
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp" flush="true" >
        <jsp:param name="pwm.PageName" value="<%=PageName%>"/>
    </jsp:include>
    <div id="centerbody" class="wide">
        <h1 id="page-content-title"><pwm:display key="Title_UserActivity" bundle="Admin"/></h1>
        <%@ include file="fragment/admin-nav.jsp" %>
        <div id="ActivityTabContainer" class="tab-container" style="width: 100%; height: 100%;">
            <input name="tabs" type="radio" id="tab-1" checked="checked" class="input"/>
            <label for="tab-1" class="label"><pwm:display key="Title_Sessions" bundle="Admin"/></label>
            <div id="SessionsTab" class="tab-content-pane" title="<pwm:display key="Title_Sessions" bundle="Admin"/>" >

                <div id="activeSessionGrid" class="analysisGrid">
                </div>
                <div style="text-align: center">
                    <input name="maxResults" id="maxActiveSessionResults" value="1000" type="number" min="10" max="10000000" style="width: 70px"/>
                    Rows
                    <button class="btn" type="button" id="button-activeSessionRefresh">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh">&nbsp;</span></pwm:if>
                        <pwm:display key="Button_Refresh" bundle="Admin"/>
                    </button>
                    <pwm:script>
                        <script type="text/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_MAIN.addEventHandler('button-activeSessionRefresh','click',function(){
                                    PWM_ADMIN.refreshActiveSessionGrid()
                                });
                            });
                        </script>
                    </pwm:script>
                    <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded" id="download-sessions" name="download-sessions">
                        <button type="submit" class="btn">
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download"></span></pwm:if>
                            <pwm:display key="Button_DownloadCSV" bundle="Admin"/>
                        </button>
                        <input type="hidden" name="processAction" value="<%=AdminServlet.AdminAction.downloadSessionsCsv%>"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </div>
            </div>
            <input name="tabs" type="radio" id="tab-2" class="input"/>
            <label for="tab-2" class="label"><pwm:display key="Title_Intruders" bundle="Admin"/></label>
            <div id="IntrudersTab" class="tab-content-pane" title="<pwm:display key="Title_Intruders" bundle="Admin"/>">
                <div class="tab-container" style="width: 100%; height: 100%;">
                    <% boolean checked = true; %>
                    <% for (final RecordType recordType : RecordType.values()) { %>
                    <% final String titleName = LocaleHelper.getLocalizedMessage(activity_pwmRequest.getLocale(),"IntruderRecordType_" + recordType.toString(), activity_pwmRequest.getConfig(), Admin.class); %>
                    <input name="intruder_tabs" type="radio" id="tab-2.<%=recordType%>" <%=checked?"checked=\"checked\"":""%> class="input"/>
                    <label for="tab-2.<%=recordType%>" class="label"><%=titleName%></label>
                    <div class="tab-content-pane" title="<%=titleName%>">

                            <div id="<%=recordType%>_Grid" class="analysisGrid">
                            </div>
                    </div>
                    <% checked = false; %>
                    <% } %>
                    <div class="tab-end"></div>
                </div>
            </div>
            <input name="tabs" type="radio" id="tab-3" class="input"/>
            <label for="tab-3" class="label" id="audit_tab_label"><pwm:display key="Title_Audit" bundle="Admin"/></label>
            <div id="AuditTab" class="tab-content-pane" title="<pwm:display key="Title_Audit" bundle="Admin"/>">
                <div class="tab-container" style="width: 100%; height: 100%;">

                    <input name="audit_tabs" type="radio" id="tab-3.1" checked="checked" class="input"/>
                    <label for="tab-3.1" class="label"><pwm:display key="Title_AuditUsers" bundle="Admin"/></label>
                    <div class="tab-content-pane" title="<pwm:display key="Title_AuditUsers" bundle="Admin"/>" class="tabContent">

                        <div id="auditUserGrid" class="analysisGrid">
                        </div>

                        <div style="text-align: center">
                            <input name="maxAuditUserResults" id="maxAuditUserResults" value="100" type="number" min="10" max="10000000" style="width: 70px"/>
                            Rows
                            <button class="btn" type="button" id="button-refreshAuditUser">
                                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh">&nbsp;</span></pwm:if>
                                <pwm:display key="Button_Refresh" bundle="Admin"/>
                            </button>
                            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded">
                                <button type="submit" class="btn">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download"></span></pwm:if>
                                    <pwm:display key="Button_DownloadCSV" bundle="Admin"/>
                                </button>
                                <input type="hidden" name="processAction" value="<%=AdminServlet.AdminAction.downloadAuditLogCsv%>"/>
                                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                            </form>
                        </div>
                    </div>
                    <input name="audit_tabs" type="radio" id="tab-3.2" class="input"/>
                    <label for="tab-3.2" class="label"><pwm:display key="Title_AuditHelpdesk" bundle="Admin"/></label>
                    <div class="tab-content-pane" title="<pwm:display key="Title_AuditHelpdesk" bundle="Admin"/>" class="tabContent">
                        <div id="auditHelpdeskGrid" class="analysisGrid">
                        </div>
                        <div style="text-align: center">
                            <input name="maxAuditHelpdeskResults" id="maxAuditHelpdeskResults" value="100" type="number" min="10" max="10000000" style="width: 70px"/>
                            Rows
                            <button class="btn" type="button" id="button-refreshHelpdeskUser">
                                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh">&nbsp;</span></pwm:if>
                                <pwm:display key="Button_Refresh" bundle="Admin"/>
                            </button>
                            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded">
                                <button type="submit" class="btn">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download"></span></pwm:if>
                                    <pwm:display key="Button_DownloadCSV" bundle="Admin"/>
                                </button>
                                <input type="hidden" name="processAction" value="downloadAuditLogCsv"/>
                                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                            </form>
                        </div>
                    </div>
                    <input name="audit_tabs" type="radio" id="tab-3.3" class="input"/>
                    <label for="tab-3.3" class="label"><pwm:display key="Title_AuditSystem" bundle="Admin"/></label>
                    <div class="tab-content-pane" title="<pwm:display key="Title_AuditSystem" bundle="Admin"/>" class="tabContent">
                        <div id="auditSystemGrid" class="analysisGrid">
                        </div>
                        <div style="text-align: center">
                            <input name="maxAuditSystemResults" id="maxAuditSystemResults" value="100" type="number" min="10" max="10000000" style="width: 70px"/>
                            Rows
                            <button class="btn" type="button" id="button-refreshSystemAudit">
                                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh">&nbsp;</span></pwm:if>
                                <pwm:display key="Button_Refresh" bundle="Admin"/>
                            </button>
                            <form action="<pwm:current-url/>" method="post" enctype="application/x-www-form-urlencoded">
                                <button type="submit" class="btn">
                                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download"></span></pwm:if>
                                    <pwm:display key="Button_DownloadCSV" bundle="Admin"/>
                                </button>
                                <input type="hidden" name="processAction" value="downloadAuditLogCsv"/>
                                <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                            </form>
                        </div>
                    </div>
                    <div class="tab-end"></div>
                </div>
            </div>
            <div class="tab-end"></div>
        </div>
        <div class="push"></div>
    </div>
    <pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_MAIN.addEventHandler('audit_tab_label','click',function(){
            });

            PWM_ADMIN.initAuditGrid();
            PWM_ADMIN.initActiveSessionGrid();
            PWM_ADMIN.initIntrudersGrid();

            PWM_MAIN.addEventHandler('button-refreshAuditUser','click',function(){
                PWM_ADMIN.refreshAuditGridData(PWM_MAIN.getObject('maxAuditUserResults').value,'USER');
            });
            PWM_MAIN.addEventHandler('button-refreshHelpdeskUser','click',function(){
                PWM_ADMIN.refreshAuditGridData(PWM_MAIN.getObject('maxAuditHelpdeskResults').value,'HELPDESK');
            });
            PWM_MAIN.addEventHandler('button-refreshSystemAudit','click',function(){
                PWM_ADMIN.refreshAuditGridData(PWM_MAIN.getObject('maxAuditSystemResults').value,'SYSTEM');
            });
        });
    </script>
    </pwm:script>
    <%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
