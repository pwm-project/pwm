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

<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.svc.cluster.ClusterService" %>
<%@ page import="password.pwm.svc.pwnotify.PwNotifyService" %>
<%@ page import="password.pwm.svc.pwnotify.StoredJobState" %>
<%@ page import="password.pwm.util.LocaleHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body class="nihilo">
<link href="<pwm:context/><pwm:url url='/public/resources/configmanagerStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%>"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></h1>
        <%@ include file="fragment/configmanager-nav.jsp" %>
        <pwm:if test="<%=PwmIfTest.booleanSetting%>" setting="<%=PwmSetting.PW_EXPY_NOTIFY_ENABLE%>" negate="true">
            Password Notification Feature is not enabled.  See ConfigEditor: <%=PwmSetting.PW_EXPY_NOTIFY_ENABLE.toMenuLocationDebug(null,null)%>
        </pwm:if>
        <pwm:if test="<%=PwmIfTest.booleanSetting%>" setting="<%=PwmSetting.PW_EXPY_NOTIFY_ENABLE%>">
            <% final ClusterService clusterService = JspUtility.getPwmRequest( pageContext ).getPwmApplication().getClusterService(); %>
            <% final PwNotifyService service = JspUtility.getPwmRequest(pageContext).getPwmApplication().getPwNotifyService();%>
            <% final StoredJobState storedJobState = service.getJobState(); %>
            <table>
                <tr><td colspan="2" class="title">Password Expiration Notification Status</td></tr>
                <tr><td>Currently Running (on this server) </td><td><%=JspUtility.freindlyWrite(pageContext, service.isRunning())%></td></tr>
                <tr><td>This Server is Cluster Master</td><td><%=JspUtility.freindlyWrite(pageContext, clusterService.isMaster())%></td></tr>
                <% if (storedJobState != null)  { %>
                <tr><td>Last Job Start Time </td><td><span class="timestamp"><%=JspUtility.freindlyWrite(pageContext, storedJobState.getLastStart())%></span></td></tr>
                <tr><td>Last Job Completion Time </td><td><span class="timestamp"><%=JspUtility.freindlyWrite(pageContext, storedJobState.getLastCompletion())%></span></td></tr>
                <tr><td>Last Job Server Instance</td><td><%=JspUtility.freindlyWrite(pageContext, storedJobState.getServerInstance())%></td></tr>
                <% if (storedJobState.getLastError() != null) { %>
                <tr><td>Last Job Error</td><td><%=JspUtility.freindlyWrite(pageContext, storedJobState.getLastError().toDebugStr())%></td></tr>
                <% } %>
                <% } %>
            </table>
            <br/><br/>
            <table><tr><td class="title">Debug Log</td></tr>
                <tr><td><div style="max-height: 500px; overflow: auto">
                    <% if (StringUtil.isEmpty( service.debugLog())) { %>
                    <span class="footnote">Job has not been run on this server since startup.</span>
                    <% } else { %>
                    <div style="white-space: nowrap;  "><%=StringUtil.escapeHtml(service.debugLog()).replace("\n","<br/>")%></div>
                    <% } %>
                </div></td> </tr>
            </table>

            <div class="buttonbar" style="width:100%">
                <form action="<pwm:current-url/>" method="get" id="form-refresh">
                    <button type="submit" name="change" class="btn" id="button-refresh">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-refresh"></span></pwm:if>
                        Refresh
                    </button>
                </form>
                <button id="button-runJob" type="button" class="btn">
                    <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-play"></span></pwm:if>
                    Start Job
                </button>
            </div>

        </pwm:if>
        <br/>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">

        PWM_GLOBAL['startupFunctions'].push(function () {
            PWM_MAIN.addEventHandler('button-runJob','click',function(){
                PWM_MAIN.showWaitDialog({loadFunction:function(){
                        var url = PWM_MAIN.addParamToUrl(window.location.pathname, 'processAction','startJob');
                        PWM_MAIN.ajaxRequest(url,function(data){
                            PWM_MAIN.showDialog({title:'Job Started',text:data['successMessage']})
                        });
                    }
                });
            });
        });

    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
