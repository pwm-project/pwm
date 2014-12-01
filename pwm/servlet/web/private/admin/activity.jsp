<%@ page import="password.pwm.error.PwmException" %>
<%@ page import="password.pwm.http.JspUtility" %>
<%@ page import="password.pwm.i18n.Admin" %>
<%@ page import="password.pwm.i18n.LocaleHelper" %>
<%@ page import="password.pwm.util.intruder.RecordType" %>
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

<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true" contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<%
    PwmRequest activity_pwmRequest = null;
    try {
        activity_pwmRequest = PwmRequest.forRequest(request, response);
    } catch (PwmException e) {
        JspUtility.logError(pageContext, "error during page setup: " + e.getMessage());
    }
%>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo">
<div id="wrapper">
    <jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
        <jsp:param name="pwm.PageName" value="User Activity"/>
    </jsp:include>
    <div id="centerbody" class="wide">
        <%@ include file="admin-nav.jsp" %>
        <div data-dojo-type="dijit/layout/TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
            <div data-dojo-type="dijit/layout/ContentPane" title="<pwm:display key="Title_Sessions" bundle="Admin"/>">
                <div id="activeSessionGrid">
                </div>
                <div style="text-align: center">
                    <input name="maxResults" id="maxActiveSessionResults" value="1000" data-dojo-type="dijit/form/NumberSpinner" style="width: 70px"
                           data-dojo-props="constraints:{min:10,max:10000000,pattern:'#'},smallDelta:100"/>
                    Rows
                    <button class="btn" type="button" id="button-activeSessionRefresh">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-refresh">&nbsp;</span></pwm:if>
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

                </div>
            </div>
            <% for (RecordType recordType : RecordType.values()) { %>
            <% String titleName = LocaleHelper.getLocalizedMessage(activity_pwmRequest.getLocale(),"IntruderRecordType_" + recordType.toString(), activity_pwmRequest.getConfig(), Admin.class); %>
            <div data-dojo-type="dijit/layout/ContentPane" title="Intruders<br/><%=titleName%>">
                <div id="<%=recordType%>_Grid">
                </div>
            </div>
            <% } %>
            <div data-dojo-type="dijit/layout/ContentPane" title="Audit Records<br/><pwm:display key="Title_AuditUsers" bundle="Admin"/>">
                <div id="auditUserGrid">
                </div>
                <div style="text-align: center">
                    <input name="maxAuditUserResults" id="maxAuditUserResults" value="1000" data-dojo-type="dijit/form/NumberSpinner" style="width: 70px"
                           data-dojo-props="constraints:{min:10,max:10000000,pattern:'#'},smallDelta:100"/>
                    Rows
                    <button class="btn" type="button" id="button-refreshAuditUser">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-refresh">&nbsp;</span></pwm:if>
                        <pwm:display key="Button_Refresh" bundle="Admin"/>
                    </button>
                    <form action="<pwm:context/><pwm:url url="/private/CommandServlet"/>" method="GET">
                        <button type="submit" class="btn">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-download">&nbsp;</span></pwm:if>
                            Download as CSV
                        </button>
                        <input type="hidden" name="processAction" value="outputAuditLogCsv"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </div>
            </div>
            <div data-dojo-type="dijit/layout/ContentPane" title="Audit Records<br/><pwm:display key="Title_AuditSystem" bundle="Admin"/>">
                <div id="auditSystemGrid">
                </div>
                <div style="text-align: center">
                    <input name="maxAuditSystemResults" id="maxAuditSystemResults" value="1000" data-dojo-type="dijit/form/NumberSpinner" style="width: 70px"
                           data-dojo-props="constraints:{min:10,max:10000000,pattern:'#'},smallDelta:100"/>
                    Rows
                    <button class="btn" type="button" id="button-refreshSystemAudit">
                        <pwm:if test="showIcons"><span class="btn-icon fa fa-refresh">&nbsp;</span></pwm:if>
                        <pwm:display key="Button_Refresh" bundle="Admin"/>
                    </button>
                    <form action="<pwm:context/><pwm:url url="/private/CommandServlet"/>" method="GET">
                        <button type="submit" class="btn">
                            <pwm:if test="showIcons"><span class="btn-icon fa fa-download">&nbsp;</span></pwm:if>
                            Download as CSV
                        </button>
                        <input type="hidden" name="processAction" value="outputAuditLogCsv"/>
                        <input type="hidden" name="pwmFormID" value="<pwm:FormID/>"/>
                    </form>
                </div>
            </div>
        </div>
        <br/>
        <%--
        <div style="text-align: center">
            <input name="maxResults" id="maxIntruderGridResults" value="1000" data-dojo-type="dijit/form/NumberSpinner" style="width: 70px"
                   data-dojo-props="constraints:{min:10,max:10000000,pattern:'#'},smallDelta:100"/>
            Rows
            <button class="btn" type="button" onclick="PWM_ADMIN.refreshIntruderGrid()">
                <pwm:if test="showIcons"><span class="btn-icon fa fa-refresh">&nbsp;</span></pwm:if>
                <pwm:display key="Button_Refresh" bundle="Admin"/>
            </button>
        </div>
        --%>
    </div>
    <div>
        <div class="push"></div>
    </div>
</div>
<pwm:script>
    <script type="text/javascript">
        PWM_GLOBAL['startupFunctions'].push(function(){
            require(["dojo/parser","dojo/ready","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog","dijit/form/NumberSpinner"],function(dojoParser,ready){
                dojoParser.parse(PWM_MAIN.getObject('centerbody'));
                setTimeout(function(){
                    ready(function(){
                        PWM_ADMIN.initIntrudersGrid();
                        PWM_ADMIN.initActiveSessionGrid();
                        PWM_ADMIN.initAuditGrid();
                    });

                    PWM_MAIN.addEventHandler('button-refreshAuditUser','click',function(){
                        PWM_ADMIN.refreshAuditGridData(PWM_MAIN.getObject('maxAuditUserResults').value);
                    });
                    PWM_MAIN.addEventHandler('button-refreshSystemAudit','click',function(){
                        PWM_ADMIN.refreshAuditGridData(PWM_MAIN.getObject('maxAuditSystemResults').value);
                    });
                },3000);
            });
        });
    </script>
</pwm:script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


