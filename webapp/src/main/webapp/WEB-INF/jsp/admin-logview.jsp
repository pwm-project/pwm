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

<%@ page import="password.pwm.i18n.Admin" %>
<%@ page import="password.pwm.util.logging.LocalDBLogger" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<html lang="<pwm:value name="<%=PwmValue.localeCode%>"/>" dir="<pwm:value name="<%=PwmValue.localeDir%>"/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <% final String PageName = JspUtility.localizedString(pageContext,"Title_LogViewer",Admin.class);%>
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="<%=PageName%>"/>
    </jsp:include>
    <div id="centerbody" class="wide">
        <h1 id="page-content-title"><pwm:display key="Title_LogViewer" bundle="Admin" displayIfMissing="true"/></h1>
        <%@ include file="fragment/admin-nav.jsp" %>
        <jsp:include page="/WEB-INF/jsp/fragment/log-viewer.jsp"/>

        <div id="wrapper-logInteraction">
            <form action="<pwm:context/>/private/admin" method="post" enctype="application/x-www-form-urlencoded"
                  name="form-downloadLog" id="CC" class="">
                <input type="hidden" name="<%=PwmConstants.PARAM_FORM_ID%>" value="<pwm:FormID/>"/>
                <input type="hidden" name="<%=PwmConstants.PARAM_ACTION_REQUEST%>" value="<%=AdminServlet.AdminAction.downloadLogData%>"/>
                <table style="max-width: 350px; margin-right: auto; margin-left: auto">
                    <tr class="noborder">
                        <td class="noborder">
                            <label for="downloadType">Type</label>
                        </td>
                        <td class="noborder">
                            <label for="compressionType">Compression</label>
                        </td>
                        <td class="noborder">
                        </td>
                    </tr>
                    <tr class="noborder">
                        <td>
                            <select id="downloadType" name="downloadType">
                                <option value="plain">Plain</option>
                                <option value="csv">CSV</option>
                            </select>
                        </td>
                        <td>
                            <select id="compressionType" name="compressionType">
                                <option value="none">None</option>
                                <option value="zip">ZIP</option>
                                <option value="gzip">GZip</option>
                            </select>
                        </td>
                        <td>
                            <button type="submit" name="button-download" id="button-download" class="btn">
                                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-download"></span></pwm:if>
                                Download
                            </button>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
        <div style="text-align: center;">
            <button type="button" class="btn" id="button-openLogViewerButton">
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-list-alt"></span></pwm:if>
                Pop-Out Log Viewer
                &nbsp;
                <pwm:if test="<%=PwmIfTest.showIcons%>"><span class="btn-icon pwm-icon pwm-icon-external-link"></span></pwm:if>
            </button>
        </div>
        <pwm:script>
            <script type="text/javascript">
                PWM_GLOBAL['startupFunctions'].push(function() {
                    PWM_MAIN.addEventHandler('button-openLogViewerButton', 'click', function () {
                        var windowUrl = PWM_GLOBAL['url-context'] + '/private/admin/administration?processAction=viewLogWindow';
                        var windowName = 'logViewer';
                        PWM_MAIN.newWindowOpen(windowUrl, windowName);
                    });
                });
            </script>
        </pwm:script>


        <div style="max-width: 100%; text-align: center; margin-left: auto; margin-right: auto">
            <div style="max-width: 600px;  text-align: center;  margin-left: auto; margin-right: auto">
                <div class="footnote">
                    <% LocalDBLogger localDBLogger = JspUtility.getPwmRequest(pageContext).getPwmApplication().getLocalDBLogger();%>
                    <p>
                        This page shows the debug log
                        history. This records shown here are stored in the LocalDB.  The LocalDB contains <%=JspUtility.friendlyWrite( pageContext, localDBLogger.getStoredEventCount() )%> events. The oldest event stored in the LocalDB is from
                        <span class="timestamp"><%= JspUtility.friendlyWrite( pageContext, ContextManager.getPwmApplication(session).getLocalDBLogger().getTailDate() ) %></span>.
                    </p>
                    <p>
                        The LocalDB is configured to capture events of level
                        <i><%=ContextManager.getPwmApplication(session).getConfig().readSettingAsString(PwmSetting.EVENTS_LOCALDB_LOG_LEVEL)%>
                        </i> and higher.
                    </p>
                </div>
            </div>
        </div>
    </div>


    <div class="push"></div>
</div>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>
