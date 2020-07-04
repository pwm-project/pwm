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


<%@ page import="password.pwm.config.option.DataStorageMethod" %>
<%@ page import="password.pwm.config.profile.LdapProfile" %>
<%@ page import="password.pwm.health.HealthRecord" %>
<%@ page import="password.pwm.i18n.Admin" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.java.JavaHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>
<%@ page import="password.pwm.util.localdb.LocalDB" %>
<%@ page import="java.time.Instant" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="password.pwm.svc.stats.EpsStatistic" %>
<%@ page import="password.pwm.http.servlet.admin.AppDashboardData" %>
<%@ page import="password.pwm.http.PwmRequestAttribute" %>
<%@ page import="password.pwm.http.bean.DisplayElement" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final AppDashboardData appDashboardData = (AppDashboardData)JspUtility.getAttribute(pageContext, PwmRequestAttribute.AppDashboardData); %>
<% final PwmRequest dashboard_pwmRequest = JspUtility.getPwmRequest(pageContext); %>
<% final PwmApplication dashboard_pwmApplication = dashboard_pwmRequest.getPwmApplication(); %>
<% final Locale locale = JspUtility.locale(request); %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<% final String PageName = JspUtility.localizedString(pageContext,"Title_Dashboard",Admin.class);%>
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=PageName%>"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_Dashboard" bundle="Admin"/></h1>
        <%@ include file="fragment/admin-nav.jsp" %>
        <div id="DashboardTabContainer" class="tab-container" style="width: 100%; height: 100%;">

            <input name="tabs" type="radio" id="tab-1" checked="checked" class="input"/>
            <label for="tab-1" class="label">Status</label>
            <div id="StatusTab" class="tab-content-pane" title="Status" >
                <table class="nomargin">
                    <tr>
                        <td class="key">
                            <pwm:display key="Title_Sessions" bundle="Admin"/>
                        </td>
                        <td id="SessionCount">
                            <%= appDashboardData.getSessionCount() %>
                        </td>
                        <td class="key">
                            <pwm:display key="Title_LDAPConnections" bundle="Admin"/>
                        </td>
                        <td id="LDAPConnectionCount">
                            <%= appDashboardData.getLdapConnectionCount() %>
                        </td>
                    </tr>
                    <tr>
                        <td class="key">
                            <pwm:display key="Title_RequestsInProgress" bundle="Admin"/>
                        </td>
                        <td id="RequestsInProgress">
                            <%= appDashboardData.getRequestsInProgress() %>
                        </td>
                        <td>
                        </td>
                        <td>
                        </td>
                    </tr>
                </table>
                <table class="nomargin">
                    <tr>
                        <td>
                        </td>
                        <td style="text-align: center; font-weight: bold;">
                            <pwm:display key="Title_LastMinute" bundle="Admin"/>
                        </td>
                        <td style="text-align: center; font-weight: bold;">
                            <pwm:display key="Title_LastHour" bundle="Admin"/>
                        </td>
                        <td style="text-align: center; font-weight: bold;">
                            <pwm:display key="Title_LastDay" bundle="Admin"/>
                        </td>
                    </tr>
                    <% for (final EpsStatistic loopEpsType : EpsStatistic.values()) { %>
                    <% if ((loopEpsType != EpsStatistic.DB_READS && loopEpsType != EpsStatistic.DB_WRITES) || dashboard_pwmApplication.getConfig().hasDbConfigured()) { %>
                    <tr>
                        <td class="key">
                            <%= loopEpsType.getLabel(locale) %> / Minute
                        </td>
                        <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_MINUTE">
                            <span style="font-size: smaller; font-style: italic"><pwm:display key="Display_PleaseWait"/></span>
                        </td>
                        <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_HOUR">
                            <span style="font-size: smaller; font-style: italic"><pwm:display key="Display_PleaseWait"/></span>
                        </td>
                        <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_DAY">
                            <span style="font-size: smaller; font-style: italic"><pwm:display key="Display_PleaseWait"/></span>
                        </td>
                    </tr>
                    <% } %>
                    <% } %>
                </table>
                <div class="tab-container" style="margin-top: 15px;">
                    <input name="status_tabs" type="radio" id="tab-1.1" checked="checked" class="input"/>
                    <label for="tab-1.1" class="label">Last Minute</label>
                    <div class="tab-content-pane" title="Last Minute" class="tabContent">
                        <table class="nomargin noborder" style="min-height: 164px;">
                            <tr>
                                <td colspan="10" class="noborder nomargin nopadding">
                                    <div style="max-width: 600px; text-align: center">
                                        <div id="EPS-GAUGE-AUTHENTICATION_MINUTE" style="float: left; width: 33%">Authentications</div>
                                        <div id="EPS-GAUGE-PASSWORD_CHANGES_MINUTE" style="float: left; width: 33%">Password Changes</div>
                                        <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_MINUTE" style="float: left; width: 33%">Intruder Attempts</div>
                                    </div>
                                </td>
                            </tr>
                        </table>
                    </div>

                    <input name="status_tabs" type="radio" id="tab-1.2" class="input"/>
                    <label for="tab-1.2" class="label">Last Hour</label>
                    <div class="tab-content-pane" title="Last Hour" class="tabContent">
                        <table class="nomargin noborder" style="min-height: 164px;">
                            <tr>
                                <td colspan="10" class="noborder nomargin nopadding">
                                    <div style="max-width: 600px; text-align: center">
                                        <div id="EPS-GAUGE-AUTHENTICATION_HOUR" style="float: left; width: 33%">Authentications</div>
                                        <div id="EPS-GAUGE-PASSWORD_CHANGES_HOUR" style="float: left; width: 33%">Password Changes</div>
                                        <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_HOUR" style="float: left; width: 33%">Intruder Attempts</div>
                                    </div>
                                </td>
                            </tr>
                        </table>
                    </div>

                    <input name="status_tabs" type="radio" id="tab-1.3" class="input"/>
                    <label for="tab-1.3" class="label">Last Day</label>
                    <div class="tab-content-pane" title="Last Day" class="tabContent">
                        <table class="nomargin noborder" style="min-height: 164px;">
                            <tr>
                                <td colspan="10" class="noborder nomargin nopadding">
                                    <div style="max-width: 600px; text-align: center">
                                        <div id="EPS-GAUGE-AUTHENTICATION_DAY" style="float: left; width: 33%">Authentications</div>
                                        <div id="EPS-GAUGE-PASSWORD_CHANGES_DAY" style="float: left; width: 33%">Password Changes</div>
                                        <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_DAY" style="float: left; width: 33%">Intruder Attempts</div>
                                    </div>
                                </td>
                            </tr>
                        </table>
                    </div>
                    <div class="tab-end"></div>
                    <div class="noticebar">Events rates are per minute.  <pwm:display key="Notice_DynamicRefresh" bundle="Admin"/></div>
                </div>
            </div>

            <input name="tabs" type="radio" id="tab-2" class="input"/>
            <label for="tab-2" class="label">Health</label>
            <div id="HealthTab" class="tab-content-pane" title="Health">
                <div id="healthBody">
                    <div class="WaitDialogBlank"></div>
                </div>
                <br/>
                <div class="noticebar">
                    <pwm:display key="Notice_DynamicRefresh" bundle="Admin"/>  A public health page at
                    <a href="<pwm:context/>/public/health.jsp"><pwm:context/>/public/health.jsp</a>
                </div>
            </div>

            <input name="tabs" type="radio" id="tab-3" class="input"/>
            <label for="tab-3" class="label"><pwm:display key="Title_About" bundle="Admin"/></label>
            <div id="AboutTab" class="tab-content-pane" title="<pwm:display key="Title_About" bundle="Admin"/>">
                <div style="max-height: 400px; overflow: auto;">
                    <table class="nomargin">
                        <% for (final DisplayElement displayElement : appDashboardData.getAbout()) { %>
                        <% request.setAttribute("displayElement", displayElement); %>
                        <jsp:include page="fragment/displayelement-row.jsp"/>
                        <% } %>
                        <tr>
                            <td class="key">
                                Last LDAP Unavailable Time
                            </td>
                            <% final Collection<LdapProfile> ldapProfiles = dashboard_pwmApplication.getConfig().getLdapProfiles().values(); %>
                            <td>
                                <% if (ldapProfiles.size() < 2) { %>
                                <% final Instant lastError = dashboard_pwmApplication.getLdapConnectionService().getLastLdapFailureTime(ldapProfiles.iterator().next()); %>
                                <span class="timestamp">
                                <%= lastError == null ? JspUtility.getMessage(pageContext, Display.Value_NotApplicable) :JavaHelper.toIsoDate(lastError) %>
                                </span>
                                <% } else { %>
                                <table class="nomargin">
                                    <% for (final LdapProfile ldapProfile : ldapProfiles) { %>
                                    <tr>
                                        <td><%=ldapProfile.getDisplayName(locale)%></td>
                                        <td class="timestamp">
                                            <% final Instant lastError = dashboard_pwmApplication.getLdapConnectionService().getLastLdapFailureTime(ldapProfile); %>
                                            <%= lastError == null ? JspUtility.getMessage(pageContext, Display.Value_NotApplicable) :JavaHelper.toIsoDate(lastError) %>
                                        </td>
                                    </tr>
                                    <% } %>
                                </table>
                                <% } %>
                            </td>
                        </tr>
                        <tr>
                            <td class="key">
                                Dojo API Version
                            </td>
                            <td>
                                <span id="dojoVersionSpan"></span>
                                <pwm:script>
                                    <script type="text/javascript">
                                        PWM_GLOBAL['startupFunctions'].push(function(){
                                            require(["dojo"],function(dojo){
                                                dojo.byId('dojoVersionSpan').innerHTML = dojo.version;
                                            });
                                        });
                                    </script>
                                </pwm:script>
                            </td>
                        </tr>
                        <tr>
                            <td class="key">
                                License Information
                            </td>
                            <td>
                                <a href="<pwm:context/><pwm:url url="/public/reference/license.jsp"/>">License Information</a>
                            </td>
                        </tr>
                    </table>
                </div>
            </div>

            <input name="tabs" type="radio" id="tab-4" class="input"/>
            <label for="tab-4" class="label">Services</label>
            <div id="ServicesTab" class="tab-content-pane" title="Services">
                <div style="max-height: 600px; overflow: auto;">
                    <table class="nomargin">
                        <tr>
                            <th style="font-weight:bold;">
                                Service
                            </td>
                            <td style="font-weight:bold;">
                                Status
                            </td>
                            <td style="font-weight:bold;">
                                Storage
                            </td>
                            <td style="font-weight:bold;">
                                Health
                            </td>
                        </tr>
                        <% for (final AppDashboardData.ServiceData loopService : appDashboardData.getServices()) { %>
                        <tr id="serviceName-<%=loopService.getName()%>">
                            <td>
                                <%= loopService.getName() %>
                                <% if (!JavaHelper.isEmpty(loopService.getDebugData())) { %>
                                &nbsp;
                                <div class="btn-icon pwm-icon pwm-icon-list-alt"></div>
                                <% } %>
                            </td>
                            <td>
                                <%= loopService.getStatus() %>
                            </td>
                            <td>
                                <% for (final DataStorageMethod loopMethod : loopService.getStorageMethod()) { %>
                                <%=loopMethod == null ? "" : loopMethod.toString()%>
                                <br/>
                                <% } %>
                            </td>
                            <td>
                                <% if (!JavaHelper.isEmpty(loopService.getHealth())) { %>
                                <% for (final HealthRecord loopRecord : loopService.getHealth()) { %>
                                <%= loopRecord.getTopic(locale, dashboard_pwmApplication.getConfig()) %> - <%= loopRecord.getStatus().toString() %> - <%= loopRecord.getDetail(locale,
                                    dashboard_pwmApplication.getConfig()) %>
                                <br/>
                                <% } %>
                                <% } else { %>
                                No Issues
                                <% } %>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                </div>
            </div>

            <input name="tabs" type="radio" id="tab-5" class="input"/>
            <label for="tab-5" class="label">LocalDB</label>
            <div id="LocalDBTab" class="tab-content-pane" title="LocalDB">
                <div style="max-height: 400px; overflow: auto;">
                    <table class="nomargin">
                        <% for (final DisplayElement displayElement : appDashboardData.getLocalDbInfo()) { %>
                        <% request.setAttribute("displayElement", displayElement); %>
                        <jsp:include page="fragment/displayelement-row.jsp"/>
                        <% } %>
                    </table>
                </div>
                <br/>
                <div style="max-height: 400px; overflow: auto;">
                    <% if (!JavaHelper.isEmpty(appDashboardData.getLocalDbSizes())) { %>
                    <table class="nomargin">
                        <tr>
                            <td class="key">
                                Name
                            </td>
                            <td class="key" style="text-align: left">
                                Record Count
                            </td>
                        </tr>
                        <% for (final Map.Entry<LocalDB.DB,String> entry : appDashboardData.getLocalDbSizes().entrySet()) { %>
                        <tr>
                            <td style="text-align: right">
                                <%= entry.getKey() %>
                            </td>
                            <td>
                                <%= entry.getValue() %>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                    <% } else { %>
                    <div class="noborder" style="text-align:center; width:100%;">
                        <a style="cursor: pointer" id="button-showLocalDBCounts">Show LocalDB record counts</a> (may be slow to load)
                    </div>
                    <% } %>
                </div>
            </div>

            <input name="tabs" type="radio" id="tab-6" class="input"/>
            <label for="tab-6" class="label">Java</label>
            <div id="JavaTab" class="tab-content-pane" title="Java">
                <table class="nomargin">
                    <% for (final DisplayElement displayElement : appDashboardData.getJavaAbout()) { %>
                    <% request.setAttribute("displayElement", displayElement); %>
                    <jsp:include page="fragment/displayelement-row.jsp"/>
                    <% } %>
                </table>
                <br/>
                <% if (!JavaHelper.isEmpty(appDashboardData.getThreads())) { %>
                <div style="max-height: 400px; overflow: auto;">
                    <table class="nomargin">
                        <tr>
                            <td style="font-weight:bold;">
                                Id
                            </td>
                            <td style="font-weight:bold;">
                                Name
                            </td>
                            <td style="font-weight:bold;">
                                State
                            </td>
                        </tr>
                        <% for (final AppDashboardData.ThreadData threadData : appDashboardData.getThreads()) { %>
                        <tr id="<%=threadData.getId()%>">
                            <td>
                                <%= threadData.getId() %>
                            </td>
                            <td>
                                <%= threadData.getName() %>
                            </td>
                            <td>
                                <%= threadData.getState() %>
                            </td>
                        </tr>
                        <pwm:script>
                            <script type="application/javascript">
                                PWM_GLOBAL['startupFunctions'].push(function(){
                                    PWM_MAIN.addEventHandler('<%=threadData.getId()%>','click',function(){
                                        PWM_MAIN.showDialog({class:'wide',title:'Thread <%=threadData.getId()%>',text:'<pre>' +'<%=StringUtil.escapeJS(threadData.getTrace())%>' + '</pre>'})
                                    });
                                });
                            </script>
                        </pwm:script>
                        <% } %>
                    </table>
                </div>
                <% } else { %>
                <div class="noborder" style="text-align:center; width:100%;">
                    <a style="cursor: pointer" id="button-showThreadDetails">Show thread details</a> (may be slow to load)
                </div>
                <% } %>
            </div>

            <input name="tabs" type="radio" id="tab-7" class="input"/>
            <label for="tab-7" class="label">Nodes</label>
            <div id="Status" class="tab-content-pane" title="Nodes">
                <% if (!JavaHelper.isEmpty(appDashboardData.getNodeData())) { %>
                <div style="max-height: 400px; overflow: auto;">
                    <table class="nomargin">
                        <tr>
                            <td style="font-weight:bold;">
                                Instance ID
                            </td>
                            <td style="font-weight:bold;">
                                Uptime
                            </td>
                            <td style="font-weight:bold;">
                                Last Seen
                            </td>
                            <td style="font-weight:bold;">
                                Master
                            </td>
                            <td style="font-weight:bold;">
                                Config Match
                            </td>
                        </tr>
                        <% for (final AppDashboardData.NodeData nodeData : appDashboardData.getNodeData()) { %>
                        <tr>
                            <td>
                                <%= nodeData.getInstanceID()  %>
                            </td>
                            <td>
                                <%= nodeData.getUptime() %>
                            </td>
                            <td>
                                <span class="timestamp">
                                    <%= nodeData.getLastSeen() %>
                                </span>
                            </td>
                            <td>
                                <%= nodeData.getState() %>
                            </td>
                            <td>
                                <%= JspUtility.friendlyWrite(pageContext, nodeData.isConfigMatch())%>
                            </td>
                        </tr>
                        <% } %>
                    </table>
                    <br/>
                    <div class="footnote">
                        <%=appDashboardData.getNodeSummary()%>
                    </div>
                    <% if ( appDashboardData.getNodeStorageMethod() != null ) { %>
                    <p><div class="footnote">
                    Node communication method: <%= appDashboardData.getNodeStorageMethod() %>
                    </div></p>
                    <% } %>
                </div>
                <% } else { %>
                <div class="footnote">Node data is not yet available.</div>
                <% } %>
            </div>

            <pwm:if test="<%=PwmIfTest.booleanSetting%>" setting="<%=PwmSetting.PW_EXPY_NOTIFY_ENABLE%>">
            <input name="tabs" type="radio" id="tab-8" class="input"/>
            <label for="tab-8" class="label">Password Notification</label>
            <div id="Status" class="tab-content-pane" title="Password Notification">
                <table id="table-pwNotifyStatus">
                </table>
                <div class="footnote">
                    <pwm:display key="Notice_DynamicRefresh" bundle="Admin"/>
                </div>

                <br/>
                <table><tr><td class="title">Local Debug Log</td></tr>
                    <tr>
                        <td>
                            <div style="max-height: 500px; max-width: 580px; overflow: auto; white-space: pre" id="div-pwNotifyDebugLog"></div>
                        </td>
                    </tr>
                </table>
                <div class="buttonbar" style="width:100%">
                    <button type="submit" class="btn" id="button-refreshPwNotifyStatus">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh"></span></pwm:if>
                        Refresh Log
                    </button>
                    <button id="button-executePwNotifyJob" type="button" class="btn">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-play"></span></pwm:if>
                        Start Job
                    </button>
                </div>
            </div>
            </pwm:if>

            <div class="tab-end"></div>
        </div>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            PWM_ADMIN.showStatChart('PASSWORD_CHANGES',14,'statsChart',{refreshTime:11*1000});
            PWM_ADMIN.showAppHealth('healthBody', {showRefresh:true,showTimestamp:true});

            PWM_MAIN.addEventHandler('button-showLocalDBCounts','click',function(){
                PWM_MAIN.showWaitDialog({loadFunction:function(){
                        PWM_MAIN.gotoUrl('dashboard?showLocalDBCounts=true');
                    }})
            });
            PWM_MAIN.addEventHandler('button-showThreadDetails','click',function(){
                PWM_MAIN.showWaitDialog({loadFunction:function(){
                        PWM_MAIN.gotoUrl('dashboard?showThreadDetails=true');
                    }})
            });
            <% for (final AppDashboardData.ServiceData loopService : appDashboardData.getServices()) { %>
            <% if (!JavaHelper.isEmpty(loopService.getDebugData())) { %>
            PWM_MAIN.addEventHandler('serviceName-<%=loopService.getName()%>','click',function(){
                var tableText = '<table>';
                <% for (final Map.Entry<String,String> entry : loopService.getDebugData().entrySet()) { %>
                tableText += '<tr><td><%=StringUtil.escapeJS(entry.getKey())%></td>'
                    + '<td><%=StringUtil.escapeJS(entry.getValue())%></td></tr>';
                <% } %>
                tableText += '</table>';
                PWM_MAIN.showDialog({title:'Debug Properties',text:tableText});
            });
            <% } %>
            <% } %>

            <pwm:if test="<%=PwmIfTest.booleanSetting%>" setting="<%=PwmSetting.PW_EXPY_NOTIFY_ENABLE%>">
            PWM_ADMIN.initPwNotifyPage();
            </pwm:if>
        });
    </script>
</pwm:script>

<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
<pwm:script-ref url="/public/resources/js/admin.js"/>
</body>
</html>
