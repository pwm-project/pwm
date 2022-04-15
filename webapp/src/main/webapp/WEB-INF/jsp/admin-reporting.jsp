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



<%@ page import="password.pwm.i18n.Admin" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>

<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <% final String PageName = JspUtility.localizedString(pageContext,"Title_DirectoryReport",Admin.class);%>
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=PageName%>"/>
    </jsp:include>
    <div id="centerbody">
        <h1 id="page-content-title"><pwm:display key="Title_DirectoryReport" bundle="Admin"/></h1>
        <%@ include file="fragment/admin-modular-nav.jsp" %>
        <table id="statusTable">
            <tr><td><pwm:display key="Display_PleaseWait"/></td></tr>
        </table>
        <br/>
        <form name="downloadReportZipForm" id="downloadReportZipForm" method="POST">
            <fieldset id="downloadReportOptionsFieldset" disabled class="noborder">
                <table class="noborder">
                    <tr>
                        <td>
                            Record Format
                        </td>
                        <td>
                            <select id="recordType" name="recordType">
                                <option value="csv">CSV</option>
                                <option value="json">JSON</option>
                            </select>
                        </td>
                    </tr>
                    <tr>
                        <td>
                            Maximum Record Count
                        </td>
                        <td>
                            <input type="number" name="recordCount" id="recordCount" value="1000"/>
                        </td>
                    </tr>
                </table>
            </fieldset>
            <table class="noborder">
                <tr><td colspan="2">
                    <div class="buttonbar">
                        <button id="reportDownloadButton" class="btn" type="button" disabled>
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download">&nbsp;</span></pwm:if>
                            <pwm:display key="Button_Download_Report" bundle="Admin"/>
                        </button>
                        <button id="reportCancelButton" class="btn" type="button" disabled>
                            <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-times"></span></pwm:if>
                            <pwm:display key="Button_Cancel" bundle="Display"/>
                        </button>
                    </div>
                </td></tr>
            </table>
            <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
            <input type="hidden" name="processAction" value="downloadReportZip"/>
        </form>
    </div>
</div>
<div class="push"></div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_ADMIN.initDownloadProcessReportZipForm();
        PWM_GLOBAL['startupFunctions'].push(function(){
            setInterval(function () { PWM_ADMIN.refreshReportProcessStatus() }, 5 * 1000);
            PWM_ADMIN.refreshReportProcessStatus();
        });
    </script>
</pwm:script>
<% JspUtility.setFlag(pageContext, PwmRequestFlag.HIDE_LOCALE); %>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
