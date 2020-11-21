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
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.http.tag.conditional.PwmIfTest" %>
<%@ page import="password.pwm.i18n.Config" %>
<%@ page import="password.pwm.i18n.Display" %>
<%@ page import="password.pwm.util.java.FileSystemUtility" %>
<%@ page import="password.pwm.util.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.java.StringUtil" %>

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.INCLUDE_CONFIG_CSS);%>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    PwmRequest localdb_pwmRequest = null;
    PwmApplication localdb_pwmApplication = null;
    try {
        localdb_pwmRequest = PwmRequest.forRequest(request, response);
        localdb_pwmApplication = localdb_pwmRequest.getPwmApplication();
    } catch (PwmException e) {
        JspUtility.logError(pageContext, "error during page setup: " + e.getMessage());
    }
%>


<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="fragment/header.jsp" %>
<body>
<link href="<pwm:context/><pwm:url url='/public/resources/configmanagerStyle.css'/>" rel="stylesheet" type="text/css"/>
<div id="wrapper">
    <jsp:include page="fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%>"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><%=LocaleHelper.getLocalizedMessage(Config.Title_ConfigManager, JspUtility.getPwmRequest(pageContext))%></h1>
        <%@ include file="fragment/configmanager-nav.jsp" %>
        <table style="width:550px" id="table-localDBAbout">
            <tr>
                <td class="title">About the LocalDB</td>
            </tr>
            <tr>
                <td>
                    The LocalDB is a per-server data store.  Data stored in the LocalDB includes:
                    <ul>
                        <li>Debug Logs</li>
                        <li>Custom Wordlist & Seedlist</li>
                        <li>Audit Events</li>
                        <li>Statistics</li>
                        <li>Data Caches</li>
                        <li>Email and SMS Queues</li>
                    </ul>
                    The LocalDB can be downloaded or uploaded.  This is generally only useful when upgrading or migrating a server, and the data is being backed up or restored.
                </td>
            </tr>
        </table>
        <br/>
        <table style="width:550px">
            <tr>
                <td class="title" colspan="2">LocalDB Status</td>
            </tr>
            <tr>
                <td class="key">
                    Location on disk
                </td>
                <td>
                    <%= localdb_pwmApplication.getLocalDB() == null
                            ? JspUtility.getMessage(pageContext, Display.Value_NotApplicable)
                            : localdb_pwmApplication.getLocalDB().getFileLocation() == null
                            ? JspUtility.getMessage(pageContext, Display.Value_NotApplicable)
                            : localdb_pwmApplication.getLocalDB().getFileLocation().getAbsolutePath()
                    %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    LocalDB Size On Disk
                </td>
                <td>
                    <%= localdb_pwmApplication.getLocalDB() == null
                            ? JspUtility.getMessage(pageContext, Display.Value_NotApplicable)
                            : localdb_pwmApplication.getLocalDB().getFileLocation() == null
                            ? JspUtility.getMessage(pageContext, Display.Value_NotApplicable)
                            : StringUtil.formatDiskSize(FileSystemUtility.getFileDirectorySize(
                            localdb_pwmApplication.getLocalDB().getFileLocation()))
                    %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    LocalDB Free Space
                </td>
                <td>
                    <%= localdb_pwmApplication.getLocalDB() == null
                            ? JspUtility.getMessage(pageContext, Display.Value_NotApplicable)
                            : localdb_pwmApplication.getLocalDB().getFileLocation() == null
                            ? JspUtility.getMessage(pageContext, Display.Value_NotApplicable)
                            : StringUtil.formatDiskSize(FileSystemUtility.diskSpaceRemaining(localdb_pwmApplication.getLocalDB().getFileLocation())) %>
                </td>
            </tr>
        </table>


        <table style="width:550px" id="table-localDBInfo">
        </table>
        <br/>
        <table class="noborder">
            <tr class="buttonrow">
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_ExportLocalDB">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download"></span></pwm:if>
                        <pwm:display key="MenuItem_ExportLocalDB" bundle="Config"/>
                    </a>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                PWM_MAIN.addEventHandler('MenuItem_ExportLocalDB','click',function(){PWM_CONFIG.downloadLocalDB()});
                                makeTooltip('MenuItem_ExportLocalDB',PWM_CONFIG.showString('MenuDisplay_ExportLocalDB'));
                            });
                        </script>
                    </pwm:script>
                </td>
                <td class="buttoncell">
                    <a class="menubutton" id="MenuItem_UploadLocalDB">
                        <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-upload"></span></pwm:if>
                        Import (Upload) LocalDB Archive File
                    </a>
                    <pwm:script>
                        <script type="application/javascript">
                            PWM_GLOBAL['startupFunctions'].push(function(){
                                makeTooltip('MenuItem_UploadConfig',PWM_CONFIG.showString('MenuDisplay_UploadConfig'));
                                PWM_MAIN.addEventHandler('MenuItem_UploadLocalDB',"click",function(){
                                    <pwm:if test="<%=PwmIfTest.configurationOpen%>">
                                    PWM_CONFIG.uploadLocalDB();
                                    </pwm:if>
                                    <pwm:if test="<%=PwmIfTest.configurationOpen%>" negate="true">
                                    PWM_CONFIG.configClosedWarning();
                                    </pwm:if>
                                });
                            });
                        </script>
                    </pwm:script>
                </td>

            </tr>
        </table>
    </div>
    <div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">

        function makeTooltip(id,text) {
            PWM_MAIN.showTooltip({
                id: id,
                showDelay: 800,
                position: ['below','above'],
                text: text,
                width: 300
            });
        }

        PWM_GLOBAL['startupFunctions'].push(function () {
        });

    </script>
</pwm:script>
<pwm:script-ref url="/public/resources/js/configmanager.js"/>
<pwm:script-ref url="/public/resources/js/uilibrary.js"/>
<pwm:script-ref url="/public/resources/js/admin.js"/>
<div><%@ include file="fragment/footer.jsp" %></div>
</body>
</html>
