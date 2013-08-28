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

<%@ page import="password.pwm.util.stats.Statistic" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.math.RoundingMode" %>
<%@ page import="java.text.DateFormat" %>
<%@ page import="java.text.NumberFormat" %>
<!DOCTYPE html>
<%@ page language="java" session="true" isThreadSafe="true"
         contentType="text/html; charset=UTF-8" %>
<%@ taglib uri="pwm" prefix="pwm" %>
<% final PwmApplication pwmApplication = ContextManager.getPwmApplication(session); %>
<% final NumberFormat numberFormat = NumberFormat.getInstance(PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<% final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, PwmSession.getPwmSession(session).getSessionStateBean().getLocale()); %>
<html dir="<pwm:LocaleOrientation/>">
<%@ include file="/WEB-INF/jsp/fragment/header.jsp" %>
<body class="nihilo" onload="pwmPageLoadHandler();">
<div id="wrapper">
<jsp:include page="/WEB-INF/jsp/fragment/header-body.jsp">
    <jsp:param name="pwm.PageName" value="Activity"/>
</jsp:include>
    <div id="centerbody">
    <%@ include file="admin-nav.jsp" %>
        <table class="tablemain">
            <tr>
                <td class="key">
                    <a href="<pwm:url url='activesessions.jsp'/>">
                        Active HTTP Sessions
                    </a>
                </td>
                <td>
                    <a href="<pwm:url url='activesessions.jsp'/>">
                        <%= ContextManager.getContextManager(session).getPwmSessions().size() %>
                    </a>
                </td>
                <td class="key">
                    Active LDAP Connections
                </td>
                <td>
                    <%= Helper.figureLdapConnectionCount(pwmApplication,ContextManager.getContextManager(session)) %>
                </td>
            </tr>
            <tr>
                <td class="key">
                    <a href="<pwm:url url='intruders.jsp'/>">
                        Locked Users
                    </a>
                </td>
                <td>
                    <a href="<pwm:url url='intruders.jsp'/>">
                        <%= numberFormat.format(pwmApplication.getIntruderManager().userRecordCount()) %>
                    </a>
                </td>
                <td class="key">
                    <a href="<pwm:url url='intruders.jsp'/>">
                        Locked Addresses
                    </a>
                </td>
                <td>
                    <a href="<pwm:url url='intruders.jsp'/>">
                        <%= numberFormat.format(pwmApplication.getIntruderManager().addressRecordCount()) %>
                    </a>
                </td>
            </tr>
        </table>
        <table class="tablemain">
            <tr>
                <td>
                </td>
                <td style="text-align: center; font-weight: bold;">
                    Last Minute
                </td>
                <td style="text-align: center; font-weight: bold;">
                    Last Hour
                </td>
                <td style="text-align: center; font-weight: bold;">
                    Last Day
                </td>
            </tr>
            <% for (final Statistic.EpsType loopEpsType : Statistic.EpsType.values()) { %>
            <tr>
                <td class="key">
                    <%= loopEpsType.getDescription(pwmSessionHeader.getSessionStateBean().getLocale()) %> / Minute
                </td>
                <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_MINUTE">
                    <span style="font-size: smaller; font-style: italic">Loading...</span>
                </td>
                <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_HOUR">
                    <span style="font-size: smaller; font-style: italic">Loading...</span>
                </td>
                <td style="text-align: center" id="FIELD_<%=loopEpsType.toString()%>_DAY">
                    <span style="font-size: smaller; font-style: italic">Loading...</span>
                </td>
            </tr>
            <% } %>
        </table>
        <br/>
        <div data-dojo-type="dijit.layout.TabContainer" style="width: 100%; height: 100%;" data-dojo-props="doLayout: false, persist: true">
            <div data-dojo-type="dijit.layout.ContentPane" title="Last Minute">
                <table class="tablemain">
                    <tr>
                        <td colspan="10" style="margin:0; padding:0">
                            <div style="max-width: 600px; text-align: center">
                                <div id="EPS-GAUGE-AUTHENTICATION_MINUTE" style="float: left; width: 33%">Authentications</div>
                                <div id="EPS-GAUGE-PASSWORD_CHANGES_MINUTE" style="float: left; width: 33%">Password Changes</div>
                                <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_MINUTE" style="float: left; width: 33%">Intruder Attempts</div>
                            </div>
                        </td>
                    </tr>
                </table>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="Last Hour">
                <table class="tablemain">
                    <tr>
                        <td colspan="10" style="margin:0; padding:0">
                            <div style="max-width: 600px; text-align: center">
                                <div id="EPS-GAUGE-AUTHENTICATION_HOUR" style="float: left; width: 33%">Authentications</div>
                                <div id="EPS-GAUGE-PASSWORD_CHANGES_HOUR" style="float: left; width: 33%">Password Changes</div>
                                <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_HOUR" style="float: left; width: 33%">Intruder Attempts</div>
                            </div>
                        </td>
                    </tr>
                </table>
            </div>
            <div data-dojo-type="dijit.layout.ContentPane" title="Last Day">
                <table class="tablemain">
                    <tr>
                        <td colspan="10" style="margin:0; padding:0">
                            <div style="max-width: 600px; text-align: center">
                                <div id="EPS-GAUGE-AUTHENTICATION_DAY" style="float: left; width: 33%">Authentications</div>
                                <div id="EPS-GAUGE-PASSWORD_CHANGES_DAY" style="float: left; width: 33%">Password Changes</div>
                                <div id="EPS-GAUGE-INTRUDER_ATTEMPTS_DAY" style="float: left; width: 33%">Intruder Attempts</div>
                            </div>
                        </td>
                    </tr>
                </table>
            </div>
            <div style="width: 100%; font-size: smaller; font-style: italic; text-align: center">events per minute, this content is dynamically refreshed</div>
        </div>
    </div>
    <div class="push"></div>
</div>
<script type="text/javascript">
    function startupPage() {
        require(["dojo/parser","dojo/domReady!","dijit/layout/TabContainer","dijit/layout/ContentPane","dijit/Dialog"],function(dojoParser){
            dojoParser.parse();

            showStatChart('PASSWORD_CHANGES',14,'statsChart');
            setInterval(function(){
                showStatChart('PASSWORD_CHANGES',14,'statsChart');
            }, 11 * 1000);
        });
    }

    PWM_GLOBAL['startupFunctions'].push(function(){
    startupPage();
    });
</script>
<%@ include file="/WEB-INF/jsp/fragment/footer.jsp" %>
</body>
</html>


